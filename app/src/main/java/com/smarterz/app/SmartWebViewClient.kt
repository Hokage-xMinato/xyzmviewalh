package com.smarterz.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * SmartWebViewClient — the security firewall for the player WebView.
 *
 * ALLOWED domains (ONLY):
 *   - vidsrcme.ru   (the embed host)
 *   - vidsrc.me     (canonical vidsrc)
 *   - vidsrc.net    (vidsrc CDN)
 *   - vidsrc.cc     (vidsrc mirror)
 *   - vidsrc.xyz    (vidsrc mirror)
 *   - vidsrc.icu    (vidsrc mirror)
 *   - *.vidsrc.*    (any vidsrc subdomain/mirror)
 *   - tmdb.org      (poster images loaded by the main WebView — NOT the player)
 *
 * ALL other domains — including ad networks, trackers, popups, redirects — are
 * BLOCKED and return an empty 200 response so the page doesn't error out.
 *
 * External intents (tel:, intent:, market:, etc.) are ALWAYS blocked.
 */
class SmartWebViewClient(private val context: Context) : WebViewClient() {

    companion object {
        private const val TAG = "SmartWebViewClient"

        // Allowed host suffixes — case-insensitive match against host
        private val ALLOWED_HOST_SUFFIXES = listOf(
            "vidsrcme.ru",
            "vidsrc.me",
            "vidsrc.net",
            "vidsrc.cc",
            "vidsrc.xyz",
            "vidsrc.icu",
            "vidsrc.nl",
            "vidsrc.in",
            "vidsrc.pm",
            "vidsrc.to",
            "vidsrc.su",
            "2embed.cc",      // vidsrc sometimes uses this CDN for subtitles
            "opensubtitles.com", // subtitle resource occasionally used
        )

        // Schemes that must NEVER leave the WebView as external intents
        private val BLOCKED_SCHEMES = setOf(
            "intent", "android-app", "market", "tel", "sms", "mailto",
            "whatsapp", "tg", "fb", "twitter", "instagram"
        )

        private val EMPTY_RESPONSE = WebResourceResponse(
            "text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))
        )
    }

    /**
     * Returns true if this URL is from a vidsrc-family domain.
     */
    private fun isAllowed(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: return false
            ALLOWED_HOST_SUFFIXES.any { suffix ->
                host == suffix || host.endsWith(".$suffix")
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Called before each resource (script, image, iframe, XHR, etc.) is loaded.
     * Non-vidsrc resources are blocked silently.
     */
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return EMPTY_RESPONSE
        val scheme = request.url.scheme?.lowercase() ?: ""

        // Block all non-https/http schemes
        if (scheme !in listOf("https", "http", "data", "blob")) {
            Log.d(TAG, "BLOCKED scheme=$scheme url=$url")
            return EMPTY_RESPONSE
        }

        // Allow vidsrc family
        if (isAllowed(url)) {
            return null // null = proceed normally
        }

        // Block everything else (ads, trackers, etc.)
        Log.d(TAG, "BLOCKED non-vidsrc resource: $url")
        return EMPTY_RESPONSE
    }

    /**
     * Called when WebView is about to navigate to a new URL (top-level navigation).
     * Prevents any redirect or navigation away from vidsrc.
     */
    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url?.toString() ?: return true
        val scheme = request.url.scheme?.lowercase() ?: ""

        // ALWAYS block external intent schemes
        if (scheme in BLOCKED_SCHEMES) {
            Log.d(TAG, "BLOCKED external intent: $url")
            return true // block
        }

        // Allow vidsrc navigation
        if (isAllowed(url)) {
            return false // allow WebView to load
        }

        // Block all other top-level navigations (ads, popups, redirects)
        Log.d(TAG, "BLOCKED navigation: $url")
        return true
    }

    /**
     * Extra safety: also intercept old-API URL loading.
     */
    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return true
        val scheme = try { Uri.parse(url).scheme?.lowercase() ?: "" } catch (e: Exception) { "" }
        if (scheme in BLOCKED_SCHEMES) return true
        return !isAllowed(url)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        // Inject JS to disable window.open and anchor target=_blank redirects
        view?.evaluateJavascript(
            """
            (function() {
                // Kill window.open — ads love this
                window.open = function(url, name, specs) {
                    console.log('Smarterz: blocked window.open -> ' + url);
                    return null;
                };
                // Kill all anchor clicks that would open new tabs
                document.addEventListener('click', function(e) {
                    var el = e.target;
                    while (el && el.tagName !== 'A') el = el.parentElement;
                    if (el && el.target && el.target !== '_self') {
                        el.target = '_self';
                    }
                }, true);
            })();
            """.trimIndent(), null
        )
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        Log.e(TAG, "Render process gone, crashed=${detail?.didCrash()}")
        return true // handle gracefully, don't crash the app
    }
}
