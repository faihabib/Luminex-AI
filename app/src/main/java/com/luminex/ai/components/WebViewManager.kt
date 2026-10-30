package com.luminex.ai.components

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.luminex.ai.utils.network.BlobDownloaderInterface
import java.io.File
import java.lang.ref.WeakReference


private const val TAG = "WebViewManager"


class WebViewManager(
    private val context: Context,
    private val activity: WeakReference<ComponentActivity>,
    /**
     * Returns true if the WebView managed by this instance is currently
     * attached to the visible view hierarchy. When false (i.e. the tab is
     * pooled in the background), UI callbacks like [onPageFinished] and
     * [onProgressChanged] are suppressed so hidden tabs don't fight the
     * visible one for the status bar / progress indicator.
     *
     * JS-side hooks (download capture, image long-press, etc.) still run
     * for ALL tabs — only the UI callbacks are gated.
     */
    private val isActive: () -> Boolean = { true },
) {
    var onShowFileChooser: (ValueCallback<Array<Uri>>, Intent) -> Unit = { _, _ -> }

    var onShowToast: (message: String) -> Unit = {}

    var onStartActivity: (intent: Intent) -> Unit = {}

    var onPageFinished: (view: WebView, url: String) -> Unit = { _, _ -> }

    var onPermissionRequest: (request: PermissionRequest) -> Unit = { it.deny() }
    var onCameraTmpFileCreated: (Uri) -> Unit = {}
    var onProgressChanged: (Int) -> Unit = {}

    // --- Private State ---

    private var lastUrl: String? = null
    private var lastFailedUrl: String? = null
    private val errorUrl = "file:///android_asset/webview_error.html"
    private val spoofHeaders = mapOf("X-Requested-With" to "")

    // --- Core WebView Clients and Listeners ---

    val webViewClient: WebViewClient = object : WebViewClient() {
        @SuppressLint("QueryPermissionsNeeded")
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return when {
                url.startsWith("http://") || url.startsWith("https://") -> {
                    view.loadUrl(url, spoofHeaders)
                    true
                }
                url.startsWith("intent://") -> handleIntentUrl(url, view)
                url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:") || url.startsWith("market:") -> handleExternalAppUrl(url)
                else -> false
            }
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): android.webkit.WebResourceResponse? {
            val url = request.url.toString()

            // Only intercept Gemini's main HTML document.
            // Skip static assets, API calls, and non-Gemini domains.
            val isGeminiMainDocument =
                url.startsWith("https://gemini.google.com") &&
                request.method == "GET" &&
                !url.contains("/_/") &&
                !url.contains(".js") &&
                !url.contains(".css") &&
                !url.contains(".png") &&
                !url.contains(".jpg") &&
                !url.contains(".svg") &&
                !url.contains(".woff") &&
                !url.contains("?")  // most query URLs are API calls

            if (!isGeminiMainDocument) return null

            return try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = true

                // Copy cookies from the WebView's cookie store
                val cookie = android.webkit.CookieManager.getInstance().getCookie(url)
                if (!cookie.isNullOrEmpty()) {
                    conn.setRequestProperty("Cookie", cookie)
                }
                conn.setRequestProperty("User-Agent", view.settings.userAgentString)
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")

                val stream = conn.inputStream
                val html = stream.bufferedReader(Charsets.UTF_8).readText()
                stream.close()

                // Inject the viewport into <head> BEFORE any other script runs.
                val viewportTag = """<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">"""
                val patched = if (html.contains("<head>", ignoreCase = true)) {
                    html.replaceFirst(
                        Regex("<head[^>]*>", RegexOption.IGNORE_CASE),
                        "$0" + viewportTag
                    )
                } else {
                    html  // nothing to inject into
                }

                android.util.Log.d("WebViewManager", "Injected viewport into Gemini HTML (${html.length} -> ${patched.length} bytes)")

                android.webkit.WebResourceResponse(
                    "text/html",
                    "utf-8",
                    200,
                    "OK",
                    mapOf("Access-Control-Allow-Origin" to "*"),
                    patched.byteInputStream(Charsets.UTF_8)
                )
            } catch (e: Exception) {
                android.util.Log.e("WebViewManager", "Gemini interception failed, falling back to WebView", e)
                null  // let WebView load the page normally
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)

            // === Force Mobile Viewport ASAP ===
            // Runs BEFORE the page's own JS. This makes sure Gemini (and
            // others) see a mobile viewport immediately, so they don't
            // render a desktop layout that has to be reflowed later.
            //
            // The Object.defineProperty at the end "locks" the viewport so
            // the page's own JS can't change it back to a wrong value.
            val viewportScript = """
            (function() {
                function forceViewport() {
                    var meta = document.querySelector('meta[name="viewport"]');
                    if (!meta) {
                        meta = document.createElement('meta');
                        meta.name = 'viewport';
                        var target = document.head || document.documentElement;
                        if (target) {
                            target.appendChild(meta);
                        } else {
                            return;
                        }
                    }
                    try {
                        meta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no');
                    } catch (e) {}
                }
                forceViewport();
                // Re-run once the DOM is ready, in case the site replaced <head>.
                if (document.readyState !== 'complete') {
                    document.addEventListener('DOMContentLoaded', forceViewport, true);
                    document.addEventListener('readystatechange', forceViewport, true);
                }
            })();
            """.trimIndent()

            try {
                view.evaluateJavascript(viewportScript, null)
            } catch (e: Exception) {
                android.util.Log.e("WebViewManager", "viewport inject failed", e)
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)

            val preventRevokeScript = """
            window.URL.revokeObjectURL = function(url) {
                console.log("Luminex: Blocked revocation of " + url);
                // Do nothing! Keep the blob alive.
            };
        """.trimIndent()

            view.evaluateJavascript(preventRevokeScript, null)

            // === Download Hook ===
            // Intercepts download clicks and blob URLs on AI sites,
            // converts to base64, and passes to the AndroidBlob bridge.
            val downloadHookScript = """
            (function() {
                if (window.__geminiproDownloadHook) return;
                window.__geminiproDownloadHook = true;

                // Convert blob to base64 and send to Android
                async function sendBlobToAndroid(blob, filename) {
                    try {
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            var base64 = reader.result;
                            var mime = blob.type || 'application/octet-stream';
                            // Extract extension from filename
                            var ext = '';
                            if (filename) {
                                var parts = filename.split('.');
                                if (parts.length > 1) ext = '.' + parts[parts.length - 1];
                            }
                            AndroidBlob.processBlobData(base64, mime);
                        };
                        reader.readAsDataURL(blob);
                    } catch(e) {
                        console.log('Luminex: error converting blob: ' + e);
                    }
                }

                // Hook 1: intercept HTMLAnchorElement.click for download links
                var originalClick = HTMLAnchorElement.prototype.click;
                HTMLAnchorElement.prototype.click = function() {
                    try {
                        var href = this.href || '';
                        var download = this.getAttribute('download');
                        if (download) {
                            // It's a download link
                            if (href.startsWith('blob:')) {
                                fetch(href)
                                    .then(r => r.blob())
                                    .then(b => sendBlobToAndroid(b, download));
                                return;
                            } else if (href.startsWith('data:')) {
                                // data URL — parse it
                                var mime = href.substring(5, href.indexOf(';'));
                                var base64 = href.substring(href.indexOf(',') + 1);
                                AndroidBlob.processBlobData(base64, mime);
                                return;
                            }
                        }
                    } catch(e) { console.log('Luminex anchor hook error: ' + e); }
                    return originalClick.apply(this, arguments);
                };

                // Hook 2: intercept URL.createObjectURL to catch blobs
                var originalCreateObjectURL = URL.createObjectURL;
                URL.createObjectURL = function(obj) {
                    var url = originalCreateObjectURL.call(URL, obj);
                    try {
                        if (obj instanceof Blob) {
                            window.__geminiproLastBlob = { url: url, blob: obj };
                        }
                    } catch(e) {}
                    return url;
                };

                // Hook 3: monitor clicks on elements with download-related text
                document.addEventListener('click', function(e) {
                    var target = e.target.closest('button, a, [role=button]');
                    if (!target) return;
                    var text = (target.textContent || '').toLowerCase();
                    var label = (target.getAttribute('aria-label') || '').toLowerCase();
                    var testid = (target.getAttribute('data-testid') || '').toLowerCase();

                    var isDownload = text.includes('download') ||
                                     label.includes('download') ||
                                     testid.includes('download');

                    if (isDownload && window.__geminiproLastBlob) {
                        // There's a recently created blob — likely the file to download
                        setTimeout(function() {
                            var last = window.__geminiproLastBlob;
                            if (last && last.blob) {
                                sendBlobToAndroid(last.blob, 'download');
                                window.__geminiproLastBlob = null;
                            }
                        }, 500);
                    }
                }, true);

                console.log('Luminex: Download hook installed');
            })();
            """.trimIndent()

            view.evaluateJavascript(downloadHookScript, null)

            // === DNS Prefetch + Preconnect Hints ===
            // Speeds up cross-origin requests to common AI site CDNs.
            // Runs on every page load; harmless if domains aren't used.
            val preconnectScript = """
            (function() {
                var domains = [
                    'https://aistudio.google.com',
                    'https://gemini.google.com',
                    'https://chatgpt.com',
                    'https://chat.deepseek.com',
                    'https://claude.ai',
                    'https://www.perplexity.ai',
                    'https://grok.com',
                    'https://chat.mistral.ai',
                    'https://notebooklm.google.com',
                    'https://poe.com',
                    'https://chat.qwen.ai',
                    'https://coral.cohere.com',
                    'https://manus.im'
                ];
                var head = document.head || document.getElementsByTagName('head')[0];
                if (!head) return;
                for (var i = 0; i < domains.length; i++) {
                    var pc = document.createElement('link');
                    pc.rel = 'preconnect';
                    pc.href = domains[i];
                    pc.crossOrigin = 'anonymous';
                    head.appendChild(pc);
                    var dp = document.createElement('link');
                    dp.rel = 'dns-prefetch';
                    dp.href = domains[i];
                    head.appendChild(dp);
                }
                console.log('Luminex: preconnect hints injected');
            })();
            """.trimIndent()

            view.evaluateJavascript(preconnectScript, null)


            // === Double-Tap Listener ===
            // Fires Android.onDoubleTap() on dblclick. The Android side
            // checks isFullScreen before invoking the exit callback.
            val doubleTapScript = """
            (function() {
                if (window.__geminiproDoubleTap) return;
                window.__geminiproDoubleTap = true;
                document.addEventListener('dblclick', function(e) {
                    try {
                        Android.onDoubleTap();
                    } catch (err) {
                        console.log('Luminex: onDoubleTap failed: ' + err);
                    }
                }, true);
                console.log('Luminex: double-tap toggle handler installed');
            })();
            """.trimIndent()

            view.evaluateJavascript(doubleTapScript, null)

            // === Universal Long-Press Image Handler ===
            // Catches images that HitTestResult misses: canvas, CSS backgrounds,
            // blob URLs, shadow DOM, and blob/data URL images.
            val longPressImageScript = """
            (function() {
                if (window.__geminiproLongPress) return;
                window.__geminiproLongPress = true;

                // Strict mode: only fire on a real image target.
                // IMPORTANT: We do NOT walk up the DOM tree — that caused
                // false positives when selecting text.
                function getImageInfo(el) {
                    if (!el) return null;

                    // Bail out if the user is selecting text
                    try {
                        var sel = window.getSelection();
                        if (sel && sel.toString().length > 0) return null;
                    } catch (e) {}

                    var tag = (el.tagName || '').toLowerCase();
                    var src = '';
                    var bgImage = '';
                    var dataUrl = '';

                    if (tag === 'img') {
                        src = el.currentSrc || el.src || '';
                    } else if (tag === 'canvas') {
                        try { dataUrl = el.toDataURL('image/png'); } catch (err) {}
                    } else if (tag === 'svg') {
                        try {
                            var serializer = new XMLSerializer();
                            var svgStr = serializer.serializeToString(el);
                            dataUrl = 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(svgStr)));
                        } catch (err) {}
                    } else if (el.style) {
                        // Only check THIS element's own background-image (not ancestors)
                        var bg = getComputedStyle(el).backgroundImage;
                        if (bg && bg !== 'none' && bg.indexOf('url(') === 0) {
                            bgImage = bg.slice(5, -2).replace(/["']/g, '');
                        }
                    }

                    // Require an actual image source — no DOM walking
                    if (!src && !bgImage && !dataUrl) return null;

                    // Require a reasonable minimum size (avoid 1x1 tracking pixels
                    // and tiny decorative backgrounds)
                    try {
                        var rect = el.getBoundingClientRect();
                        if (rect.width < 48 || rect.height < 48) return null;
                    } catch (e) {}

                    return { tag: tag, src: src, bgImage: bgImage, dataUrl: dataUrl };
                }

                document.addEventListener('contextmenu', function(e) {
                    // Double-check: don't fire if text is selected
                    try {
                        var sel = window.getSelection();
                        if (sel && sel.toString().length > 0) return;
                    } catch (err) {}

                    var el = e.composedPath ? e.composedPath()[0] : e.target;
                    var info = getImageInfo(el);
                    if (info) {
                        try {
                            Android.onLongPressElement(info.tag, info.src, info.bgImage, info.dataUrl);
                        } catch (err) {
                            console.log('Luminex: long press callback failed: ' + err);
                        }
                    }
                }, true);

                console.log('Luminex: strict long-press handler installed');
            })();
            """.trimIndent()

            view.evaluateJavascript(longPressImageScript, null)

            if (isActive()) {
                onPageFinished.invoke(view, url)
            }
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            super.onReceivedError(view, request, error)
            if (request?.isForMainFrame == true) {
                val failingUrl = request.url.toString()
                Log.e(TAG, "WebView Error: ${error?.errorCode} - ${error?.description} for $failingUrl")
                lastFailedUrl = failingUrl
                view?.loadUrl(errorUrl)
            }
        }
    }

    val webChromeClient: WebChromeClient = object : WebChromeClient() {
        private var customView: View? = null
        private var customViewCallback: CustomViewCallback? = null
        private var originalOrientation: Int = 0
        private var fullscreenContainer: FrameLayout? = null
        private var onBackPressedCallback: OnBackPressedCallback? = null

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            var photoUri: Uri? = null
            var captureIntent: Intent? = null

            try {
                val photoFile = File.createTempFile("gemini_cam_", ".jpg", context.cacheDir)

                photoUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    photoFile
                )

                photoUri?.let { onCameraTmpFileCreated(it) }

                captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)

            } catch (e: Exception) {
                Log.e(TAG, "Unable to create camera temp file", e)
            }

            val galleryIntent = fileChooserParams.createIntent()

            val chooserIntent = Intent(Intent.ACTION_CHOOSER)
            chooserIntent.putExtra(Intent.EXTRA_INTENT, galleryIntent)
            chooserIntent.putExtra(Intent.EXTRA_TITLE, "Upload Image")

            captureIntent?.let {
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(it))
            }

            onShowFileChooser(filePathCallback, chooserIntent)
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            onPermissionRequest.invoke(request)
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            val currentActivity = activity.get() ?: run {
                callback?.onCustomViewHidden()
                return
            }

            if (customView != null) {
                callback?.onCustomViewHidden()
                return
            }

            customView = view
            customViewCallback = callback
            originalOrientation = currentActivity.requestedOrientation

            val decorView = currentActivity.window.decorView as ViewGroup
            fullscreenContainer = FrameLayout(currentActivity).apply {
                setBackgroundColor(Color.BLACK)
                addView(customView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }

            decorView.addView(fullscreenContainer, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setFullscreen(enabled = true, activity = currentActivity)

            onBackPressedCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { onHideCustomView() }
            }
            currentActivity.onBackPressedDispatcher.addCallback(currentActivity, onBackPressedCallback!!)
        }

        override fun onHideCustomView() {
            val currentActivity = activity.get() ?: return
            if (customView == null) return

            setFullscreen(enabled = false, activity = currentActivity)
            (fullscreenContainer?.parent as? ViewGroup)?.removeView(fullscreenContainer)

            fullscreenContainer = null
            customView = null
            customViewCallback?.onCustomViewHidden()

            onBackPressedCallback?.remove()
            onBackPressedCallback = null
        }

        private fun setFullscreen(enabled: Boolean, activity: Activity) {
            val window = activity.window
            val container = fullscreenContainer ?: return
            WindowInsetsControllerCompat(window, container).let { controller ->
                if (enabled) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            val currentUrl = view?.url

            // Only update the visible UI for the ACTIVE tab.
            // Background tabs still load; we just don't show their progress.
            if (isActive()) {
                onProgressChanged(newProgress)

                if ((newProgress == 100) && (currentUrl != null) && (currentUrl != lastUrl)) {
                    lastUrl = currentUrl
                    onPageFinished.invoke(view, currentUrl)
                }
            }
        }
    }

    fun getDownloadListener(webView: WebView): DownloadListener {
        return DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            when {
                url.startsWith("blob:") -> {
                    val jsScript = """
                        (function() {
                            var xhr = new XMLHttpRequest();
                            xhr.open('GET', '$url', true);
                            xhr.responseType = 'blob';
                            xhr.onload = function(e) {
                                if (this.status == 200) {
                                    var blob = this.response;
                                    var reader = new FileReader();
                                    reader.readAsDataURL(blob);
                                    reader.onloadend = function() {
                                        var base64data = reader.result;
                                        // Call the Android Interface
                                        // 'AndroidBlob' is the name we will register in the next step
                                        AndroidBlob.processBlobData(base64data, blob.type);
                                    }
                                }
                            };
                            xhr.send();
                        })();
                    """.trimIndent()

                    webView.evaluateJavascript(jsScript, null)
                }

                url.startsWith("data:") -> {
                    val cleanUrl = url.substringAfter("base64,")
                    val extractedMime = url.substringAfter("data:").substringBefore(";")
                    BlobDownloaderInterface(context).processBlobData(cleanUrl, extractedMime)
                }

                else -> handleStandardDownload(url, userAgent, contentDisposition, mimeType)
            }
        }
    }

    // --- Private Helper Functions ---

    private fun handleIntentUrl(url: String, webView: WebView): Boolean {
        try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            val fallbackUrl = intent.getStringExtra("browser_fallback_url")

            if (intent.resolveActivity(context.packageManager) != null) {
                onStartActivity(intent)
            } else if (fallbackUrl != null) {
                webView.loadUrl(fallbackUrl, spoofHeaders)
            } else {
                onShowToast("Cannot handle this type of link.")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing intent URL: $url", e)
            onShowToast("Error handling link.")
            return false
        }
    }

    private fun handleExternalAppUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            onStartActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start activity for URL: $url", e)
            onShowToast("Cannot open link.")
            true
        }
    }

    private fun handleStandardDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        try {
            val request = DownloadManager.Request(url.toUri()).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                setTitle(filename)
                setDescription("Downloading file...")
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            onShowToast("Starting download: ${URLUtil.guessFileName(url, contentDisposition, mimeType)}")
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for URL: $url", e)
            onShowToast("Download failed: ${e.message}")
        }
    }
}