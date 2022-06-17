package com.thefootballcompany.club.fcl.android.model

/**
 * Created by muriel on 21.04.2022..
 */
enum class ServicesType(val serviceName: String) {
    AUTH_N("authn"),
    AUTH_Z("authz"),
    PRE_AUTH_Z("pre-authz"),
    USER_SIGNATURE("user-signature"),
    BACK_CHANNEL("back-channel-rpc"),
    LOCAL_VIEW("local-view"),
    OPEN_ID("open-id")
}

enum class ResponseStatus(val value: String) {
    PENDING("PENDING"),
    APPROVED("APPROVED"),
    DECLINED("DECLINED")
}

data class FCLAuthResponse(
    val status: ResponseStatus,
    val reason: String?,
    val addr: String? = null
)

data class AuthResponse(
    val status: ResponseStatus,
    val data: AuthData?,
    val updates: Service?,
    val local: Any?,
    val reason: String?,
    val compositeSignature: AuthData?,
    val authorizationUpdates: Service?
) {
    fun isPending(): Boolean {
        return status == ResponseStatus.PENDING
    }

    fun isApproved(): Boolean {
        return status == ResponseStatus.APPROVED
    }

    fun isDeclined(): Boolean {
        return status == ResponseStatus.DECLINED
    }
}

data class AuthData(
    val addr: String,
    val services: Array<Service>?,
    val authorization: List<Service>?,
    val f_type: String?,
    val f_vsn: String?,
    val payer: List<Service>?,
    val proposer: Service?,
    val signature: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AuthData

        if (addr != other.addr) return false
        if (services != null) {
            if (other.services == null) return false
            if (!services.contentEquals(other.services)) return false
        } else if (other.services != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = addr.hashCode()
        result = 31 * result + (services?.contentHashCode() ?: 0)
        return result
    }
}

data class Service(
    val ftType: String?,
    val fVsn: String?,
    val uid: String?,
    val identity: Identity,
    val provider: Provider?,
    val type: String?,
    val method: String?,
    val endpoint: String,
    var params: Map<String, String>
)

data class Provider(
    val fType: String,
    val fVsn: String?,
    val address: String,
    val name: String
)

data class Identity(val address: String, val keyId: Long)
