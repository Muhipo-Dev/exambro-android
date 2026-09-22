package com.muhipo.exambrowser.exam

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.muhipo.exambrowser.security.SecurityManager
import com.muhipo.exambrowser.utils.UrlValidator

class ExamWebClient(
    private val allowedDomainsProvider: () -> Set<String>,
    private val listener: Listener
) : WebViewClient() {

    interface Listener {
        fun onPageLoading(isPageLoading: Boolean)
        fun onDomainBlocked(blockedUrl: String)
        fun onLoadFailed(errorCode: Int, description: String)
        fun onSslErrorOccurred(handler: SslErrorHandler, error: SslError)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return true
        return handleUrlNavigation(view, url)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return true
        return handleUrlNavigation(view, url)
    }

    private fun handleUrlNavigation(view: WebView?, url: String): Boolean {
        // Enforce HTTP / HTTPS scheme only
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            // Block external intents, market://, tel://, etc.
            listener.onDomainBlocked(url)
            return true
        }

        val allowedDomains = allowedDomainsProvider()
        if (UrlValidator.isUrlAllowed(url, allowedDomains)) {
            // Internal navigation allowed within exam environment
            return false
        } else {
            // Blocked: Domain outside whitelist
            listener.onDomainBlocked(url)
            return true
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        listener.onPageLoading(true)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        listener.onPageLoading(false)
        view?.let {
            SecurityManager.injectAntiCopyCss(it)
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            listener.onLoadFailed(
                error?.errorCode ?: -1,
                error?.description?.toString() ?: "Network error"
            )
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame == true) {
            val status = errorResponse?.statusCode ?: 0
            if (status >= 400) {
                listener.onLoadFailed(status, "HTTP Error $status")
            }
        }
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
    ) {
        if (handler != null && error != null) {
            listener.onSslErrorOccurred(handler, error)
        } else {
            handler?.cancel()
        }
    }
}
