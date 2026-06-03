package com.smarterz.app

import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * SmartChromeClient — blocks all popup windows and new window requests.
 *
 * Ads frequently try to open new windows via:
 *   - window.open()
 *   - <a target="_blank">
 *   - JavaScript redirects
 *
 * All of these are blocked here. Only the main WebView frame is allowed.
 */
class SmartChromeClient : WebChromeClient() {

    companion object {
        private const val TAG = "SmartChromeClient"
    }

    /**
     * Block ALL requests to create new windows / popups.
     * Return false = deny the popup, null resultMsg = nothing opens.
     */
    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message?
    ): Boolean {
        Log.d(TAG, "BLOCKED popup window (isDialog=$isDialog, userGesture=$isUserGesture)")
        // Do NOT pass resultMsg anywhere — popup is simply dropped
        return false
    }

    /**
     * Block fullscreen requests from ad scripts.
     * Uncomment the super call if you want fullscreen video support.
     */
    override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
        // Let fullscreen video work (needed for some players)
        super.onShowCustomView(view, callback)
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        // Swallow console messages in release; useful in debug
        Log.v(TAG, "JS Console [${consoleMessage?.messageLevel()}]: ${consoleMessage?.message()}")
        return true
    }
}
