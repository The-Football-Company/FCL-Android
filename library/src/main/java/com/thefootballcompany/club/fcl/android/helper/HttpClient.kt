package com.thefootballcompany.club.fcl.android.helper

import com.thefootballcompany.club.fcl.android.model.AuthResponse
import retrofit2.HttpException
import retrofit2.http.*

/**
 * Created by muriel on 21.04.2022..
 */
interface HttpClient {

    @Throws(HttpException::class)
    suspend fun executeGet(url: String): AuthResponse

    @Throws(HttpException::class)
    suspend fun executePost(
        url: String,
        body: Any? = null,
        headers: Map<String, String>? = null
    ): AuthResponse
}

class AppHttpClient(private val httpClient: HttpClient) :
    HttpClient {

    override suspend fun executeGet(url: String): AuthResponse {
        return httpClient.executeGet(url)
    }

    override suspend fun executePost(
        url: String,
        body: Any?,
        headers: Map<String, String>?
    ): AuthResponse {
        return if (body == null) httpClient.executePost(url, headers ?: emptyMap())
        else httpClient.executePost(url, body, headers ?: emptyMap())
    }

    interface HttpClient {

        @GET
        suspend fun executeGet(@Url url: String): AuthResponse

        @POST
        suspend fun executePost(
            @Url url: String,
            @HeaderMap headers: Map<String, String> = emptyMap()
        ): AuthResponse

        @POST
        suspend fun executePost(
            @Url url: String,
            @Body body: Any,
            @HeaderMap headers: Map<String, String> = emptyMap()
        ): AuthResponse

    }

}