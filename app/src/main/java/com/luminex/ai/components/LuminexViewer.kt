package com.luminex.ai.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.luminex.ai.models.ImageTarget
import com.luminex.ai.utils.network.WebAppInterface
import java.lang.ref.WeakReference

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun GeminiWebViewer(
    modifier: Modifier,
    siteUrl: String,                     // ← the picker key, e.g. "https://chatgpt.com"
    pool: WebViewPool,                   // ← the pool this viewer belongs to
    customUa: String = "",
    desktopMode: Boolean = false,
    filePathCallbackState: MutableState<ValueCallback<Array<Uri>>?>,
    filePickerLauncher: ActivityResultLauncher<Intent>?,
    isVideoSelectionMode: Boolean,
    onWebViewCreated: (WebView) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onPageFinished: (WebView, String) -> Unit,
    onCameraTmpFileCreated: (Uri) -> Unit,
    onImageLongPress: ((ImageTarget) -> Unit)? = null,
    isFullScreen: Boolean = false,
    onToggleFullScreen: (() -> Unit)? = null,
    onGeminiHydrated: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? ComponentActivity
    val spoofHeaders = mapOf("X-Requested-With" to "")

    val currentVideoMode by rememberUpdatedState(isVideoSelectionMode)
    val currentCameraCallback by rememberUpdatedState(onCameraTmpFileCreated)
    val currentProgressCallback by rememberUpdatedState(onProgressChanged)
    val currentPageFinishedCallback by rememberUpdatedState(onPageFinished)
    val lastTouchX = remember { mutableFloatStateOf(0f) }
    val lastTouchY = remember { mutableFloatStateOf(0f) }
    val currentIsFullScreen by rememberUpdatedState(isFullScreen)
    val currentOnToggleFullScreen by rememberUpdatedState(onToggleFullScreen)
    val currentOnGeminiHydrated by rememberUpdatedState(onGeminiHydrated)

    val noVideoFoundText = stringResource(com.luminex.ai.R.string.no_video_found)
    val currentNoVideoFoundText by rememberUpdatedState(noVideoFoundText)

    // A single, persistent FrameLayout that survives recompositions.
    // The pool attaches/detaches WebViews to/from this container.
    val hostFrame = remember(context) {
        FrameLayout(context).apply { fitsSystemWindows = true }
    }

    // Give the pool a reference to the host FrameLayout (idempotent).
    remember(hostFrame) {
        pool.setHost(hostFrame)
        true
    }

    // A per-site map of WebViewManager instances. Each pooled WebView gets
    // its own manager so callbacks don't cross-contaminate between tabs.
    val managers = remember { mutableMapOf<String, WebViewManager>() }

    AndroidView(
        modifier = modifier,
        factory = { hostFrame }
    )

    // Cleanup: destroy pooled WebViews when this composable leaves the tree.
    // Happens when the user disables split-screen (bottom pane is removed)
    // or when the activity is destroyed.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            pool.destroyAll()
        }
    }

    // React to site changes: activate the pooled WebView, or create a new one.
    LaunchedEffect(siteUrl, customUa, desktopMode) {
        // Case 1: we already have a live WebView for this site → just show it.
        pool.getOrNull(siteUrl)?.let { existing ->
            pool.setActive(siteUrl)
            onWebViewCreated(existing)
            return@LaunchedEffect
        }

        // Case 2: brand new → create WebView and load homepage.
        val urlToLoad = siteUrl

        val manager = WebViewManager(context, WeakReference(activity)).apply {
            this.onShowFileChooser = { cb, intent ->
                filePathCallbackState.value = cb
                try {
                    filePickerLauncher?.launch(intent)
                } catch (e: Exception) {
                    Log.e("GeminiWebViewer", "Cannot open file picker", e)
                    filePathCallbackState.value?.onReceiveValue(null)
                    filePathCallbackState.value = null
                }
            }
            this.onProgressChanged = { currentProgressCallback(it) }
            this.onPageFinished = { view, url -> currentPageFinishedCallback(view, url) }
            this.onPermissionRequest = { request -> request.grant(request.resources) }
            this.onCameraTmpFileCreated = { uri -> currentCameraCallback(uri) }
        }
        managers[siteUrl] = manager

        val webView = WebView(context).apply {
            
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setBackgroundColor(Color.Transparent.toArgb())

            addJavascriptInterface(
                com.luminex.ai.utils.network.BlobDownloaderInterface(context),
                "AndroidBlob",
            )

            webViewClient = manager.webViewClient
            webChromeClient = manager.webChromeClient
            setDownloadListener(manager.getDownloadListener(this))

            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                allowFileAccess = false
                allowContentAccess = false
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                blockNetworkImage = false

                val defaultUserAgent = userAgentString ?: ""
                val chromeVersionToken = try {
                    val pattern = "Chrome/[.0-9]+".toRegex()
                    pattern.find(defaultUserAgent)?.value ?: "Chrome/144.0.0.0"
                } catch (_: Exception) {
                    "Chrome/144.0.0.0"
                }
                userAgentString = if (customUa.isNotBlank()) {
                    customUa
                } else if (desktopMode) {
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) $chromeVersionToken Safari/537.36"
                } else {
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) $chromeVersionToken Mobile Safari/537.36"
                }
            }

            addJavascriptInterface(
                WebAppInterface(
                    webViewRef = WeakReference(this as WebView?),
                    getRetryUrl = { urlToLoad },
                    onLongPressCallback = { tag, src, bgImage, dataUrl ->
                        onImageLongPress?.invoke(
                            ImageTarget(tag = tag, src = src, bgImage = bgImage, dataUrl = dataUrl)
                        )
                    },
                    onDoubleTapCallback = {
                        // Toggle full screen (enter if normal, exit if full screen)
                        currentOnToggleFullScreen?.invoke()
                    },
                    onGeminiHydratedCallback = {
                        currentOnGeminiHydrated?.invoke()
                    }
                ),
                "Android"
            )

            setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    lastTouchX.floatValue = event.x
                    lastTouchY.floatValue = event.y
                }
                false
            }

            setOnCreateContextMenuListener { menu, v, _ ->
                val hitTestWebView = v as WebView
                val result = hitTestWebView.hitTestResult

                if (currentVideoMode) {
                    menu.add("Download Video Here").setOnMenuItemClickListener {
                        val density = resources.displayMetrics.density
                        val cssX = lastTouchX.floatValue / density
                        val cssY = lastTouchY.floatValue / density

                        val js = """
                     (function() {
                         var el = document.elementFromPoint($cssX, $cssY);
                         while (el && el.tagName !== 'VIDEO') {
                             el = el.parentElement;
                         }
                         if (el && el.tagName === 'VIDEO') {
                             return el.currentSrc;
                         }
                         return null;
                     })();
                 """.trimIndent()

                        evaluateJavascript(js) { resultJs ->
                            val url = resultJs?.replace("\"", "")
                            if (!url.isNullOrEmpty() && (url != "null")) {
                                manager.getDownloadListener(this).onDownloadStart(
                                    url,
                                    settings.userAgentString,
                                    "video.mp4",
                                    "video/mp4",
                                    0L
                                )
                            } else {
                                Toast.makeText(
                                    context,
                                    currentNoVideoFoundText,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                        true
                    }
                }
            }

            loadUrl(urlToLoad, spoofHeaders)
        }

        pool.add(siteUrl, webView)
        pool.setActive(siteUrl)
        onWebViewCreated(webView)
    }
}
