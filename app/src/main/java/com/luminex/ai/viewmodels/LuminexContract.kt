package com.luminex.ai.viewmodels

import android.content.Intent
import com.luminex.ai.utils.system.UpdateInfo

data class GeminiUiState(
    val isApplicationReady: Boolean = false,
    val isKeepScreenOn: Boolean = false,
    val isMenuLeft: Boolean = false,
    val isKeyboardVisible: Boolean = false,
    val clipboardContentType: ClipboardContentType = ClipboardContentType.NONE,
    val activeWebViewUrl: String? = null,
    val canWebViewGoBack: Boolean = false,
    val isReloading: Boolean = false,
    val loadingProgress: Int = 0,
    val isVideoSelectionMode: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val currentSiteIndex: Int = 0,
    val showSitePicker: Boolean = false,

    // === Settings ===
    val theme: String = "system",
    val defaultAiIndex: Int = 0,
    val autoLoadLast: Boolean = true,
    val desktopMode: Boolean = false,
    val splitScreen: Boolean = false,
    val customUa: String = "",
    val fontSize: Int = 17,
    val preventScreenshots: Boolean = false,
    // === Split Screen (Secondary Pane) ===
    val secondarySiteIndex: Int = 0,
    val showSecondarySitePicker: Boolean = false,
    val fullScreen: Boolean = false,
    val splitRatio: Float = 0.5f,
    val isGeminiHydrating: Boolean = false
)

enum class ClipboardContentType { NONE, DIAGRAM, HTML }

sealed interface GeminiUiEvent {
    object ApplicationReady : GeminiUiEvent
    object OpenDocsClicked : GeminiUiEvent
    object KeepScreenOnToggled : GeminiUiEvent
    object OpenFlowClicked : GeminiUiEvent
    object SaveToFileClicked : GeminiUiEvent
    object OpenInBrowserClicked : GeminiUiEvent
    object SharePageClicked : GeminiUiEvent
    object CopyLinkClicked : GeminiUiEvent
    object ReloadPageClicked : GeminiUiEvent
    object DownloadClicked : GeminiUiEvent
    object GoForwardClicked : GeminiUiEvent
    data class MenuPositionChanged(val isLeft: Boolean) : GeminiUiEvent
    object BackButtonPressed : GeminiUiEvent
    data class KeyboardVisibilityChanged(val isVisible: Boolean) : GeminiUiEvent
    data class WebViewNavigated(val canGoBack: Boolean, val url: String?) : GeminiUiEvent
    data class LoadingProgressChanged(val progress: Int) : GeminiUiEvent
    object ToggleVideoSelectionMode : GeminiUiEvent
    object DismissUpdateDialog : GeminiUiEvent
    object UpdateClicked : GeminiUiEvent
    object SkipUpdateClicked : GeminiUiEvent
    data class SwitchSite(val index: Int) : GeminiUiEvent
    object OpenSitePicker : GeminiUiEvent
    object CloseSitePicker : GeminiUiEvent
    // === Split Screen (Secondary Pane) ===
    object OpenSecondarySitePicker : GeminiUiEvent
    object CloseSecondarySitePicker : GeminiUiEvent
    data class SwitchSecondarySite(val index: Int) : GeminiUiEvent
    object SwapPanes : GeminiUiEvent
    data class UpdateSplitRatio(val ratio: Float) : GeminiUiEvent
    object ToggleSplitScreen : GeminiUiEvent
    object ToggleFullScreen : GeminiUiEvent
    data class GeminiHydrationChanged(val hydrating: Boolean) : GeminiUiEvent
    object ExitFullScreen : GeminiUiEvent
    object ClearCurrentSiteData : GeminiUiEvent
    data class SaveSettings(
        val theme: String,
        val defaultAiIndex: Int,
        val autoLoadLast: Boolean,
        val desktopMode: Boolean,
        val splitScreen: Boolean,
        val customUa: String,
        val fontSize: Int,
        val preventScreenshots: Boolean
    ) : GeminiUiEvent
}

sealed interface GeminiSideEffect {
    data class LaunchIntent(val intent: Intent) : GeminiSideEffect
    data class ShowToast(val message: String) : GeminiSideEffect
    data class LoadUrl(val url: String) : GeminiSideEffect
    object LaunchSaveToFile : GeminiSideEffect
    object WebViewGoBack : GeminiSideEffect
    object WebViewReload : GeminiSideEffect
    object WebViewGoForward : GeminiSideEffect
}