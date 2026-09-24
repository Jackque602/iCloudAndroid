package com.icloudandroid.auth

import android.content.Context
import com.icloudandroid.network.HttpClientProvider
import com.icloudandroid.network.WebViewCookieSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the Apple ID sign-in state machine used by the login screen and
 * remembers a completed session so the app can reopen straight into the
 * gallery next time.
 */
class AuthRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = SessionStore(appContext)
    private val api = AppleAuthApi(HttpClientProvider.client(appContext), store)
    private val cookieJar = HttpClientProvider.cookieJar(appContext)

    private val _state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val savedAppleId: String?
        get() = store.appleId

    /** Call on app start: resumes a previous session without prompting for credentials. */
    suspend fun restoreSession() {
        val hasTrustedContext = cookieJar.hasSessionCookies() && store.sessionToken != null
        if (!hasTrustedContext) {
            _state.value = AuthState.AwaitingCredentials
            return
        }
        _state.value = AuthState.CheckingSavedSession
        when (val result = api.accountLogin()) {
            is AppleAuthApi.AccountLoginResult.Success ->
                _state.value = AuthState.SignedIn(result.photosServiceUrl)
            is AppleAuthApi.AccountLoginResult.Error -> {
                // Saved session is stale; fall back to a fresh sign-in.
                store.clear()
                _state.value = AuthState.AwaitingCredentials
            }
        }
    }

    suspend fun signIn(appleId: String, password: String) {
        _state.value = AuthState.SigningIn
        store.appleId = appleId
        when (val result = api.signIn(appleId, password)) {
            AppleAuthApi.SignInResult.RequiresTwoFactor ->
                _state.value = AuthState.AwaitingTwoFactorCode

            AppleAuthApi.SignInResult.Authenticated ->
                finishLogin()

            is AppleAuthApi.SignInResult.InvalidCredentials ->
                _state.value = AuthState.Failed(
                    result.detail ?: "Incorrect Apple ID or password.",
                    AuthState.AwaitingCredentials
                )

            is AppleAuthApi.SignInResult.Error ->
                _state.value = AuthState.Failed(result.message, AuthState.AwaitingCredentials)
        }
    }

    suspend fun submitTwoFactorCode(code: String) {
        _state.value = AuthState.VerifyingTwoFactorCode
        when (val result = api.verifyTwoFactorCode(code)) {
            AppleAuthApi.TwoFactorResult.Verified -> {
                api.trustSession()
                finishLogin()
            }

            is AppleAuthApi.TwoFactorResult.InvalidCode ->
                _state.value = AuthState.Failed(
                    result.detail ?: "That code didn't match. Please try again.",
                    AuthState.AwaitingTwoFactorCode
                )

            is AppleAuthApi.TwoFactorResult.Error ->
                _state.value = AuthState.Failed(result.message, AuthState.AwaitingTwoFactorCode)
        }
    }

    private suspend fun finishLogin() {
        when (val result = api.accountLogin()) {
            is AppleAuthApi.AccountLoginResult.Success ->
                _state.value = AuthState.SignedIn(result.photosServiceUrl)
            is AppleAuthApi.AccountLoginResult.Error ->
                _state.value = AuthState.Failed(result.message, AuthState.AwaitingCredentials)
        }
    }

    fun currentPhotosServiceUrl(): String? = store.photosServiceUrl

    fun signOut() {
        store.clearAll()
        cookieJar.clear()
        WebViewCookieSync.clear()
        _state.value = AuthState.AwaitingCredentials
    }
}
