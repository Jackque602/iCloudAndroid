package com.icloudandroid.auth

/** Current state of the Apple ID sign-in flow, surfaced to the UI layer. */
sealed interface AuthState {
    data object SignedOut : AuthState
    data object CheckingSavedSession : AuthState
    data object AwaitingCredentials : AuthState
    data object SigningIn : AuthState
    data object AwaitingTwoFactorCode : AuthState
    data object VerifyingTwoFactorCode : AuthState
    data class SignedIn(val photosServiceUrl: String) : AuthState
    data class Failed(val message: String, val retryState: AuthState) : AuthState
}

class AuthException(message: String) : Exception(message)
