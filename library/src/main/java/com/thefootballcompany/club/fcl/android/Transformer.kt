package com.thefootballcompany.club.fcl.android

import android.util.Log
import com.nftco.flow.sdk.FlowAccessApi
import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.bytesToHex
import com.thefootballcompany.club.fcl.android.extension.*
import com.thefootballcompany.club.fcl.android.helper.ServiceProcessor
import com.thefootballcompany.club.fcl.android.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Created by muriel on 9.06.2022..
 */
interface TransformerFactory {

    @Throws(IllegalArgumentException::class)
    fun create(request: Request): Transformer
}

class AppTransformerFactory(
    private val appInfo: AppInfo,
    private val flowAccessApi: FlowAccessApi,
    private val serviceProcessor: ServiceProcessor
) : TransformerFactory {

    override fun create(request: Request): Transformer {
        when (request) {
            is CadenceRequest -> {
                val transformers = mutableListOf<Transformer>()

                transformers.add(ScriptTransformer(appInfo))
                transformers.add(AccountTransformer(appInfo, request.currentUser, serviceProcessor))
                transformers.add(RefBlockTransformer(flowAccessApi))
                transformers.add(SequenceNumberTransformer(flowAccessApi))
                transformers.add(SignatureTransformer(appInfo, serviceProcessor))

                return CadenceTransformer(transformers)
            }

            else -> throw IllegalArgumentException("$request is not supported")
        }
    }
}

interface Transformer {

    @Throws
    suspend fun transform(interaction: Interaction): Interaction
}

abstract class BaseTransformer : Transformer {

    companion object {
        private val TAG = this.javaClass.simpleName
    }

    protected fun log(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}

class ScriptTransformer(private val appInfo: AppInfo) : BaseTransformer() {

