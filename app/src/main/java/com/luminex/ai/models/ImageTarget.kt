package com.luminex.ai.models

/**
 * Represents an image that the user long-pressed in the WebView.
 *
 * At least one of [src], [bgImage], or [dataUrl] will be non-blank.
 */
data class ImageTarget(
    val tag: String,        // "img", "canvas", "svg", or "css-bg"
    val src: String,        // http URL, blob URL, or data: URL (may be blank)
    val bgImage: String,    // CSS background image URL (may be blank)
    val dataUrl: String     // base64 data URL (for canvas/svg, may be blank)
) {
    val isDataUrl: Boolean get() = src.startsWith("data:") || dataUrl.startsWith("data:")
    val isBlobUrl: Boolean get() = src.startsWith("blob:") || bgImage.startsWith("blob:")
    val isHttpUrl: Boolean get() = src.startsWith("http") || bgImage.startsWith("http")

    /** The best URL to fetch or open. */
    fun bestUrl(): String = when {
        dataUrl.isNotBlank() -> dataUrl
        src.isNotBlank() -> src
        bgImage.isNotBlank() -> bgImage
        else -> ""
    }
}
