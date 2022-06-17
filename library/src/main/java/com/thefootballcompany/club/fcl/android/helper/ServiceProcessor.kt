package com.thefootballcompany.club.fcl.android.helper

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.thefootballcompany.club.fcl.android.BuildConfig
import com.thefootballcompany.club.fcl.android.R
import com.thefootballcompany.club.fcl.android.extension.toLocal
import com.thefootballcompany.club.fcl.android.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.TimeoutException

/**
 * Created by muriel on 10.06.2022..
 */
interface ServiceProcessor {

    suspend fun executeService(
        service: Service,
        body: Any? = null,
        location: String? = null,
        headers: Map<String, String>? = null,
        pauseMs: Long = 500L,
        timeoutMs: Long = 120_000L,
    ): AuthResponse

    suspend fun executeService(
        method: ServiceMethod,
        url: String,
        body: Any? = null,
        headers: Map<String, String>? = null,
        pauseMs: Long = 500L,
        timeoutMs: Long = 120_000L,
    ): AuthResponse
}

class AppServiceProcessor(
    private val activity: AppCompatActivity,
    private val httpHelper: HttpClient,
    private val appInfo: AppInfo,
    private val gson: Gson
) : ServiceProcessor {

    companion object {
        private val TAG = this.javaClass.simpleName
    }

    private var bottomSheetDialogFragment: BottomSheetDialogFragment? = null

    override suspend fun executeService(
        service: Service,
        body: Any?,
        location: String?,
        headers: Map<String, String>?,
        pauseMs: Long, timeoutMs: Long,
    ): AuthResponse {
        return executeService(
            methodToServiceMethod(service.method!!),
            buildUrlFor(service, location).toString(),
            body,
            headers,
            pauseMs,
            timeoutMs,
        )
    }

    override suspend fun executeService(
        method: ServiceMethod,
        url: String,
        body: Any?,
        headers: Map<String, String>?,
        pauseMs: Long,
        timeoutMs: Long,
    ): AuthResponse {
        check(pauseMs < timeoutMs) {
            "timeout < pause"
        }

        var authResponse = execute(method, url, body, headers)

        if (authResponse.isDeclined() || authResponse.isApproved()) {
            return authResponse
        }

        val localService = authResponse.toLocal(gson)
            ?: throw NullPointerException("Local service is null for $authResponse")

        try {
            //Request to open Web!
            openAuthTab(localService)
            val startTime = System.nanoTime()
            while (currentCoroutineContext().isActive && authResponse.isPending()) {
                check(isDialogOpen()) {
                    "user probably canceled or dismissed the dialog"
                }

                //We want to give some time to user first, before pinging for updates
                delay(pauseMs)

                val elapseTime = (System.nanoTime() - startTime) / 1000_000

                if (elapseTime >= timeoutMs) {
                    throw TimeoutException()
                }

                val updateService = authResponse.updates ?: authResponse.authorizationUpdates
                ?: throw NullPointerException("update service is null for $authResponse")

                authResponse = execute(updateService)

            }
        } finally {
            closeAuthTab()
        }

        return authResponse
    }

    private suspend fun execute(
        service: Service,
        body: Any? = null,
        headers: Map<String, String>? = null
    ): AuthResponse {
        val method = methodToServiceMethod(service.method!!)
        val url = buildUrlFor(service).toString()

        return execute(method, url, body, headers)
    }

    private suspend fun execute(
        method: ServiceMethod,
        url: String,
        body: Any? = null,
        headers: Map<String, String>? = null
    ): AuthResponse {

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "executing service : method = $method url = $url body = $body header = headers"
            )
        }

        val authResponse = when (method) {
            ServiceMethod.HTTP_POST -> httpHelper.executePost(url, body, headers)
            ServiceMethod.HTTP_GET -> httpHelper.executeGet(url)
            else -> {
                throw  IllegalArgumentException("unsupported method $method")
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "response = $authResponse")
        }

        return authResponse
    }

    private fun buildUrlFor(service: Service, location: String? = null): Uri {
        return buildUrlFor(service.endpoint, service.params, location)
    }

    private fun buildUrlFor(
        url: String,
        params: Map<String, String>?,
        location: String? = null
    ): Uri {
        val baseUri = Uri.parse(url).buildUpon()

        params?.let { params ->
            for ((key, value) in params) {
                baseUri.appendQueryParameter(key, value)
            }
        }

        if (location != null) {
            baseUri.appendQueryParameter("l6n", location)
        }
        return baseUri.build()
    }

    private fun openAuthTab(
        service: Service,
    ) {
        val uri = buildUrlFor(service.endpoint, service.params, appInfo.location)
        val bottomSheetDialogFragment =
            WebViewBottomSheetDialogFragment.newInstance(uri.toString())

        bottomSheetDialogFragment.showNow(
            activity.supportFragmentManager,
            this.javaClass.simpleName
        )

        this.bottomSheetDialogFragment = bottomSheetDialogFragment
    }

    private fun closeAuthTab() {
        bottomSheetDialogFragment?.dismissAllowingStateLoss()
        bottomSheetDialogFragment = null
    }

    private fun isDialogOpen(): Boolean {
        return bottomSheetDialogFragment != null && bottomSheetDialogFragment!!.requireDialog().isShowing
    }

    class WebViewBottomSheetDialogFragment : BottomSheetDialogFragment() {

        companion object {

            private const val URL_BUNDLE_KEY =
                "com.thefootballcompany.club.fcl.android.helper.AppServiceProcessor.WebViewBottomSheetDialogFragment"

            fun newInstance(url: String): WebViewBottomSheetDialogFragment {
                return WebViewBottomSheetDialogFragment().also {
                    it.arguments = bundleOf(URL_BUNDLE_KEY to url)
                }
            }
        }

        lateinit var webView: WebView
        lateinit var progressView: View

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val view = inflater.inflate(R.layout.layout_dialog, container, false)

            val size = getWindowSize()
            view.layoutParams = ViewGroup.LayoutParams(size.width, size.height)
            webView = view.findViewById(R.id.web_view)
            progressView = view.findViewById(R.id.progressBar)
            initializeWebView()
            return view
        }

        private fun initializeWebView() {
            webView.webViewClient = WebViewClient()
            webView.webChromeClient = object : WebChromeClient() {

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    progressView.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
                }
            }

            webView.settings.apply {
                javaScriptEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                domStorageEnabled = true
            }

            webView.loadUrl(requireArguments().getString(URL_BUNDLE_KEY, ""))
        }

        private fun getWindowSize(): Size {
            val displayMetrics = requireActivity().resources.displayMetrics
            return Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }

        override fun getTheme(): Int = R.style.TFCBottomSheetDialogTheme

        override fun onDestroyView() {
            super.onDestroyView()
            webView.stopLoading()
        }
    }
}
