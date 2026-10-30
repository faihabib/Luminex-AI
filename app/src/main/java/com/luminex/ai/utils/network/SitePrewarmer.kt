package com.luminex.ai.utils.network

import android.util.Log
import com.luminex.ai.data.AI_SITES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Warms DNS + TLS for all AI sites in the background.
 *
 * Does a lightweight HTTP HEAD request to each URL. The OS caches the
 * DNS resolution and the connection is dropped after — but the SSL
 * session and DNS entries persist, so the next real page load is faster.
 *
 * This uses no extra memory beyond the requests in flight, and no
 * additional dependency (uses HttpURLConnection).
 */
object SitePrewarmer {

    private const val TAG = "SitePrewarmer"
    private const val TIMEOUT_MS = 5000

    /**
     * Fire-and-forget: warms up all sites in AI_SITES.
     * Safe to call multiple times — the OS will just dedupe the DNS lookups.
     */
    suspend fun prewarmAll() = withContext(Dispatchers.IO) {
        AI_SITES.forEach { site ->
            try {
                val url = URL(site.url)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Mobile Safari/537.36")
                }
                val code = connection.responseCode
                Log.d(TAG, "Prewarmed ${site.name}: HTTP $code")
                connection.disconnect()
            } catch (e: Exception) {
                Log.d(TAG, "Prewarm failed for ${site.name}: ${e.message}")
                // Don't fail on individual errors — best-effort
            }
        }
    }
}
