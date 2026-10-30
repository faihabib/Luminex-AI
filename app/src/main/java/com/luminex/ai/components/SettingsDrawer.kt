package com.luminex.ai.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminex.ai.data.AI_SITES

@Composable
fun SettingsDrawer(
    isOpen: Boolean,
    initialTheme: String = "system",
    initialDefaultAi: Int = 0,
    initialAutoLoadLast: Boolean = true,
    initialDesktopMode: Boolean = false,
    initialSplitScreen: Boolean = false,
    initialCustomUa: String = "",
    initialFontSize: Float = 17f,
    initialPreventScreenshots: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (SettingsData) -> Unit = {}
) {
    var theme by remember(isOpen) { mutableStateOf(initialTheme) }
    var defaultAi by remember(isOpen) { mutableStateOf(initialDefaultAi) }
    var autoLoadLast by remember(isOpen) { mutableStateOf(initialAutoLoadLast) }
    var desktopMode by remember(isOpen) { mutableStateOf(initialDesktopMode) }
    var splitScreen by remember(isOpen) { mutableStateOf(initialSplitScreen) }
    var customUa by remember(isOpen) { mutableStateOf(initialCustomUa) }
    var fontSize by remember(isOpen) { mutableStateOf(initialFontSize) }
    var preventScreenshots by remember(isOpen) { mutableStateOf(initialPreventScreenshots) }
    var showDefaultAiMenu by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = isOpen,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                .clickable { onDismiss() }
        )
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.85f)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = "Settings",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // THEME
                SectionHeader("THEME")
                RadioRow("Follow system", theme == "system") { theme = "system" }
                RadioRow("Always dark", theme == "dark") { theme = "dark" }
                RadioRow("Always light", theme == "light") { theme = "light" }

                Spacer(Modifier.height(20.dp))

                // BEHAVIOR
                SectionHeader("BEHAVIOR")
                Text(
                    text = "Default AI on launch",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                )

                Box {
                    Text(
                        text = (AI_SITES.getOrNull(defaultAi)?.name ?: "AI Studio") + "  \u25BE",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showDefaultAiMenu = !showDefaultAiMenu }
                            .padding(12.dp)
                    )
                    DropdownMenu(
                        expanded = showDefaultAiMenu,
                        onDismissRequest = { showDefaultAiMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AI_SITES.forEachIndexed { index, site ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = (if (index == defaultAi) "\u2713  " else "    ") + site.name,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    defaultAi = index
                                    showDefaultAiMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                CheckboxRow("Remember last used AI", autoLoadLast) { autoLoadLast = it }
                CheckboxRow("Desktop mode (all sites)", desktopMode) { desktopMode = it }
                CheckboxRow("Split screen mode", splitScreen) { splitScreen = it }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Custom User-Agent (blank = default)",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                )
                TextField(
                    value = customUa,
                    onValueChange = { customUa = it },
                    placeholder = { Text("Mozilla/5.0...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedIndicatorColor = Color(0xFFD0BCFF),
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                )

                Spacer(Modifier.height(20.dp))

                // APPEARANCE
                SectionHeader("APPEARANCE")
                Text(
                    text = "Picker font size: ${fontSize.toInt()}sp",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 12f..22f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                )

                Spacer(Modifier.height(20.dp))

                // PRIVACY
                SectionHeader("PRIVACY")
                CheckboxRow("Prevent screenshots", preventScreenshots) { preventScreenshots = it }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        onSave(
                            SettingsData(
                                theme = theme,
                                defaultAi = defaultAi,
                                autoLoadLast = autoLoadLast,
                                desktopMode = desktopMode,
                                splitScreen = splitScreen,
                                customUa = customUa,
                                fontSize = fontSize,
                                preventScreenshots = preventScreenshots
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD0BCFF),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Save Settings", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFD0BCFF)
                    )
                ) {
                    Text("Cancel")
                }

                Spacer(Modifier.height(24.dp))

                // === Credit Footer (minimal) ===
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Text(
                    text = "Developed by Faiman Habib",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "with assistance from DeepSeek",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

data class SettingsData(
    val theme: String,
    val defaultAi: Int,
    val autoLoadLast: Boolean,
    val desktopMode: Boolean,
    val splitScreen: Boolean,
    val customUa: String,
    val fontSize: Float,
    val preventScreenshots: Boolean
)

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selected) "\u25C9" else "\u25CB",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 18.sp,
            modifier = Modifier.width(28.dp)
        )
        Text(text = label, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (checked) "\u25C9" else "\u25CB",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 18.sp,
            modifier = Modifier.width(28.dp)
        )
        Text(text = label, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
    }
}
