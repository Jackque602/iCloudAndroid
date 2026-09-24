package com.icloudandroid.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Holds the small set of non-cookie values the Apple ID auth dance needs to
 * remember: the session/trust tokens returned as response headers, the scnt
 * and session-id values that must be echoed back on the 2FA follow-up calls,
 * and the photos service URL resolved once after accountLogin succeeds.
 */
class SessionStore(context: Context) {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "icloud_session_state",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var clientId: String
        get() = prefs.getString(KEY_CLIENT_ID, null) ?: newClientId().also { clientId = it }
        set(value) = prefs.edit().putString(KEY_CLIENT_ID, value).apply()

    var appleId: String?
        get() = prefs.getString(KEY_APPLE_ID, null)
        set(value) = prefs.edit().putString(KEY_APPLE_ID, value).apply()

    var sessionToken: String?
        get() = prefs.getString(KEY_SESSION_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_SESSION_TOKEN, value).apply()

    var trustToken: String?
        get() = prefs.getString(KEY_TRUST_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TRUST_TOKEN, value).apply()

    var scnt: String?
        get() = prefs.getString(KEY_SCNT, null)
        set(value) = prefs.edit().putString(KEY_SCNT, value).apply()

    var appleIdSessionId: String?
        get() = prefs.getString(KEY_SESSION_ID, null)
        set(value) = prefs.edit().putString(KEY_SESSION_ID, value).apply()

    var accountCountry: String?
        get() = prefs.getString(KEY_ACCOUNT_COUNTRY, null)
        set(value) = prefs.edit().putString(KEY_ACCOUNT_COUNTRY, value).apply()

    var photosServiceUrl: String?
        get() = prefs.getString(KEY_PHOTOS_URL, null)
        set(value) = prefs.edit().putString(KEY_PHOTOS_URL, value).apply()

    fun clear() {
        prefs.edit()
            .remove(KEY_SESSION_TOKEN)
            .remove(KEY_SCNT)
            .remove(KEY_SESSION_ID)
            .remove(KEY_ACCOUNT_COUNTRY)
            .remove(KEY_PHOTOS_URL)
            // trustToken and appleId are kept so a returning user can skip 2FA.
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun newClientId(): String = "auth-" + java.util.UUID.randomUUID().toString().lowercase()

    private companion object {
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_APPLE_ID = "apple_id"
        const val KEY_SESSION_TOKEN = "session_token"
        const val KEY_TRUST_TOKEN = "trust_token"
        const val KEY_SCNT = "scnt"
        const val KEY_SESSION_ID = "apple_id_session_id"
        const val KEY_ACCOUNT_COUNTRY = "account_country"
        const val KEY_PHOTOS_URL = "photos_service_url"
    }
}
