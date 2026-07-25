package com.orbis.browser.tv

import android.net.Uri

object AdBlocker {
    private val blockedHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "amazon-adsystem.com", "taboola.com",
        "outbrain.com", "popads.net", "propellerads.com", "adskeeper.com",
        "scorecardresearch.com", "googletagmanager.com", "google-analytics.com"
    )

    private val blockedTokens = listOf(
        "/ads/", "/adserver", "/banner", "/popup", "clickunder",
        "tracking", "analytics", "prebid", "vast", "doubleclick"
    )

    fun shouldBlock(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        val host = runCatching { Uri.parse(lower).host.orEmpty() }.getOrDefault("")
        return blockedHosts.any { host == it || host.endsWith(".$it") } ||
            blockedTokens.any { lower.contains(it) }
    }
}
