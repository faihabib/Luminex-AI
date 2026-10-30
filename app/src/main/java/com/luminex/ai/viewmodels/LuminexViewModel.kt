package com.luminex.ai.viewmodels

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luminex.ai.components.getMermaidDiagramPrefixes
import com.luminex.ai.data.UserPreferencesRepository
import com.luminex.ai.utils.services.GoogleServices
import com.luminex.ai.utils.system.ClipboardHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.luminex.ai.utils.system.UpdateChecker
import com.luminex.ai.R
import com.luminex.ai.data.AI_SITES
import kotlinx.coroutines.flow.firstOrNull

@HiltViewModel
class LuminexViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val googleServices: GoogleServices,
    private val clipboardHelper: ClipboardHelper,
    application: Application,
) : AndroidViewModel(application) {
    private val  _uiState = MutableStateFlow(GeminiUiState())
    val uiState: StateFlow<GeminiUiState> = _uiState.asStateFlow()

    private val _sideEffectChannel = Channel<GeminiSideEffect>()
    val sideEffectFlow = _sideEffectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            userPreferencesRepository.isMenuLeftFlow.collect { isLeft ->
                _uiState.update { it.copy(isMenuLeft = isLeft) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.themeFlow.collect { theme ->
                _uiState.update { it.copy(theme = theme) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.defaultAiIndexFlow.collect { idx ->
                _uiState.update { it.copy(defaultAiIndex = idx) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.autoLoadLastFlow.collect { v ->
                _uiState.update { it.copy(autoLoadLast = v) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.desktopModeFlow.collect { v ->
                _uiState.update { it.copy(desktopMode = v) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.splitScreenFlow.collect { v ->
                _uiState.update { it.copy(splitScreen = v) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.secondarySiteIndexFlow.collect { v ->
                _uiState.update { it.copy(secondarySiteIndex = v) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.customUaFlow.collect { v ->
                _uiState.update { it.copy(customUa = v) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.fontSizeFlow.collect { v ->
                _uiState.update { it.copy(fontSize = v) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.preventScreenshotsFlow.collect { v ->
                _uiState.update { it.copy(preventScreenshots = v) }
            }
        }
        // Load default AI or last used AI
        viewModelScope.launch {
            val autoLoadLast = userPreferencesRepository.autoLoadLastFlow.firstOrNull() ?: true
            val defaultAi = userPreferencesRepository.defaultAiIndexFlow.firstOrNull() ?: 0
            val siteIndex = if (autoLoadLast) {
                // For now, default to defaultAi since we don't track last used yet
                defaultAi
            } else {
                defaultAi
            }
            _uiState.update { it.copy(currentSiteIndex = siteIndex) }
        }

        checkForUpdates()

        // === Prewarm all AI sites in the background ===
        // Warms DNS + TLS so the first page load of any site is faster.
        // Runs async — doesn't block app startup.
        viewModelScope.launch {
            try {
                com.luminex.ai.utils.network.SitePrewarmer.prewarmAll()
            } catch (e: Exception) {
                android.util.Log.e("LuminexViewModel", "Prewarm failed", e)
            }
        }
    }

    fun onEvent(event: GeminiUiEvent){
        when(event){
            is GeminiUiEvent.ApplicationReady -> handleApplicationReady()
            is GeminiUiEvent.OpenDocsClicked -> openDocs()
            is GeminiUiEvent.KeepScreenOnToggled -> keepScreenOn()
            is GeminiUiEvent.OpenFlowClicked -> {
                viewModelScope.launch {
                    _sideEffectChannel.send(GeminiSideEffect.LoadUrl("https://labs.google/fx/tools/flow"))
                }
            }
            is GeminiUiEvent.SaveToFileClicked -> saveToFile()
            is GeminiUiEvent.OpenInBrowserClicked -> openInBrowser()
            is GeminiUiEvent.SharePageClicked -> sharePage()
            is GeminiUiEvent.CopyLinkClicked -> copyLink()
            is GeminiUiEvent.ReloadPageClicked -> {
                if (isGeminiIndex(_uiState.value.currentSiteIndex)) {
                    _uiState.update { it.copy(isGeminiHydrating = true) }
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(2000L)
                        _uiState.update { it.copy(isGeminiHydrating = false) }
                    }
                }
                handleReload()
            }
            is GeminiUiEvent.DownloadClicked -> {
                // Download is handled directly in the Screen via evaluateJavascript.
                // No ViewModel action needed.
            }
            is GeminiUiEvent.GoForwardClicked -> goForward()
            is GeminiUiEvent.MenuPositionChanged -> setMenuPosition(event.isLeft)
            is GeminiUiEvent.BackButtonPressed -> handleBackPress()
            is GeminiUiEvent.KeyboardVisibilityChanged -> {
                _uiState.update { it.copy(isKeyboardVisible = event.isVisible) }
            }
            is GeminiUiEvent.WebViewNavigated -> onWebViewNavigated(event.canGoBack, event.url)
            is GeminiUiEvent.LoadingProgressChanged -> {
                _uiState.update {
                    it.copy(loadingProgress = event.progress)
                }
            }
            is GeminiUiEvent.ToggleVideoSelectionMode -> {
                _uiState.update {
                    it.copy(isVideoSelectionMode = !it.isVideoSelectionMode)
                }
            }
            is GeminiUiEvent.DismissUpdateDialog -> {
                _uiState.update { it.copy(updateInfo = null) }
            }
            is GeminiUiEvent.UpdateClicked -> {
                val url = UpdateChecker.getReleaseUrl()
                viewModelScope.launch {
                    _sideEffectChannel.send(
                        GeminiSideEffect.LaunchIntent(
                            Intent(Intent.ACTION_VIEW, url.toUri()),
                        )
                    )
                }
                _uiState.update { it.copy(updateInfo = null) }
            }
            is GeminiUiEvent.SkipUpdateClicked -> {
                val info = _uiState.value.updateInfo
                if (info != null) {
                    viewModelScope.launch {
                        userPreferencesRepository.setSkippedVersion(info.version)
                    }
                }
                _uiState.update { it.copy(updateInfo = null) }
            }
            is GeminiUiEvent.SwitchSite -> {
                val site = AI_SITES.getOrNull(event.index) ?: return
                val isGemini = isGeminiIndex(event.index)
                _uiState.update {
                    it.copy(
                        currentSiteIndex = event.index,
                        showSitePicker = false,
                        activeWebViewUrl = site.url,
                        isGeminiHydrating = isGemini
                    )
                }
                if (isGemini) {
                    // Gemini takes ~1.5-2s to hydrate. Show the veil for
                    // a fixed duration, then hide it — no JS detection needed.
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(2000L)
                        _uiState.update { it.copy(isGeminiHydrating = false) }
                    }
                }
            }
            is GeminiUiEvent.OpenSitePicker -> {
                _uiState.update { it.copy(showSitePicker = true) }
            }
            is GeminiUiEvent.CloseSitePicker -> {
                _uiState.update { it.copy(showSitePicker = false) }
            }
            // === Split Screen (Secondary Pane) ===
            is GeminiUiEvent.OpenSecondarySitePicker -> {
                _uiState.update { it.copy(showSecondarySitePicker = true) }
            }
            is GeminiUiEvent.CloseSecondarySitePicker -> {
                _uiState.update { it.copy(showSecondarySitePicker = false) }
            }
            is GeminiUiEvent.SwitchSecondarySite -> {
                val site = AI_SITES.getOrNull(event.index) ?: return
                _uiState.update {
                    it.copy(
                        secondarySiteIndex = event.index,
                        showSecondarySitePicker = false
                    )
                }
                viewModelScope.launch {
                    userPreferencesRepository.saveSecondarySiteIndex(event.index)
                }
            }
            is GeminiUiEvent.SwapPanes -> {
                val current = _uiState.value.currentSiteIndex
                val secondary = _uiState.value.secondarySiteIndex
                _uiState.update {
                    it.copy(
                        currentSiteIndex = secondary,
                        secondarySiteIndex = current
                    )
                }
                viewModelScope.launch {
                    userPreferencesRepository.saveDefaultAiIndex(secondary)
                    userPreferencesRepository.saveSecondarySiteIndex(current)
                }
            }
            is GeminiUiEvent.ToggleSplitScreen -> {
                val newValue = !_uiState.value.splitScreen
                _uiState.update { it.copy(splitScreen = newValue) }
                viewModelScope.launch {
                    userPreferencesRepository.saveSplitScreen(newValue)
                }
            }
            is GeminiUiEvent.UpdateSplitRatio -> {
                // Clamp between 0.2 and 0.8 so neither pane disappears
                val clamped = event.ratio.coerceIn(0.2f, 0.8f)
                _uiState.update { it.copy(splitRatio = clamped) }
            }
            is GeminiUiEvent.ClearCurrentSiteData -> {
                val site = AI_SITES.getOrNull(_uiState.value.currentSiteIndex)
                if (site != null) {
                    val origin = try {
                        java.net.URI(site.url).let { "${it.scheme}://${it.host}" }
                    } catch (e: Exception) {
                        site.url
                    }

                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try {
                            // Clear cookies for this origin only
                            val cookieManager = android.webkit.CookieManager.getInstance()
                            cookieManager.removeAllCookies(null)
                            cookieManager.flush()

                            // Clear WebStorage (localStorage, IndexedDB) for this origin
                            android.webkit.WebStorage.getInstance().deleteOrigin(origin)

                            // Clear HTTP cache (shared across sites, but only way to reset)
                            val ctx = getApplication<android.app.Application>()
                            android.webkit.WebView(ctx).clearCache(true)
                        } catch (e: Exception) {
                            android.util.Log.e("LuminexViewModel", "Clear data failed", e)
                        }
                    }

                    // Restart the current site — the WebView reloads with fresh state
                    val newIndex = _uiState.value.currentSiteIndex
                    _uiState.update {
                        it.copy(
                            currentSiteIndex = -1  // trick to force reload
                        )
                    }
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(150)
                        _uiState.update { it.copy(currentSiteIndex = newIndex) }
                    }
                }
            }
            is GeminiUiEvent.GeminiHydrationChanged -> {
                _uiState.update { it.copy(isGeminiHydrating = event.hydrating) }
            }
            is GeminiUiEvent.ToggleFullScreen -> {
                _uiState.update { it.copy(fullScreen = !it.fullScreen) }
            }
            is GeminiUiEvent.ExitFullScreen -> {
                _uiState.update { it.copy(fullScreen = false) }
            }
            is GeminiUiEvent.SaveSettings -> {
                viewModelScope.launch {
                    userPreferencesRepository.saveTheme(event.theme)
                    userPreferencesRepository.saveDefaultAiIndex(event.defaultAiIndex)
                    userPreferencesRepository.saveAutoLoadLast(event.autoLoadLast)
                    userPreferencesRepository.saveDesktopMode(event.desktopMode)
                    userPreferencesRepository.saveSplitScreen(event.splitScreen)
                    userPreferencesRepository.saveCustomUa(event.customUa)
                    userPreferencesRepository.saveFontSize(event.fontSize)
                    userPreferencesRepository.savePreventScreenshots(event.preventScreenshots)
                }
            }
        }
    }


    private fun isGeminiIndex(index: Int): Boolean {
        return AI_SITES.getOrNull(index)?.name == "Gemini"
    }

    fun getCurrentSiteUrl(): String {
        return AI_SITES.getOrNull(_uiState.value.currentSiteIndex)?.url
            ?: AI_SITES[0].url
    }

    fun onClipboardTextChanged(text: String) {
        val type = when {
            getMermaidDiagramPrefixes().any { text.startsWith(it) } -> ClipboardContentType.DIAGRAM
            text.lowercase().startsWith("<!doctype html") -> ClipboardContentType.HTML
            else -> ClipboardContentType.NONE
        }
        _uiState.update { it.copy(clipboardContentType = type) }

        if (type != ClipboardContentType.NONE) {
            viewModelScope.launch {
                delay(6500)
                if (_uiState.value.clipboardContentType == type) {
                    _uiState.update { it.copy(clipboardContentType = ClipboardContentType.NONE) }
                }
            }
        }
    }

    private fun checkForUpdates() {
        viewModelScope.launch {
            val lastCheckTime = userPreferencesRepository.lastUpdateCheckTimeFlow.firstOrNull() ?: 0L
            val currentTime = System.currentTimeMillis()
            val twentyFourHoursInMillis = 24 * 60 * 60 * 1000L

            if (currentTime - lastCheckTime < twentyFourHoursInMillis) {
                return@launch
            }

            val updateInfo = UpdateChecker.checkForNewVersion()
            val skippedVersion = userPreferencesRepository.skippedVersionFlow.firstOrNull()

            userPreferencesRepository.saveLastUpdateCheckTime(currentTime)

            if ((updateInfo != null) && (updateInfo.version != skippedVersion)) {
                _uiState.update { it.copy(updateInfo = updateInfo) }
            }
        }
    }

    private fun onWebViewNavigated(canGoBack: Boolean, url: String?) {
        _uiState.update { it.copy(canWebViewGoBack = canGoBack, activeWebViewUrl = url, isReloading = false) }
    }

    private fun handleReload() {
        if(_uiState.value.isReloading) return

        _uiState.update { it.copy(isReloading = true) }
        viewModelScope.launch {
            _sideEffectChannel.send(GeminiSideEffect.WebViewReload)
        }
    }

    private fun handleBackPress() {
        viewModelScope.launch {
            _sideEffectChannel.send(GeminiSideEffect.WebViewGoBack)
        }
    }


    private fun handleApplicationReady() {
        if (_uiState.value.isApplicationReady) return

        _uiState.update { it.copy(isApplicationReady = true) }
    }

    private fun setMenuPosition(isLeft: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveMenuPosition(isLeft)
        }
    }

    private fun openDocs()
    {
        googleServices.openGoogleDocs(getApplication())
    }

    private fun keepScreenOn()
    {
        _uiState.update { it.copy(isKeepScreenOn = !it.isKeepScreenOn) }

        viewModelScope.launch {
            if(uiState.value.isKeepScreenOn)
                _sideEffectChannel.send(GeminiSideEffect.ShowToast("Caffeine mode was turned on"))
            else
                _sideEffectChannel.send(GeminiSideEffect.ShowToast("Caffeine mode was turned off"))
        }
    }

    private fun saveToFile()
    {
        viewModelScope.launch {
            _sideEffectChannel.send(GeminiSideEffect.LaunchSaveToFile)
        }
    }

    private fun openInBrowser() {
        _uiState.value.activeWebViewUrl?.let { url ->
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            viewModelScope.launch {
                _sideEffectChannel.send(GeminiSideEffect.LaunchIntent(intent))
            }
        }
    }

    private fun sharePage() {
        _uiState.value.activeWebViewUrl?.let { url ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, url)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(intent, "Share URL")
            viewModelScope.launch {
                _sideEffectChannel.send(GeminiSideEffect.LaunchIntent(shareIntent))
            }
        }
    }

    private fun copyLink() {
        val url = _uiState.value.activeWebViewUrl
        if (url != null) {
            clipboardHelper.copy(url, "Copied URL")
            viewModelScope.launch {
                _sideEffectChannel.send(GeminiSideEffect.ShowToast(getApplication<Application>().getString(R.string.link_copied)))
            }
        } else {
            viewModelScope.launch {
                _sideEffectChannel.send(GeminiSideEffect.ShowToast(getApplication<Application>().getString(R.string.no_url_to_copy)))
            }
        }
    }

    private fun goForward()
    {
        viewModelScope.launch {
            _sideEffectChannel.send(GeminiSideEffect.WebViewGoForward)
        }
    }
}