package com.smartclock.data.remote.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smartclock.BuildConfig
import com.smartclock.data.local.SessionStore
import com.smartclock.data.remote.dto.ApiAuthResponse
import com.smartclock.data.remote.dto.ApiErrorResponse
import com.smartclock.data.remote.dto.ApiRefreshTokenRequest
import com.smartclock.util.LegacyTextSanitizer
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ApiClient @Inject constructor(
    private val sessionStore: SessionStore
) {
    private companion object {
        const val CLIENT_TYPE_APP = "app"
    }

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
    private val refreshMutex = Mutex()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend inline fun <reified T> get(path: String, authenticated: Boolean = true): T =
        request("GET", path, null, authenticated, object : TypeToken<T>() {}.type)

    suspend inline fun <reified T> post(path: String, body: Any, authenticated: Boolean = true): T =
        request("POST", path, body, authenticated, object : TypeToken<T>() {}.type)

    suspend fun <T> request(
        method: String,
        path: String,
        body: Any?,
        authenticated: Boolean,
        type: Type
    ): T = withContext(Dispatchers.IO) {
        val accessToken = if (authenticated) sessionStore.accessToken() else null
        execute(method, path, body, accessToken, type, allowRefresh = authenticated)
    }

    private suspend fun <T> execute(
        method: String,
        path: String,
        body: Any?,
        accessToken: String?,
        type: Type,
        allowRefresh: Boolean
    ): T {
        val request = buildRequest(method, path, body, accessToken)
        httpClient.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                return gson.fromJson(payload, type)
            }
            if (response.code == 401 && allowRefresh) {
                val refreshedAccessToken = refreshTokens(accessToken)
                return execute(method, path, body, refreshedAccessToken, type, allowRefresh = false)
            }
            val error = runCatching { gson.fromJson(payload, ApiErrorResponse::class.java) }.getOrNull()
            throw IllegalStateException(
                LegacyTextSanitizer.sanitize(error?.message) ?: "HTTP ${response.code}"
            )
        }
    }

    private fun buildRequest(method: String, path: String, body: Any?, accessToken: String?): Request {
        val resolvedUrl = if (path.startsWith("http")) path else "$baseUrl${if (path.startsWith("/")) path else "/$path"}"
        val builder = Request.Builder()
            .url(resolvedUrl)
            .header("Accept", "application/json")
        if (!accessToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $accessToken")
        }
        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post(gson.toJson(body).toRequestBody(jsonMediaType))
            else -> error("Unsupported method: $method")
        }
        return builder.build()
    }

    private suspend fun refreshTokens(failedAccessToken: String?): String = refreshMutex.withLock {
        val latestAccessToken = sessionStore.accessToken()
        if (
            !failedAccessToken.isNullOrBlank() &&
            !latestAccessToken.isNullOrBlank() &&
            latestAccessToken != failedAccessToken
        ) {
            return latestAccessToken
        }

        val refreshToken = sessionStore.refreshToken()
            ?: throw IllegalStateException("Missing refresh token")
        val request = buildRequest(
            method = "POST",
            path = "/api/v1/auth/refresh",
            body = ApiRefreshTokenRequest(refreshToken = refreshToken, clientType = CLIENT_TYPE_APP),
            accessToken = null
        )
        httpClient.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val error = runCatching { gson.fromJson(payload, ApiErrorResponse::class.java) }.getOrNull()
                if (response.code == 401 || response.code == 403) {
                    sessionStore.clear()
                } else {
                    Log.w("ApiClient", "token refresh failed: HTTP ${response.code}")
                }
                throw IllegalStateException(
                    LegacyTextSanitizer.sanitize(error?.message) ?: "Refresh failed"
                )
            }
            val authResponse = gson.fromJson(payload, ApiAuthResponse::class.java)
            sessionStore.updateTokens(authResponse.accessToken, authResponse.refreshToken)
            return authResponse.accessToken
        }
    }
}
