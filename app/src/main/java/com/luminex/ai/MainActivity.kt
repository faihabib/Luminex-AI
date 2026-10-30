package com.luminex.ai

import android.os.Bundle
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.luminex.ai.viewmodels.LuminexViewModel
import com.luminex.ai.ui.theme.LuminexTheme
import com.luminex.ai.screens.LuminexScreen
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val geminiViewModel: LuminexViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().apply {
            // Dismiss the system splash immediately — our custom Compose
            // SplashScreen composable handles the launch animation instead.
            setKeepOnScreenCondition { false }
            setOnExitAnimationListener { screen ->
                screen.remove()
            }
        }

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val uiState by geminiViewModel.uiState.collectAsState()

            // Apply FLAG_SECURE for prevent-screenshots
            LaunchedEffect(uiState.preventScreenshots) {
                if (uiState.preventScreenshots) {
                    window.setFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                    )
                }
            }

            val darkTheme = when (uiState.theme) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            // === Custom Splash Screen ===
            // Show our custom splash for 2 seconds, then fade out over 400ms.
            var showSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(3200L)
                showSplash = false
            }

            LuminexTheme(darkTheme = darkTheme) {
                androidx.compose.foundation.layout.Box {
                    LuminexApp(geminiViewModel)

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showSplash,
                        enter = androidx.compose.animation.EnterTransition.None,
                        exit = androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(400)
                        )
                    ) {
                        com.luminex.ai.components.SplashScreen()
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        startService(Intent(this, LuminexBackgroundService::class.java))
    }

    override fun onResume() {
        super.onResume()
        stopService(Intent(this, LuminexBackgroundService::class.java))
    }
}

@Composable
private fun LuminexApp(viewModel: LuminexViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val window = (LocalActivity.current)?.window
    LaunchedEffect(uiState.isKeepScreenOn) {
        if (window != null) {
            if (uiState.isKeepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    LuminexScreen()
}