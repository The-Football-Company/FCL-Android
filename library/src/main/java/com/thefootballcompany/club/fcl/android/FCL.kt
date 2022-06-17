package com.thefootballcompany.club.fcl.android

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.nftco.flow.sdk.*
import com.nftco.flow.sdk.cadence.Field
import com.nftco.flow.sdk.cadence.JsonCadenceBuilder
import com.thefootballcompany.club.fcl.android.extension.toFlowTransaction
import com.thefootballcompany.club.fcl.android.helper.AppHttpClient
import com.thefootballcompany.club.fcl.android.helper.AppServiceProcessor
import com.thefootballcompany.club.fcl.android.helper.ServiceProcessor
import com.thefootballcompany.club.fcl.android.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Created by muriel on 21.04.2022..
 */

sealed class Request
internal data class CadenceRequest(
    val currentUser: AuthData,
    val script: String,
    val computeLimit: Int,
    val arguments: List<Field<*>>
) : Request()

class RequestBuilder {

    private var script: String? = null
    private var computeLimit: Int = 1000
    private var isCadenceScript = true
    private val arguments = mutableListOf<Field<*>>()

    fun addArg(field: Field<*>) = apply {
        arguments.add(field)
    }

    fun addArg(block: JsonCadenceBuilder.() -> Field<*>) = apply {
        val builder = JsonCadenceBuilder()
        val argument = block(builder)
        addArg(argument)
    }

    fun script(script: String, cadence: Boolean = true) = apply {
        this.isCadenceScript = cadence
        this.script = script
    }

    fun computeLimit(computeLimit: Int) = apply {
        this.computeLimit = computeLimit
    }

    internal fun build(authData: AuthData): Request {
        //we only support cadance scrip for now
        checkNotNull(script) {
            "scrip can't be null"
        }

        require(isCadenceScript) {
            "only cadence script is supported"
        }

        return CadenceRequest(authData, script!!, computeLimit, arguments)
    }

}

interface FCL {

    fun isAuthenticate(): Boolean

    fun unAuthenticate()

    @Throws()
    suspend fun authenticate(): FCLAuthResponse

    @Throws()
    suspend fun send(block: RequestBuilder.() -> Unit): FlowId

    @Throws()
    suspend fun sendAndWaitForSeal(
        pauseMs: Long = 100L,
        timeoutMs: Long = 180_000L,
        block: RequestBuilder.() -> Unit,
    ): FlowTransactionResult

}

