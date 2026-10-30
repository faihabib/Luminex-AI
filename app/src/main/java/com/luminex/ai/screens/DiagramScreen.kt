package com.luminex.ai.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.luminex.ai.components.HtmlViewer
import com.luminex.ai.components.generateHtmlWithMermaid

@Composable
fun DiagramScreen(isDiagramVisible: Boolean, clipboardText: String, onBack: () -> Unit, onClose: () -> Unit) {
    val htmlContent = remember(clipboardText) { generateHtmlWithMermaid(clipboardText) }

    GenericPreviewScreen(
        isVisible = isDiagramVisible,
        title = "Close Diagram",
        onBack = onBack,
        onClose = onClose
    ) {
        HtmlViewer(htmlContent = htmlContent)
    }
}