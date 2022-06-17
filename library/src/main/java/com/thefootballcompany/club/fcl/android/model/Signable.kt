package com.thefootballcompany.club.fcl.android.model

import com.nftco.flow.sdk.cadence.Field

data class PreSignable(
    val data: Map<String, String>? = mapOf(),
    val args: List<AsArgument>? = emptyList(),
    val cadence: String? = null,
    val f_type: String = "PreSignable",
    val f_vsn: String = "1.0.1",
    val interaction: Interaction = Interaction(),
    val roles: Role = Role(),
)

data class Signable(
    val addr: String,
    val data: Map<String, String> = mapOf(),
    val args: List<AsArgument>? = emptyList(),
    val cadence: String? = null,
    val f_type: String = "Signable",
    val f_vsn: String = "1.0.1",
    val interaction: Interaction = Interaction(),
    val keyId: Int,
    val message: String? = null,
    val roles: Role = Role(),
    val voucher: Voucher? = null,
)

data class Client(
    val fclLibrary: String?,
    val fclVersion: String?,
    val hostname: Any?
)

data class SignableUser(
    val addr: String,
    val keyId: Long,
    val role: Role,
    val sequenceNum: Int? = null,
    val tempId: String,
    var signature: String? = null,
    @Transient var service: Service? = null
)

enum class Tag {
    TRANSACTION
}

data class Interaction(
    val account: Account? = Account(),
    val accounts: Map<String, SignableUser>? = mapOf(),
    val arguments: Map<String, Argument>? = mapOf(),
    val assigns: Map<String, String>? = mapOf(),
    val authorizations: List<String>? = listOf(),
    val block: Block? = Block(),
    val collection: Id = Id(),
    val events: Events? = Events(),
    val message: Message? = Message(),
    val params: Map<String, String>? = mapOf(),
    val payer: String? = null,
    val proposer: String? = null,
    val reason: String? = null,
    val status: String? = "OK",
    //now only supports transactions
    val tag: Tag = Tag.TRANSACTION,
    val transaction: Id? = Id(),
)

data class Argument(
    val kind: String,
    val tempId: String,
    val value: String,
    val xform: Xform,
    val asArgument: AsArgument? = null,
    @Transient val field: Field<*>? = null
)

data class AsArgument(
    val type: String?,
    val value: String?
)

data class Id(val id: String? = null)

data class Voucher(
    val cadence: String? = null,
    val arguments: List<AsArgument>? = emptyList(),
    val refBlock: String? = null,
    val computeLimit: Int? = null,
    val proposalKey: ProposalKey? = ProposalKey(),
    val payer: String? = null,
    val authorizers: List<String>? = emptyList(),
    val payloadSigs: List<Singature>? = emptyList(),
    val envelopeSigs: List<Singature>? = emptyList(),
)

data class Singature(
    val address: String?,
    val keyId: Int?,
    val sig: String?,
)

data class App(
    val title: String = "https://placekitten.com/g/200/200",
    val icon: String = "FCLDemo"
)

data class Transaction(
    val id: Any
)

data class Message(
    val arguments: List<String>? = emptyList(),
    val authorizations: List<String>? = emptyList(),
    val cadence: String? = null,
    val computeLimit: Int? = null,
    val params: List<String>? = emptyList(),
    val payer: String? = null,
    val proposer: String? = null,
    val refBlock: String? = null
)

data class ProposalKey(
    val address: String? = null,
    val keyId: Int? = null,
    val sequenceNum: Int? = null
)

data class Events(
    val blockIds: List<String>? = listOf(),
    val end: String? = null,
    val eventType: String? = null,
    val start: String? = null
)

data class Block(
    val height: Long? = null,
    val id: String? = null,
    val isSealed: Boolean? = null
)

data class Role(
    val proposer: Boolean = false,
    val authorizer: Boolean = false,
    val payer: Boolean = false,
    val param: Boolean? = null
)

data class Xform(
    val label: String
)

data class Account(
    val addr: String? = null
)

