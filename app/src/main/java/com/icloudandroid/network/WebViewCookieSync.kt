package com.icloudandroid.network

import android.webkit.CookieManager

/**
 * Copies our own session cookies into Android's shared WebView cookie store so an
 * embedded WebView pointed at icloud.com is already signed in, without the user
 * re-entering their Apple ID inside it.
 */
object WebViewCookieSync {

    fun sync(cookieJar: PersistentCookieJar) {
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        for (cookie in cookieJar.allCookies()) {
            val host = cookie.domain.removePrefix(".")
            val url = "https://$host${cookie.path}"
            val header = buildString {
                append(cookie.name).append('=').append(cookie.value)
                append("; Domain=").append(cookie.domain)
                append("; Path=").append(cookie.path)
                if (cookie.secure) append("; Secure")
            }
            manager.setCookie(url, header)
        }
        manager.flush()
    }

    fun clear() {
        val manager = CookieManager.getInstance()
        manager.removeAllCookies(null)
        manager.flush()
    }
}
