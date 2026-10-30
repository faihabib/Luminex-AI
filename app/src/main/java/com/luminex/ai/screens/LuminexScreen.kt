package com.luminex.ai.screens

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.luminex.ai.R
import com.luminex.ai.models.ImageTarget
import com.luminex.ai.components.*
import com.luminex.ai.utils.permissions.GetPermissions
import com.luminex.ai.viewmodels.*
import kotlinx.coroutines.delay


@Composable
fun LuminexScreen(
    modifier: Modifier = Modifier,
    viewModel: LuminexViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var webView by remember { mutableStateOf<WebView?>(null) }
    var secondaryWebView by remember { mutableStateOf<WebView?>(null) }
    var imageMenuTarget by remember { mutableStateOf<ImageTarget?>(null) }

    // Two independent WebView pools — one per pane.
    // Created once; NOT recreated on split toggle (that would destroy tabs).
    val topPanePool = remember { WebViewPool("top", maxSize = 5) }
    val bottomPanePool = remember { WebViewPool("bottom", maxSize = 5) }

    // Pause / resume WebViews when the app goes to background / foreground.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    topPanePool.pauseAll()
                    bottomPanePool.pauseAll()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    topPanePool.resumeAll()
                    bottomPanePool.resumeAll()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showSettings by remember { mutableStateOf(value = false) }
    var showDiagram by remember { mutableStateOf(value = false) }
    var showHtmlPreview by remember { mutableStateOf(value = false) }
    var clipboardText by remember { mutableStateOf(value = "") }
    var isHighlightingMenu by remember { mutableStateOf(value = false) }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }


    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(isKeyboardVisible) {
        viewModel.onEvent(GeminiUiEvent.KeyboardVisibilityChanged(isKeyboardVisible))
    }

    // --- File Picker Setup ---
    val filePathCallbackState = remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (filePathCallbackState.value == null) return@rememberLauncherForActivityResult

        val uris: Array<Uri> = if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data

            if (data?.data != null) {
                arrayOf(data.data!!)
            }
            else if (data?.clipData != null) {
                val clip = data.clipData!!
                List(clip.itemCount) { clip.getItemAt(it).uri }.toTypedArray()
            }
            else if (tempCameraUri != null) {
                arrayOf(tempCameraUri!!)
            }
            else {
                emptyArray()
            }
        } else {
            emptyArray()
        }

        filePathCallbackState.value?.onReceiveValue(uris)
        filePathCallbackState.value = null
        tempCameraUri = null
    }

    // --- Document Launcher for "Save to File" ---
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { stream ->
                stream.write(clipboardText.toByteArray())
            }
        }
    }

    // --- Side Effect Handler ---
    LaunchedEffect(Unit) {
        viewModel.sideEffectFlow.collect { effect ->
            val currentWebView = webView ?: return@collect
            when (effect) {
                is GeminiSideEffect.LaunchIntent -> context.startActivity(effect.intent)
                is GeminiSideEffect.ShowToast -> Toast.makeText(
                    context,
                    effect.message,
                    Toast.LENGTH_SHORT,
                ).show()

                is GeminiSideEffect.LaunchSaveToFile -> documentLauncher.launch("gemini-note.txt")
                is GeminiSideEffect.WebViewGoBack -> currentWebView.goBack()
                is GeminiSideEffect.WebViewReload -> currentWebView.reload()
                is GeminiSideEffect.WebViewGoForward -> currentWebView.goForward()
                is GeminiSideEffect.LoadUrl -> currentWebView.loadUrl(effect.url)
            }
        }
    }

    // --- Clipboard Monitor ---
    LaunchedEffect(clipboardManager) {
        clipboardManager.addPrimaryClipChangedListener {
            val clipData = clipboardManager.primaryClip
            if ((clipData != null) && (clipData.itemCount > 0)) {
                val text = clipData.getItemAt(0).text.toString()
                clipboardText = text
                viewModel.onClipboardTextChanged(text)
            }
        }
    }

    // --- Back Handler Logic ---
    BackHandler(enabled = uiState.canWebViewGoBack) {
        viewModel.onEvent(GeminiUiEvent.BackButtonPressed)
    }


    // --- Menu Highlight Animation Trigger ---
    LaunchedEffect(uiState.isMenuLeft) {
        isHighlightingMenu = true
        delay(2000)
        isHighlightingMenu = false
    }

    // --- UI Layer ---
    GetPermissions(context)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (!uiState.fullScreen) {
            TopBar(
                siteName = com.luminex.ai.data.AI_SITES
                    .getOrNull(uiState.currentSiteIndex)?.name ?: "AI Studio",
                onMenuClick = { showSettings = true },
                onSitePickerClick = { viewModel.onEvent(GeminiUiEvent.OpenSitePicker) },
            onFullScreenClick = { viewModel.onEvent(GeminiUiEvent.ToggleFullScreen) }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding(),
        ) {
        if (uiState.splitScreen) {
            // === SPLIT SCREEN MODE ===
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // TOP PANE
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(uiState.splitRatio)
                ) {
                    GeminiWebViewer(
                        modifier = Modifier.fillMaxSize(),
                        siteUrl = com.luminex.ai.data.AI_SITES
                            .getOrNull(uiState.currentSiteIndex)?.url
                            ?: com.luminex.ai.data.AI_SITES[0].url,
                        pool = topPanePool,
                        customUa = uiState.customUa,
                        filePathCallbackState = filePathCallbackState,
                        filePickerLauncher = filePickerLauncher,
                        isVideoSelectionMode = uiState.isVideoSelectionMode,
                        onWebViewCreated = { createdWebView ->
                            webView = createdWebView
                        },
                        onProgressChanged = { progress ->
                            viewModel.onEvent(GeminiUiEvent.LoadingProgressChanged(progress))
                        },
                        onPageFinished = { webView, _ ->
                            viewModel.onEvent(
                                GeminiUiEvent.WebViewNavigated(
                                    canGoBack = webView.canGoBack(),
                                    url = webView.url,
                                )
                            )
                            viewModel.onEvent(GeminiUiEvent.ApplicationReady)
                        },
                        onCameraTmpFileCreated = {
                            tempCameraUri = it
                        },
                        onImageLongPress = { imageMenuTarget = it },
                        isFullScreen = uiState.fullScreen,
                        onToggleFullScreen = { viewModel.onEvent(GeminiUiEvent.ToggleFullScreen) },
                        onGeminiHydrated = { viewModel.onEvent(GeminiUiEvent.GeminiHydrationChanged(false)) }
                    )
                }

                // DRAGGABLE DIVIDER BETWEEN PANES
                DraggableDivider(
                    onDragDelta = { delta ->
                        // Convert pixel delta to ratio delta.
                        // We need the total screen height — approximate it
                        // by using LocalConfiguration.
                        val screenHeightPx = context.resources.displayMetrics.heightPixels.toFloat()
                        val ratioDelta = delta / screenHeightPx
                        val newRatio = (uiState.splitRatio + ratioDelta).coerceIn(0.2f, 0.8f)
                        viewModel.onEvent(GeminiUiEvent.UpdateSplitRatio(newRatio))
                    }
                )

                // BOTTOM PANE (SECONDARY)
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f - uiState.splitRatio)
                ) {
                    // Compact secondary header — tappable to change the AI
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                viewModel.onEvent(GeminiUiEvent.OpenSecondarySitePicker)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Text(
                            text = (com.luminex.ai.data.AI_SITES
                                .getOrNull(uiState.secondarySiteIndex)?.name ?: "AI Studio") + "  \u25BE",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    }

                    androidx.compose.material3.HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        GeminiWebViewer(
                            modifier = Modifier.fillMaxSize(),
                            siteUrl = com.luminex.ai.data.AI_SITES
                                .getOrNull(uiState.secondarySiteIndex)?.url
                                ?: com.luminex.ai.data.AI_SITES[0].url,
                            pool = bottomPanePool,
                            customUa = uiState.customUa,
                            filePathCallbackState = filePathCallbackState,
                            filePickerLauncher = filePickerLauncher,
                            isVideoSelectionMode = uiState.isVideoSelectionMode,
                            onWebViewCreated = { createdWebView ->
                                secondaryWebView = createdWebView
                            },
                            onProgressChanged = { },
                            onPageFinished = { _, _ -> },
                            onCameraTmpFileCreated = { },
                            onImageLongPress = { imageMenuTarget = it },
                            isFullScreen = uiState.fullScreen,
                            onToggleFullScreen = { viewModel.onEvent(GeminiUiEvent.ToggleFullScreen) },
                            onGeminiHydrated = { viewModel.onEvent(GeminiUiEvent.GeminiHydrationChanged(false)) }
                        )
                    }
                }
            }
        } else {
            // === SINGLE PANE MODE (ORIGINAL) ===
            GeminiWebViewer(
                modifier = Modifier.fillMaxSize(),
                siteUrl = com.luminex.ai.data.AI_SITES
                    .getOrNull(uiState.currentSiteIndex)?.url
                    ?: com.luminex.ai.data.AI_SITES[0].url,
                pool = topPanePool,
                customUa = uiState.customUa,
                filePathCallbackState = filePathCallbackState,
                filePickerLauncher = filePickerLauncher,
                isVideoSelectionMode = uiState.isVideoSelectionMode,
                onWebViewCreated = { createdWebView ->
                    webView = createdWebView
                },
                onProgressChanged = { progress ->
                    viewModel.onEvent(GeminiUiEvent.LoadingProgressChanged(progress))
                },
                onPageFinished = { webView, _ ->
                    viewModel.onEvent(
                        GeminiUiEvent.WebViewNavigated(
                            canGoBack = webView.canGoBack(),
                            url = webView.url,
                        )
                    )
                    viewModel.onEvent(GeminiUiEvent.ApplicationReady)
                },
                onCameraTmpFileCreated = {
                    tempCameraUri = it
                },
                onImageLongPress = { imageMenuTarget = it },
                isFullScreen = uiState.fullScreen,
                onToggleFullScreen = { viewModel.onEvent(GeminiUiEvent.ToggleFullScreen) },
                onGeminiHydrated = { viewModel.onEvent(GeminiUiEvent.GeminiHydrationChanged(false)) }
            )
        }
        // === Full Screen Exit ===
        // Handled via double-tap anywhere on the WebView (see LuminexViewer.kt).
        // No overlay needed, so the WebView receives all touches normally.

        // === Gemini Hydration Veil ===
        // Gemini shows a broken desktop-ish layout for ~1.5 seconds while
        // it hydrates into the mobile layout. This overlay hides that
        // transition. Only shown when the current site is Gemini.
        if (uiState.isGeminiHydrating && !uiState.splitScreen) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Loading Gemini…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        }

        ReloadIndicator(isLoading = uiState.isReloading)

        VideoModeIndicator(uiState.isVideoSelectionMode)

        PreviewButtons(
            clipboardContentType = uiState.clipboardContentType,
            onShowDiagram = { showDiagram = true },
            onShowHtml = { showHtmlPreview = true }
        )

        // MenuHandles removed — the side swipe handle was awkward
        // The ✦ pill in the top bar now opens the menu

        SettingsDrawer(
            isOpen = showSettings,
            initialTheme = uiState.theme,
            initialDefaultAi = uiState.defaultAiIndex,
            initialAutoLoadLast = uiState.autoLoadLast,
            initialDesktopMode = uiState.desktopMode,
            initialSplitScreen = uiState.splitScreen,
            initialCustomUa = uiState.customUa,
            initialFontSize = uiState.fontSize.toFloat(),
            initialPreventScreenshots = uiState.preventScreenshots,
            onDismiss = { showSettings = false },
            onSave = { data ->
                viewModel.onEvent(
                    GeminiUiEvent.SaveSettings(
                        theme = data.theme,
                        defaultAiIndex = data.defaultAi,
                        autoLoadLast = data.autoLoadLast,
                        desktopMode = data.desktopMode,
                        splitScreen = data.splitScreen,
                        customUa = data.customUa,
                        fontSize = data.fontSize.toInt(),
                        preventScreenshots = data.preventScreenshots
                    )
                )
                showSettings = false
            }
        )


        DiagramScreen(
            isDiagramVisible = showDiagram,
            clipboardText = clipboardText,
            onBack = { showDiagram = false },
            onClose = { showDiagram = false }
        )

        HtmlPreviewScreen(
            isHtmlPreviewVisible = showHtmlPreview,
            clipboardText = clipboardText,
            onBack = { showHtmlPreview = false },
            onClose = { showHtmlPreview = false }
        )

        if (uiState.showSitePicker) {
            SitePickerDialog(
                currentIndex = uiState.currentSiteIndex,
                fontSize = uiState.fontSize,
                onSelect = { index ->
                    viewModel.onEvent(GeminiUiEvent.SwitchSite(index))
                },
                onDismiss = {
                    viewModel.onEvent(GeminiUiEvent.CloseSitePicker)
                }
            )
        }

        // Secondary Site Picker (Split Screen)
        if (uiState.showSecondarySitePicker) {
            SitePickerDialog(
                currentIndex = uiState.secondarySiteIndex,
                fontSize = uiState.fontSize,
                onSelect = { index ->
                    viewModel.onEvent(GeminiUiEvent.SwitchSecondarySite(index))
                },
                onDismiss = {
                    viewModel.onEvent(GeminiUiEvent.CloseSecondarySitePicker)
                }
            )
        }

        // === Image Long-Press Menu (from JS bridge) ===
        imageMenuTarget?.let { target ->
            val targetWebView = if (uiState.splitScreen) secondaryWebView else webView
            ImageLongPressMenu(
                target = target,
                onDismiss = { imageMenuTarget = null },
                onSaveImage = {
                    val url = target.bestUrl()
                    if (url.isBlank()) {
                        android.widget.Toast.makeText(context, "No image URL found", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        val mime = when {
                            target.dataUrl.startsWith("data:") -> target.dataUrl.substringAfter("data:").substringBefore(";")
                            target.src.contains(".") -> "image/" + target.src.substringAfterLast(".").substringBefore("?").lowercase()
                            else -> "image/png"
                        }
                        val safeMime = if (mime.startsWith("image/")) mime else "image/png"

                        if (target.isDataUrl) {
                            // Data URL: pass directly to AndroidBlob
                            val base64 = url.substringAfter(",")
                            targetWebView?.evaluateJavascript(
                                "AndroidBlob.processBlobData('$base64', '$safeMime')", null
                            )
                        } else if (target.isBlobUrl || target.isHttpUrl) {
                            // Blob or HTTP URL: fetch via JS (with cookies), convert, save
                            val js = """
                                (async function() {
                                    try {
                                        var resp = await fetch('$url', { credentials: 'include' });
                                        var blob = await resp.blob();
                                        var reader = new FileReader();
                                        reader.onloadend = function() {
                                            AndroidBlob.processBlobData(reader.result, blob.type || '$safeMime');
                                        };
                                        reader.readAsDataURL(blob);
                                    } catch(e) {
                                        console.log('Luminex: image fetch failed: ' + e);
                                    }
                                })();
                            """.trimIndent()
                            targetWebView?.evaluateJavascript(js, null)
                        } else {
                            // Fallback: native download
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url)
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Cannot handle URL", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onCopyImageUrl = {
                    val url = target.bestUrl()
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Image URL", url))
                    android.widget.Toast.makeText(context, "Image URL copied", android.widget.Toast.LENGTH_SHORT).show()
                },
                onOpenImageInBrowser = {
                    val url = target.bestUrl()
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "No browser found", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onShareImage = {
                    val url = target.bestUrl()
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, url)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Image URL"))
                }
            )
        }

        if (uiState.updateInfo != null) {
            UpdateAvailableDialog(
                newVersion = uiState.updateInfo!!.version,
                changelog = uiState.updateInfo!!.changelog,
                onDismiss = { viewModel.onEvent(GeminiUiEvent.DismissUpdateDialog) },
                onDownload = { viewModel.onEvent(GeminiUiEvent.UpdateClicked) },
                onSkipVersion = { viewModel.onEvent(GeminiUiEvent.SkipUpdateClicked) }
            )
        }
        }  // ← closes inner Box

        if (!uiState.fullScreen) {
        BottomBar(
                isSplitActive = uiState.splitScreen,
            onRefresh = { viewModel.onEvent(GeminiUiEvent.ReloadPageClicked) },
            onSplit = { viewModel.onEvent(GeminiUiEvent.ToggleSplitScreen) },
            onDownload = {
                // Trigger download of the most recent blob created on the page.
                // The WebViewManager JS hook has been tracking blobs since page load.
                val targetWebView = if (uiState.splitScreen) secondaryWebView else webView
                val jsTrigger = """
                    (function() {
                        // 1. Try the tracked blob first
                        if (window.__geminiproLastBlob && window.__geminiproLastBlob.blob) {
                            var reader = new FileReader();
                            var blob = window.__geminiproLastBlob.blob;
                            reader.onloadend = function() {
                                AndroidBlob.processBlobData(reader.result, blob.type || 'application/octet-stream');
                            };
                            reader.readAsDataURL(blob);
                            window.__geminiproLastBlob = null;
                            return;
                        }
                        // 2. Fallback: click any visible download button on the page
                        var candidates = document.querySelectorAll('a[download], button[aria-label*=ownload], [data-testid*=ownload]');
                        for (var i = 0; i < candidates.length; i++) {
                            try { candidates[i].click(); return; } catch(e) {}
                        }
                        console.log('Luminex: no download target found');
                    })();
                """.trimIndent()
                targetWebView?.evaluateJavascript(jsTrigger, null)
            },
                onHome = { webView?.loadUrl(uiState.activeWebViewUrl ?: "") },
                onSwap = { viewModel.onEvent(GeminiUiEvent.SwapPanes) }
            )
        }
    }  // ← closes Column
}

