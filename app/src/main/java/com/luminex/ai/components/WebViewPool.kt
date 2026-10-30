package com.luminex.ai.components

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import java.util.LinkedHashMap

/**
 * Browser-style tab pool for one pane.
 *
 * ALL pooled WebViews stay ATTACHED to the host FrameLayout. Only the
 * active one is VISIBLE; the rest are GONE. This avoids the "blank screen
 * after detach/reattach" bug that occurs when WebViews are removed from
 * the view hierarchy and put back — Android sometimes drops the render
 * surface on re-attach, showing black instead of the page.
 *
 * LRU eviction: when the pool exceeds [maxSize], the least-recently-used
 * NON-ACTIVE WebView is destroyed. The active one is never evicted.
 *
 * NOT thread-safe. Touch from the main thread only.
 */
class WebViewPool(
    private val tag: String,
    private val maxSize: Int = 5
) {
    /** The FrameLayout all pooled WebViews are attached to. Set once. */
    private var host: FrameLayout? = null

    /** The currently visible WebView. */
    private var active: WebView? = null

    // Ordered map for LRU. Access-order.
    private val pool = object : LinkedHashMap<String, WebView>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, WebView>): Boolean {
            // Never auto-remove via this hook — we manage eviction manually
            // in [evictIfNeeded] so we can skip the active WebView.
            return false
        }
    }

    /** Sets the host FrameLayout. Called once from the composable. */
    fun setHost(frameLayout: FrameLayout) {
        host = frameLayout
    }

    /** Returns the pooled WebView for [siteUrl], or null if not yet created. */
    fun getOrNull(siteUrl: String): WebView? = pool[siteUrl]

    /**
     * Creates a new WebView slot for [siteUrl]. The WebView is attached to
     * the host FrameLayout but starts GONE. Evicts the LRU non-active tab
     * if the pool is over capacity.
     */
    fun add(siteUrl: String, webView: WebView) {
        Log.d("WebViewPool[$tag]", "Adding tab: $siteUrl")

        // Ensure layout params before attaching
        if (webView.layoutParams == null) {
            webView.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        webView.visibility = View.GONE

        pool[siteUrl] = webView
        webView.tag = siteUrl
        host?.addView(webView)

        evictIfNeeded()
    }

    /** Makes [siteUrl]'s WebView the visible one. Returns it, or null if missing. */
    fun setActive(siteUrl: String): WebView? {
        val target = pool[siteUrl] ?: return null

        // Hide everything else
        pool.values.forEach { wv ->
            if (wv !== target && wv.visibility != View.GONE) {
                wv.visibility = View.GONE
            }
        }

        // Show the target
        target.visibility = View.VISIBLE
        target.requestLayout()
        target.invalidate()

        active = target
        Log.d("WebViewPool[$tag]", "Activated: $siteUrl (pool size=${pool.size})")
        return target
    }

    /** Current active WebView, if any. */
    fun getActive(): WebView? = active

    /** Pauses all pooled WebViews (JS, timers). Called on app background. */
    fun pauseAll() {
        pool.values.forEach { wv ->
            try { wv.onPause() } catch (_: Exception) {} // Removed pauseTimers() to allow background generation
        }
        Log.d("WebViewPool[$tag]", "Paused ${pool.size} tabs")
    }

    /** Resumes all pooled WebViews. Called on app foreground. */
    fun resumeAll() {
        pool.values.forEach { wv ->
            try { wv.onResume(); wv.resumeTimers() } catch (_: Exception) {}
        }
        Log.d("WebViewPool[$tag]", "Resumed ${pool.size} tabs")
    }

    /** Destroys everything. Called on composable dispose. */
    fun destroyAll() {
        pool.values.forEach { wv ->
            try {
                host?.removeView(wv)
                wv.stopLoading()
                wv.destroy()
            } catch (_: Exception) {}
        }
        pool.clear()
        active = null
        Log.d("WebViewPool[$tag]", "Destroyed all tabs")
    }

    // --- Internals ---------------------------------------------------

    /**
     * If pool size exceeds maxSize, destroy the oldest NON-ACTIVE WebView.
     * Never evicts the currently visible tab.
     */
    private fun evictIfNeeded() {
        while (pool.size > maxSize) {
            // LinkedHashMap iterator goes from eldest → newest.
            val iterator = pool.entries.iterator()
            var evicted = false
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value === active) continue  // don't touch active
                val siteUrl = entry.key
                val wv = entry.value
                Log.d("WebViewPool[$tag]", "Evicting LRU tab: $siteUrl")
                try {
                    host?.removeView(wv)
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.destroy()
                } catch (_: Exception) {}
                iterator.remove()
                evicted = true
                break
            }
            // If we couldn't evict anything (only active remained), stop.
            if (!evicted) break
        }
    }

    /** For debugging. */
    fun debugState(): String =
        "size=${pool.size} active=${active?.tag} keys=${pool.keys.toList()}"
}
