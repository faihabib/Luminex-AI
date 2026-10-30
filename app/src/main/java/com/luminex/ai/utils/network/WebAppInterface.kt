package com.luminex.ai.utils.network // Or your preferred package

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.lang.ref.WeakReference


class WebAppInterface(
    private val webViewRef: WeakReference<WebView?>,
    private val getRetryUrl: () -> String,
    private val onLongPressCallback: ((tag: String, src: String, bgImage: String, dataUrl: String) -> Unit)? = null,
    private val onDoubleTapCallback: (() -> Unit)? = null,
    private val onGeminiHydratedCallback: (() -> Unit)? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun retryLastUrl() {
        val urlToRetry = getRetryUrl()
        mainHandler.post {
            webViewRef.get()?.let { webView ->
                webView.clearHistory()
                webView.loadUrl(urlToRetry)
            }
        }
    }

    @JavascriptInterface
    fun onLongPressElement(tag: String, src: String, bgImage: String, dataUrl: String) {
        mainHandler.post {
            onLongPressCallback?.invoke(tag, src, bgImage, dataUrl)
        }
    }

    @JavascriptInterface
    fun onDoubleTap() {
        mainHandler.post {
            onDoubleTapCallback?.invoke()
        }
    }
}