@Composable
fun BoxScope.BrowserProgressBar(progress: Int) {
    val isVisible = ((progress in 1..99))

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(4.dp)
            .align(Alignment.TopCenter)
    ) {
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
        )
        }
    }

@Composable
private fun BoxScope.VideoModeIndicator(isVisible: Boolean){
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .background(Color(0xFF2196F3).copy(alpha = 0.8f))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.tap_and_hold_video),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BoxScope.PreviewButtons(
    clipboardContentType: ClipboardContentType,
    onShowDiagram: () -> Unit,
    onShowHtml: () -> Unit
) {
    val isVisible = clipboardContentType != ClipboardContentType.NONE
    val buttonText = when (clipboardContentType) {
        ClipboardContentType.DIAGRAM -> stringResource(R.string.show_diagram)
        ClipboardContentType.HTML -> stringResource(R.string.preview_html)
        else -> ""
    }
    val onClick = when (clipboardContentType) {
        ClipboardContentType.DIAGRAM -> onShowDiagram
        ClipboardContentType.HTML -> onShowHtml
        else -> ({})
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier.align(Alignment.BottomEnd)
    ) {
        DialogButton(text = buttonText, onClick = onClick)
    }
}

@Composable
private fun BoxScope.MenuHandles(
    isMenuLeft: Boolean,
    isHighlighting: Boolean,
    onReload: () -> Unit,
    onOpenMenu: () -> Unit
) {
    // Top-center reload gesture removed — was blocking top bar touches
    // Refresh button will be added to the bottom bar (Stage 3)

    val animatedColor by animateColorAsState(
        targetValue = if (isHighlighting) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f) else Color.Transparent,
        animationSpec = tween(durationMillis = 500),
        label = "menuHighlightColor"
    )
    val alignment = if (isMenuLeft) Alignment.CenterStart else Alignment.CenterEnd

    Box(
        modifier = Modifier
            .align(alignment)
            .width(30.dp)
            .fillMaxHeight(0.4f)
            .background(animatedColor)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    onOpenMenu()
                    change.consume()
                }
            }
    )
}