    override suspend fun transform(interaction: Interaction): Interaction {
        log("transform: $interaction")

        if (interaction.tag == Tag.TRANSACTION) {
            interaction.message?.cadence?.let { script ->
                var newScript = script

                log("original script: $script")

                appInfo.variables.entries.forEach { entry ->
                    newScript = newScript.replace(entry.key, entry.value)
                }

                log("transformed script: $newScript")
                return interaction.copy(message = interaction.message.copy(cadence = newScript))
            }
        }

        return interaction
    }
}

class RefBlockTransformer(
    private val flowAccessApi: FlowAccessApi,
    private val ioCoroutineContext: CoroutineContext = Dispatchers.IO
) : BaseTransformer() {

    override suspend fun transform(interaction: Interaction): Interaction {
        log("transform: $interaction")

        val refBlock = withContext(ioCoroutineContext) {
            flowAccessApi.getLatestBlock(true)
        }

        log("refBlock: $refBlock")
        return interaction.copy(message = interaction.message?.copy(refBlock = refBlock.id.base16Value))
    }
}

class SequenceNumberTransformer(
    private val flowAccessApi: FlowAccessApi,
    private val ioCoroutineContext: CoroutineContext = Dispatchers.IO
) : BaseTransformer() {

    override suspend fun transform(interaction: Interaction): Interaction {
        log("transform: $interaction")

        val accounts = interaction.accounts?.toMutableMap()
            ?: throw NullPointerException("accounts == null")
        val proposer = interaction.proposer
            ?: throw NullPointerException("proposer == null")
        val proposerAccount = accounts[proposer]
            ?: throw NullPointerException("proposerAccount == null")

        log("proposer: $proposer proposerAccount: $proposerAccount")

        val flowAccount = withContext(ioCoroutineContext) {
            flowAccessApi.getAccountAtLatestBlock(
                FlowAddress(proposerAccount.addr)
            ) ?: throw NullPointerException("flowAccount == null")
        }

        log("flowAccount: $flowAccount")

        val updatedProperAccount =
            proposerAccount.copy(sequenceNum = flowAccount.keys[proposerAccount.keyId.toInt()].sequenceNumber)

        accounts[proposer] = updatedProperAccount
        return interaction.copy(accounts = accounts)

    }
}

class AccountTransformer(
    private val appInfo: AppInfo,
    private val currentUser: AuthData,
    private val serviceProcessor: ServiceProcessor,
) : BaseTransformer() {

    override suspend fun transform(interaction: Interaction): Interaction {
        log("transform: $interaction")

        val preAuthzService = currentUser.serviceOf(ServicesType.PRE_AUTH_Z.serviceName)
            ?: throw NullPointerException("preAuthzService == null")

        val preSignable = interaction.toPreSignable()
        val authResponse =
            serviceProcessor.executeService(
                preAuthzService,
                preSignable,
                appInfo.location,
                mapOf("referer" to appInfo.location)
            )

        return createNewInteractionWith(interaction, authResponse)
    }

    private fun createNewInteractionWith(
        interaction: Interaction,
        preAuthzResponse: AuthResponse
    ): Interaction {

        var localInteraction = interaction
        val accounts = mutableMapOf<String, SignableUser>()

        val data = preAuthzResponse.data

        data?.payer?.firstOrNull()?.let { payer ->
            val tempId = genTempIdForIdentity(payer.identity)
            localInteraction = localInteraction.copy(payer = tempId)
            accounts[tempId] = createNewSignableUserWith(payer, tempId, Role(payer = true))
        }

        data?.proposer?.let { proposer ->
            val tempId = genTempIdForIdentity(proposer.identity)
            localInteraction = localInteraction.copy(proposer = tempId)
            accounts[tempId] = createNewSignableUserWith(proposer, tempId, Role(proposer = true))
        }

        val authorizations = preAuthzResponse.data?.authorization?.map { authorizer ->
            val tempId = genTempIdForIdentity(authorizer.identity)
            if (accounts.containsKey(tempId)) {
                var acc = accounts[tempId]!!
                acc = acc.copy(role = acc.role.copy(authorizer = true))
                accounts[tempId] = acc
            } else {
                accounts[tempId] =
                    createNewSignableUserWith(authorizer, tempId, Role(authorizer = true))
            }
            tempId
        }

        return localInteraction.copy(accounts = accounts, authorizations = authorizations)
    }

    private fun genTempIdForIdentity(identity: Identity): String {
        return "${identity.address}-${identity.keyId}"
    }

    private fun createNewSignableUserWith(
        service: Service,
        tempId: String,
        role: Role
    ): SignableUser {
        return SignableUser(
            addr = service.identity.address,
            keyId = service.identity.keyId,
            role = role,
            tempId = tempId,
            service = service
        )
    }
}

class SignatureTransformer(
    private val appInfo: AppInfo,
    private val serviceProcessor: ServiceProcessor,
) : BaseTransformer() {

    private val headers: Map<String, String> = mapOf(
        "referer" to appInfo.location,
        "Accept" to "application/json",
    )

    override suspend fun transform(interaction: Interaction): Interaction {
        log("transform: $interaction")

        var localInteraction = interaction
        val flowTransaction = localInteraction.toFlowTransaction()
        log("initial flow transaction: $flowTransaction")

        val accounts = localInteraction.accounts().toMutableMap()
        val insiderSigners = localInteraction.insideSigners()

        log("insider signer: $insiderSigners")

        val signedInsideSignersAccounts = fetchSignatureFor(
            accounts,
            insiderSigners,
            localInteraction,
            (flowTransaction.signablePayload()).bytesToHex()
        )

        signedInsideSignersAccounts.forEach {
            accounts[it.tempId] = it
        }

        localInteraction = localInteraction.copy(accounts = accounts)

        val flowTransactionWithInsideSigners = localInteraction.toFlowTransaction()

        log("flow transaction with inside signers: $flowTransactionWithInsideSigners")

        val outsideSigners = localInteraction.outSideSigners()

        log("outsideSigners signer: $outsideSigners")

        val signedOutsideSignersAccounts = fetchSignatureFor(
            accounts,
            outsideSigners,
            localInteraction,
            (flowTransactionWithInsideSigners.signableEnvelop()).bytesToHex()
        )

        signedOutsideSignersAccounts.forEach {
            accounts[it.tempId] = it
        }

        return localInteraction.copy(accounts = accounts)
    }

    private fun createSignable(
        account: SignableUser,
        interaction: Interaction,
    ): Signable {
        val accounts = interaction.accounts()
        val proposer = interaction.proposerAccount()
        val payer = interaction.payerAccount()
        return Signable(
            addr = account.addr,
            interaction = interaction,
            roles = account.role,
            cadence = interaction.message?.cadence,
            args = interaction.asArguments(),
            voucher = Voucher(
                cadence = interaction.message?.cadence,
                arguments = interaction.asArguments(),
                computeLimit = interaction.message?.computeLimit ?: 100,
                authorizers = interaction.authorizationsAddr(),
                payloadSigs = interaction.insideSigners().map {
                    createSigFor(it, accounts)
                },
                envelopeSigs = interaction.outSideSigners().map {
                    createSigFor(it, accounts)
                },
                payer = payer.addr,
                refBlock = interaction.message?.refBlock,
                proposalKey = ProposalKey(
                    proposer.addr,
                    proposer.keyId.toInt(),
                    proposer.sequenceNum ?: 0
                ),
            ),
            keyId = account.keyId.toInt()
        )
    }

    private fun createSigFor(
        accountTempId: String,
        accounts: Map<String, SignableUser>
    ): Singature {
        val acc = accounts[accountTempId]
        return Singature(
            acc?.addr,
            acc?.keyId?.toInt(),
            acc?.signature
        )
    }

    private fun updateUserWithSignature(
        account: SignableUser,
        authResponse: AuthResponse
    ): SignableUser {
        val signature =
            authResponse.data?.signature
                ?: authResponse.compositeSignature?.signature
                ?: throw NullPointerException("Signature == null for account $account")

        return account.copy(signature = signature)
    }

    private suspend fun fetchSignatureFor(
        accounts: Map<String, SignableUser>,
        tempIds: List<String>,
        interaction: Interaction,
        message: String
    ): List<SignableUser> {
        val signedAccounts = mutableListOf<SignableUser>()
        tempIds.forEach { tempId ->
            val account = accounts[tempId]
                ?: throw NullPointerException("account not found for tempId:$tempId")
            log("creating signable for account: $account")

            val signable = createSignable(account, interaction).copy(
                message = message,
            )

            log("signable: $signable")

            val service = account.service
                ?: throw NullPointerException("service not found for account with tempId:$tempId")

            val authResponse = serviceProcessor.executeService(
                service,
                signable,
                appInfo.location,
                headers
            )

            log("Signature authResponse $authResponse for account:$account")

            if (!authResponse.isApproved()) {
                throw IllegalStateException("User probably did not approved the transaction")
            }

            signedAccounts.add(updateUserWithSignature(account, authResponse))
        }

        return signedAccounts
    }
}

class CadenceTransformer(private val transformers: MutableList<Transformer>) : BaseTransformer() {

    override suspend fun transform(interaction: Interaction): Interaction {
        transformers.removeFirstOrNull()?.let { transformer ->
            return transform(transformer.transform(interaction))
        } ?: kotlin.run { return interaction }
    }
}