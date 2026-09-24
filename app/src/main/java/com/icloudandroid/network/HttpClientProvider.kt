package com.icloudandroid.network

import android.content.Context
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * A single OkHttpClient (and cookie jar) shared by the auth and photos layers so
 * a session established during sign-in is automatically presented on every
 * later request to *.icloud.com, exactly like a browser tab would.
 */
object HttpClientProvider {

    @Volatile private var client: OkHttpClient? = null
    @Volatile private var jar: PersistentCookieJar? = null

    fun client(context: Context): OkHttpClient =
        client ?: synchronized(this) {
            client ?: buildClient(context.applicationContext).also { client = it }
        }

    fun cookieJar(context: Context): PersistentCookieJar =
        jar ?: synchronized(this) {
            jar ?: PersistentCookieJar(context.applicationContext).also { jar = it }
        }

    private fun buildClient(context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(cookieJar(context))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
}
