package com.icloudandroid.network

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the Apple ID web session (X-APPLE-WEBAUTH-* etc.) alive across process death.
 * iCloud's web endpoints authenticate purely via cookies once accountLogin succeeds,
 * so persisting them here is what lets the app skip sign-in on the next launch.
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "icloud_session_cookies",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val store = mutableMapOf<String, Cookie>()

    init {
        load()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        var changed = false
        for (cookie in cookies) {
            val key = keyFor(cookie)
            if (cookie.expiresAt <= System.currentTimeMillis()) {
                changed = store.remove(key) != null || changed
            } else {
                store[key] = cookie
                changed = true
            }
        }
        if (changed) persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val expired = store.values.filter { it.expiresAt <= now }
        if (expired.isNotEmpty()) {
            expired.forEach { store.remove(keyFor(it)) }
            persist()
        }
        return store.values.filter { it.matches(url) }
    }

    @Synchronized
    fun hasSessionCookies(): Boolean = store.isNotEmpty()

    @Synchronized
    fun clear() {
        store.clear()
        prefs.edit().clear().apply()
    }

    private fun keyFor(cookie: Cookie) = "${cookie.domain}|${cookie.path}|${cookie.name}"

    private fun persist() {
        val array = JSONArray()
        for (cookie in store.values) {
            val obj = JSONObject()
            obj.put("name", cookie.name)
            obj.put("value", cookie.value)
            obj.put("domain", cookie.domain)
            obj.put("path", cookie.path)
            obj.put("expiresAt", cookie.expiresAt)
            obj.put("secure", cookie.secure)
            obj.put("httpOnly", cookie.httpOnly)
            obj.put("hostOnly", cookie.hostOnly)
            array.put(obj)
        }
        prefs.edit().putString(KEY_COOKIES, array.toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString(KEY_COOKIES, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val expiresAt = obj.getLong("expiresAt")
                if (expiresAt <= System.currentTimeMillis()) continue
                val builder = Cookie.Builder()
                    .name(obj.getString("name"))
                    .value(obj.getString("value"))
                    .path(obj.getString("path"))
                    .expiresAt(expiresAt)
                val domain = obj.getString("domain")
                if (obj.optBoolean("hostOnly", true)) {
                    builder.hostOnlyDomain(domain)
                } else {
                    builder.domain(domain)
                }
                if (obj.getBoolean("secure")) builder.secure()
                if (obj.getBoolean("httpOnly")) builder.httpOnly()
                val cookie = builder.build()
                store[keyFor(cookie)] = cookie
            }
        }
    }

    companion object {
        private const val KEY_COOKIES = "cookies_json"
    }
}
