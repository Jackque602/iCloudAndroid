package com.icloudandroid.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

/**
 * Raw calls against Apple's undocumented (but widely reverse-engineered, e.g. by
 * the open-source pyicloud/icloudpd projects) web sign-in API. There is no public
 * iCloud Photos API for third-party apps; this replicates exactly what
 * https://www.icloud.com's own JavaScript does when you sign in there, using the
 * same first-party OAuth client id icloud.com's web app uses.
 *
 * This is inherently fragile: Apple can change these endpoints or payloads at any
 * time without notice. Every response is defensively parsed.
 */
class AppleAuthApi(private val http: OkHttpClient, private val store: SessionStore) {

    private val jsonMedia = "application/json".toMediaType()

    private fun commonAuthHeaders(builder: Request.Builder) {
        builder
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Origin", HOME_ENDPOINT)
            .header("Referer", "$HOME_ENDPOINT/")
            .header("X-Apple-OAuth-Client-Id", OAUTH_CLIENT_ID)
            .header("X-Apple-OAuth-Client-Type", "firstPartyAuth")
            .header("X-Apple-OAuth-Response-Type", "code")
            .header("X-Apple-OAuth-Response-Mode", "web_message")
            .header("X-Apple-OAuth-State", store.clientId)
            .header("X-Apple-Widget-Key", OAUTH_CLIENT_ID)
    }

    private fun twoFactorHeaders(builder: Request.Builder) {
        store.scnt?.let { builder.header("scnt", it) }
        store.appleIdSessionId?.let { builder.header("X-Apple-ID-Session-Id", it) }
    }

    /** Result of POSTing credentials to idmsa. */
    sealed interface SignInResult {
        data object RequiresTwoFactor : SignInResult
        data object Authenticated : SignInResult
        data class InvalidCredentials(val detail: String?) : SignInResult
        data class Error(val message: String) : SignInResult
    }

    suspend fun signIn(appleId: String, password: String): SignInResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("accountName", appleId)
            put("password", password)
            put("rememberMe", true)
            put("trustTokens", store.trustToken?.let { listOf(it) } ?: emptyList<String>())
        }

        val requestBuilder = Request.Builder()
            .url("$AUTH_ENDPOINT/signin?isRememberMeEnabled=true")
            .post(body.toString().toRequestBody(jsonMedia))
        commonAuthHeaders(requestBuilder)

        runCatching { http.newCall(requestBuilder.build()).execute() }
            .fold(
                onSuccess = { response ->
                    response.use { resp ->
                        captureCommonHeaders(resp)
                        when (resp.code) {
                            200 -> SignInResult.Authenticated
                            409 -> SignInResult.RequiresTwoFactor
                            401, 403 -> SignInResult.InvalidCredentials(readErrorMessage(resp))
                            else -> SignInResult.Error("Sign-in failed (HTTP ${resp.code}).")
                        }
                    }
                },
                onFailure = { SignInResult.Error(it.message ?: "Network error while signing in.") }
            )
    }

    sealed interface TwoFactorResult {
        data object Verified : TwoFactorResult
        data class InvalidCode(val detail: String?) : TwoFactorResult
        data class Error(val message: String) : TwoFactorResult
    }

    suspend fun verifyTwoFactorCode(code: String): TwoFactorResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("securityCode", JSONObject().put("code", code))
        }
        val requestBuilder = Request.Builder()
            .url("$AUTH_ENDPOINT/verify/trusteddevice/securitycode")
            .post(body.toString().toRequestBody(jsonMedia))
        commonAuthHeaders(requestBuilder)
        twoFactorHeaders(requestBuilder)

        runCatching { http.newCall(requestBuilder.build()).execute() }
            .fold(
                onSuccess = { response ->
                    response.use { resp ->
                        captureCommonHeaders(resp)
                        when (resp.code) {
                            200, 204 -> TwoFactorResult.Verified
                            400, 401 -> TwoFactorResult.InvalidCode(readErrorMessage(resp))
                            else -> TwoFactorResult.Error("Verification failed (HTTP ${resp.code}).")
                        }
                    }
                },
                onFailure = { TwoFactorResult.Error(it.message ?: "Network error while verifying code.") }
            )
    }

    /** Marks this device as trusted so future sign-ins can skip 2FA. Best-effort. */
    suspend fun trustSession() = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url("$AUTH_ENDPOINT/2sv/trust")
            .get()
        commonAuthHeaders(requestBuilder)
        twoFactorHeaders(requestBuilder)

        runCatching { http.newCall(requestBuilder.build()).execute() }
            .onSuccess { response -> response.use { captureCommonHeaders(it) } }
    }

    sealed interface AccountLoginResult {
        data class Success(val photosServiceUrl: String) : AccountLoginResult
        data class Error(val message: String) : AccountLoginResult
    }

    suspend fun accountLogin(): AccountLoginResult = withContext(Dispatchers.IO) {
        val sessionToken = store.sessionToken
            ?: return@withContext AccountLoginResult.Error("Missing session token; please sign in again.")

        val body = JSONObject().apply {
            put("accountCountryCode", store.accountCountry ?: "")
            put("dsWebAuthToken", sessionToken)
            put("extended_login", true)
            put("trustToken", store.trustToken ?: "")
        }
        val request = Request.Builder()
            .url("$SETUP_ENDPOINT/accountLogin")
            .post(body.toString().toRequestBody(jsonMedia))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Origin", HOME_ENDPOINT)
            .header("Referer", "$HOME_ENDPOINT/")
            .build()

        runCatching { http.newCall(request).execute() }
            .fold(
                onSuccess = { response ->
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            return@withContext AccountLoginResult.Error(
                                "Could not finish signing in (HTTP ${resp.code})."
                            )
                        }
                        val json = runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrNull()
                            ?: return@withContext AccountLoginResult.Error("Unexpected response from iCloud.")
                        val photosUrl = json.optJSONObject("webservices")
                            ?.optJSONObject("ckdatabasews")
                            ?.optString("url")
                            ?.takeIf { it.isNotBlank() }
                            ?: return@withContext AccountLoginResult.Error(
                                "iCloud did not return a Photos service URL for this account."
                            )
                        store.photosServiceUrl = photosUrl
                        AccountLoginResult.Success(photosUrl)
                    }
                },
                onFailure = { AccountLoginResult.Error(it.message ?: "Network error finishing sign-in.") }
            )
    }

    private fun captureCommonHeaders(response: Response) {
        response.header("X-Apple-Session-Token")?.let { store.sessionToken = it }
        response.header("X-Apple-TwoSV-Trust-Token")?.let { store.trustToken = it }
        response.header("scnt")?.let { store.scnt = it }
        response.header("X-Apple-ID-Session-Id")?.let { store.appleIdSessionId = it }
        response.header("X-Apple-ID-Account-Country")?.let { store.accountCountry = it }
    }

    private fun readErrorMessage(response: Response): String? = runCatching {
        val text = response.peekBody(4096).string()
        if (text.isBlank()) return@runCatching null
        JSONObject(text).optString("errorMessage").takeIf { it.isNotBlank() }
    }.getOrNull()

    companion object {
        private const val OAUTH_CLIENT_ID = "d39ba9916b7251055b22c7f910e2ea796ee65e98b2ddecea8f5dde8d9d1a815d"
        const val HOME_ENDPOINT = "https://www.icloud.com"
        const val AUTH_ENDPOINT = "https://idmsa.apple.com/appleauth/auth"
        const val SETUP_ENDPOINT = "https://setup.icloud.com/setup/ws/1"
    }
}