class AppFCL(
    fclBuilder: FCLBuilder
) : FCL {

    private var flowAccessApi: FlowAccessApi
    private var serviceProcessor: ServiceProcessor
    private var appInfo: AppInfo
    private var currentUser: AuthData? = null
    private var walletProvider: WalletProvider
    private var gson: Gson
    private var activity: Activity
    private var transformerFactory: TransformerFactory
    private var ioCoroutineContext: CoroutineContext

    init {
        activity = fclBuilder.activity
        flowAccessApi = fclBuilder.flowAccessApi!!
        serviceProcessor = fclBuilder.serviceProcessor!!
        appInfo = fclBuilder.appInfo!!
        gson = fclBuilder.gson!!
        walletProvider = fclBuilder.walletProvider
        transformerFactory = fclBuilder.transformerFactory!!
        ioCoroutineContext = fclBuilder.ioCoroutineContext
    }

    override fun isAuthenticate(): Boolean {
        //todo improve me!
        return currentUser != null
    }

    override fun unAuthenticate() {
        //todo improve me!
        currentUser = null
    }

    override suspend fun authenticate(): FCLAuthResponse {
        val authResponse =
            serviceProcessor.executeService(ServiceMethod.HTTP_POST, walletProvider.url.toString())

        if (authResponse.isApproved()) {
            currentUser = authResponse.data
        }
        return FCLAuthResponse(authResponse.status, authResponse.reason, authResponse.data?.addr)
    }

    override suspend fun send(block: RequestBuilder.() -> Unit): FlowId {
        //For now, only when a user a authenticated!
        checkNotNull(currentUser) {
            "current user == null, please authenticate first"
        }

        val builder = RequestBuilder()
        block(builder)

        val request = builder.build(currentUser!!)

        val transformer = transformerFactory.create(request)

        val flowTransaction =
            transformer.transform(createInteractionFor(request)).toFlowTransaction()

        return withContext(ioCoroutineContext) {
            flowAccessApi.sendTransaction(flowTransaction)
        }
    }

    override suspend fun sendAndWaitForSeal(
        pauseMs: Long,
        timeoutMs: Long,
        block: RequestBuilder.() -> Unit
    ): FlowTransactionResult {
        check(pauseMs < timeoutMs) {
            "timeout < pause"
        }

        val flowId = send(block)
        val startTime = System.nanoTime()
        while (coroutineContext.isActive) {
            val result = withContext(ioCoroutineContext) {
                flowAccessApi.getTransactionResultById(flowId)
                    ?: throw NullPointerException("FlowTransactionResult == null for id: $flowId")
            }

            if (result.status == FlowTransactionStatus.SEALED) {
                return result
            }

            delay(pauseMs)

            val elapseTime = (System.nanoTime() - startTime) / 1000_000

            if (elapseTime >= timeoutMs) {
                throw TimeoutException()
            }
        }

        throw IllegalArgumentException("Something went wrong, probably this was cancelled")
    }

    private fun generateTempId(): String {
        val letters = "abcdefghijklmnopqrstuvwxyz0123456789"
        val sb = StringBuilder()
        repeat(10) {
            sb.append(letters.random())
        }

        return sb.toString()
    }

    private fun createInteractionFor(request: Request): Interaction {
        if (request !is CadenceRequest) {
            throw IllegalStateException("only CadenceRequest are supported")
        }

        val arguments = request.arguments.map { field ->
            Argument(
                field = field,
                asArgument = AsArgument(field.type, field.value.toString()),
                kind = "ARGUMENT",
                tempId = generateTempId(),
                value = field.value.toString(),
                xform = Xform(field.type)
            )
        }
        return Interaction(
            arguments = arguments.associateBy { it.tempId },
            message = Message(
                arguments = arguments.map { it.tempId },
                cadence = request.script,
                computeLimit = request.computeLimit
            )
        )
    }

    class FCLBuilder(internal val activity: AppCompatActivity) {

        internal var appInfo: AppInfo? = null
        internal var serviceProcessor: ServiceProcessor? = null
        internal var gson: Gson? = null
        internal var flowAccessApi: FlowAccessApi? = null
        internal var walletProvider = WalletProvider.BLOCTO
        internal var transformerFactory: TransformerFactory? = null
        internal var ioCoroutineContext: CoroutineContext = Dispatchers.IO
        private var wallet = Wallet.BLOCTO
        private var env = Env.MAIN_NET

        init {
            Flow.configureDefaults(chainId = FlowChainId.MAINNET)
        }

        fun appInfo(block: AppBuilderBuilder.() -> Unit) = apply {
            val builder = AppBuilderBuilder()
            block.invoke(builder)
            appInfo = builder.build()
        }

        fun env(env: Env) = apply {
            this.env = env
            Flow.configureDefaults(chainId = if (env == Env.MAIN_NET) FlowChainId.MAINNET else FlowChainId.TESTNET)
            updateWalletProvider()
        }

        fun wallet(wallet: Wallet) = apply {
            this.wallet = wallet
            updateWalletProvider()
        }

        fun flowAccessApi(flowAccessApi: FlowAccessApi) = apply {
            this.flowAccessApi = flowAccessApi
        }

        fun serviceHelper(serviceProcessor: ServiceProcessor) = apply {
            this.serviceProcessor = serviceProcessor
        }

        fun gson(gson: Gson) = apply {
            this.gson = gson
        }

        fun transformerFactory(transformerFactory: TransformerFactory) = apply {
            this.transformerFactory = transformerFactory
        }

        fun coroutineContext(context: CoroutineContext) = apply {
            this.ioCoroutineContext = context
        }

        fun build(): FCL {
            if (appInfo == null) {
                appInfo = AppBuilderBuilder().build()
            }

            if (flowAccessApi == null) {
                val accessProvider =
                    if (env == Env.MAIN_NET) AccessProvider.MAIN else AccessProvider.TEST_NET
                flowAccessApi = Flow.newAccessApi(
                    accessProvider.url.host,
                    accessProvider.port
                )
            }

            if (gson == null) {
                gson = Gson()
            }

            if (serviceProcessor == null) {
                val url = walletProvider.url
                val interceptor = HttpLoggingInterceptor()
                interceptor.level = HttpLoggingInterceptor.Level.BODY
                val httpServiceApi = Retrofit.Builder()
                    .baseUrl(url.protocol + "://" + url.host + "/")
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .client(
                        OkHttpClient.Builder()
                            .connectTimeout(2, TimeUnit.MINUTES)
                            .callTimeout(2, TimeUnit.MINUTES)
                            .readTimeout(2, TimeUnit.MINUTES)
                            .addInterceptor(interceptor)
                            .build()
                    )
                    .build().create(AppHttpClient.HttpClient::class.java)
                serviceProcessor =
                    AppServiceProcessor(activity, AppHttpClient(httpServiceApi), appInfo!!, gson!!)
            }

            if (transformerFactory == null) {
                transformerFactory =
                    AppTransformerFactory(appInfo!!, flowAccessApi!!, serviceProcessor!!)
            }
            return AppFCL(this)
        }

        private fun updateWalletProvider() {
            walletProvider = if (wallet == Wallet.BLOCTO) {
                if (env == Env.MAIN_NET) WalletProvider.BLOCTO else WalletProvider.BLOCTO_TEST_NET
            } else {
                if (env == Env.MAIN_NET) WalletProvider.DAPPER else WalletProvider.DAPPER_TEST_NET
            }
        }
    }
}
