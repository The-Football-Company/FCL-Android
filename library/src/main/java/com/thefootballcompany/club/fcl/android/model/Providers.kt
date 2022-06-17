package com.thefootballcompany.club.fcl.android.model

import java.net.URL

/**
 * Created by muriel on 10.05.2022..
 */
fun methodToServiceMethod(value: String): ServiceMethod {
    if (value.endsWith("GET")) {
        return ServiceMethod.HTTP_GET
    }

    if (value.endsWith("POST")) {
        return ServiceMethod.HTTP_POST
    }

    if (value.endsWith("IFRAME")) {
        return ServiceMethod.IFRAME
    }

    if (value.endsWith("RPC")) {
        return ServiceMethod.IFRAME_RPC
    }

    if (value.endsWith("DATA")) {
        return ServiceMethod.DATA
    }

    throw IllegalArgumentException("unsupported")

}

enum class Env {
    MAIN_NET,
    TEST_NET
}

enum class Wallet {
    BLOCTO,
    DAPPER
}

enum class ServiceMethod {
    HTTP_POST,
    HTTP_GET,
    IFRAME,
    IFRAME_RPC,
    DATA
}

enum class AccessProvider(
    val title: String,
    val url: URL,
    val port: Int
) {
    MAIN(
        "Main",
        URL("https://access.mainnet.nodes.onflow.org"),
        9000
    ),
    TEST_NET(
        "Testnet",
        URL("https://access.devnet.nodes.onflow.org"),
        9000
    )
}

enum class WalletProvider(
    val title: String,
    val method: ServiceMethod,
    val url: URL,
) {
    DAPPER(
        "Dapper",
        ServiceMethod.HTTP_POST,
        URL("https://dapper-http-post.vercel.app/api/testnet/authn"),
    ),
    BLOCTO(
        "Blocto",
        ServiceMethod.HTTP_POST,
        URL("https://flow-wallet.blocto.app/api/flow/authn"),
    ),

    BLOCTO_TEST_NET(
        "Blocto Test net",
        ServiceMethod.HTTP_POST,
        URL("https://flow-wallet-testnet.blocto.app/api/flow/authn"),
    ),
    DAPPER_TEST_NET(
        "Dapper",
        ServiceMethod.HTTP_POST,
        URL("https://dapper-http-post.vercel.app/api/testnet/authn"),
    )
}