@Composable
private fun rememberMenuItems(onEvent: (GeminiUiEvent) -> Unit, isMenuLeft: Boolean): List<MenuItemData> {
    return remember(isMenuLeft) {
        listOf(
            MenuItemData(R.drawable.google_docs, "Open Docs") { onEvent(GeminiUiEvent.OpenDocsClicked) },
            MenuItemData(R.drawable.coffee_cup, "Caffeine") { onEvent(GeminiUiEvent.KeepScreenOnToggled) },
            MenuItemData(R.drawable.google_flow_icon, "Google Flow") { onEvent(GeminiUiEvent.OpenFlowClicked) },
            MenuItemData(R.drawable.note_text, "Save To File") { onEvent(GeminiUiEvent.SaveToFileClicked) },
            MenuItemData(R.drawable.baseline_save_alt_24, "Download Video") { onEvent(GeminiUiEvent.ToggleVideoSelectionMode) },
            MenuItemData(R.drawable.baseline_share_24, "Share Page") { onEvent(GeminiUiEvent.SharePageClicked) },
            MenuItemData(R.drawable.baseline_link_24, "Copy Link") { onEvent(GeminiUiEvent.CopyLinkClicked) },
            MenuItemData(R.drawable.baseline_replay_circle_filled_24, "Reload Page") { onEvent(GeminiUiEvent.ReloadPageClicked) },
            MenuItemData(R.drawable.baseline_arrow_circle_right_24, "Go Forward") { onEvent(GeminiUiEvent.GoForwardClicked) },
            MenuItemData(
                painterResId = if (isMenuLeft) R.drawable.outline_arrow_menu_close_24 else R.drawable.outline_arrow_menu_open_24,
                name = "Menu Side"
            ) { onEvent(GeminiUiEvent.MenuPositionChanged(!isMenuLeft)) }
        )
    }
}

@Composable
private fun DialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.padding(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.secondary
        )
    ) {
        Text(text)
    }
}

@Composable
private fun BoxScope.ReloadIndicator(isLoading: Boolean) {
    AnimatedVisibility(
        visible = isLoading,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = Modifier.padding(top = 16.dp),
            shape = CircleShape,
            shadowElevation = 8.dp
        ) {
            CircularProgressIndicator(
                modifier = Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}