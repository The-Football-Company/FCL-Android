package com.thefootballcompany.club.fcl.android.extension

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.nftco.flow.sdk.*
import com.thefootballcompany.club.fcl.android.model.*

/**
 * Created by muriel on 10.06.2022..
 */
fun Interaction.toPreSignable(): PreSignable {
    return PreSignable(
        cadence = message?.cadence,
        args = arguments?.filter { it.value.asArgument != null }?.map { it.value.asArgument!! },
        interaction = this,
    )
}

fun Interaction.insideSigners(): List<String> {
    val insideSigner = mutableListOf<String>()
    authorizations?.let {
        insideSigner.addAll(it)
    }
    proposer?.let {
        insideSigner.add(it)
    }

    payer?.let {
        insideSigner.remove(it)
    }

    return insideSigner.distinct()
}

fun Interaction.outSideSigners(): List<String> {
    payer?.let {
        return listOf(it)
    }

    return emptyList()
}

fun Interaction.proposerAccount(): SignableUser {
    val proposer =
        proposer ?: throw NullPointerException("proposer == null")

    return accounts?.get(proposer)
        ?: throw NullPointerException("proposerAccount == null")
}

fun Interaction.payerAccount(): SignableUser {
    val payer =
        payer ?: throw NullPointerException("payer == null")

    return accounts?.get(payer)
        ?: throw NullPointerException("payerAccount == null")
}

fun Interaction.proposalKey(): FlowTransactionProposalKey {
    val proposer =
        proposer ?: throw NullPointerException("proposer == null")

    val proposerAccount = accounts?.get(proposer)
        ?: throw NullPointerException("proposerAccount == null")

    return FlowTransactionProposalKey(
        FlowAddress(proposerAccount.addr),
        proposerAccount.keyId.toInt(),
        proposerAccount.sequenceNum?.toLong() ?: 0
    )
}

fun Interaction.authorizationsAddr(): List<String> {
    val accounts = accounts()
    return authorizations?.mapNotNull { accounts[it]?.addr }?.distinct() ?: emptyList()
}

fun Interaction.asArguments(): List<AsArgument> {
    return arguments?.mapNotNull { it.value.asArgument } ?: emptyList()
}

fun Interaction.accounts(): Map<String, SignableUser> {
    return this.accounts ?: throw NullPointerException("accounts == null")
}

fun Interaction.toFlowTransaction(): FlowTransaction {
    return flowTransaction {

        val payer = payerAccount()
        val propKey = proposalKey()
        val accounts = accounts()

        val insideSigners = insideSigners()
        val outsideSigners = outSideSigners()

        script(FlowScript(message?.cadence.orEmpty()))

        val authorizationsAddress = authorizationsAddr()

        val fields = this@toFlowTransaction.arguments?.mapNotNull { it.value.field }
            ?: emptyList()

        val argumentsAsBytesArrays = fields.map {
            val jsonObject = JsonObject()
            jsonObject.addProperty("type", it.type)
            jsonObject.addProperty("value", it.value.toString())
            jsonObject.toString().toByteArray()
        }

        arguments = argumentsAsBytesArrays.map { FlowArgument(it) }.toMutableList()
        referenceBlockId = FlowId(message?.refBlock.orEmpty())
        proposalKey = propKey
        payerAddress = FlowAddress(payer.addr)

        authorizers = authorizationsAddress.map { FlowAddress(it) }

        addPayloadSignatures {
            insideSigners.forEach { tempID ->
                accounts[tempID]?.let { signableUser ->
                    signature(
                        FlowAddress(signableUser.addr),
                        signableUser.keyId,
                        FlowSignature(signableUser.signature.orEmpty())
                    )
                }
            }
        }

        gasLimit(message?.computeLimit ?: 100)

        addEnvelopeSignatures {
            outsideSigners.forEach { tempID ->
                accounts[tempID]?.let { signableUser ->
                    signature(
                        FlowAddress(signableUser.addr),
                        signableUser.keyId,
                        FlowSignature(signableUser.signature.orEmpty())
                    )
                }
            }
        }
    }
}

fun AuthResponse.toLocal(gson: Gson): Service? {
    try {
        val json = gson.toJson(local)
        return if (local is Map<*, *>) {
            gson.fromJson(json, Service::class.java)
        } else {
            gson.fromJson<List<Service>>(json, object : TypeToken<List<Service>>() {}.type)
                .firstOrNull()
        }
    } catch (e: Exception) {
        //Ignore
    }

    return null
}

fun AuthData.serviceOf(type: String): Service? {
    return services?.firstOrNull { it.type == type }
}

fun FlowTransaction.signablePayload(): ByteArray {
    return DomainTag.TRANSACTION_DOMAIN_TAG + canonicalPayload
}

fun FlowTransaction.signableEnvelop(): ByteArray {
    return DomainTag.TRANSACTION_DOMAIN_TAG + canonicalAuthorizationEnvelope
}