package com.smartclock.data.remote

import com.smartclock.data.local.SessionStore
import com.smartclock.data.remote.api.ApiClient
import com.smartclock.data.remote.dto.ApiAuthRequest
import com.smartclock.data.remote.dto.ApiAuthResponse
import com.smartclock.data.remote.dto.ApiRefreshTokenRequest
import com.smartclock.data.remote.dto.ApiStatusResponse
import com.smartclock.data.remote.dto.toDomain
import com.smartclock.domain.model.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRemoteDataSource @Inject constructor(
    private val apiClient: ApiClient,
    private val sessionStore: SessionStore
) {
    private companion object {
        const val CLIENT_TYPE_APP = "app"
    }

    sealed interface AuthResult {
        data class Success(
            val user: User,
            val accessToken: String,
            val refreshToken: String
        ) : AuthResult

        data class Failure(val message: String) : AuthResult
    }

    suspend fun register(
        account: String,
        isEmail: Boolean,
        password: String,
        nickname: String?
    ): AuthResult = runCatching {
        val response = apiClient.post<ApiAuthResponse>(
            path = "/api/v1/auth/register",
            body = ApiAuthRequest(
                account = account,
                isEmail = isEmail,
                password = password,
                nickname = nickname,
                clientType = CLIENT_TYPE_APP
            ),
            authenticated = false
        )
        AuthResult.Success(
            user = response.user.toDomain(),
            accessToken = response.accessToken,
            refreshToken = response.refreshToken
        )
    }.getOrElse {
        AuthResult.Failure(it.message ?: "register failed")
    }

    suspend fun login(account: String, isEmail: Boolean, password: String): AuthResult = runCatching {
        val response = apiClient.post<ApiAuthResponse>(
            path = "/api/v1/auth/login",
            body = ApiAuthRequest(
                account = account,
                isEmail = isEmail,
                password = password,
                clientType = CLIENT_TYPE_APP
            ),
            authenticated = false
        )
        AuthResult.Success(
            user = response.user.toDomain(),
            accessToken = response.accessToken,
            refreshToken = response.refreshToken
        )
    }.getOrElse {
        AuthResult.Failure(it.message ?: "login failed")
    }

    suspend fun logoutBestEffort() {
        val refreshToken = sessionStore.refreshToken() ?: return
        runCatching {
            apiClient.post<ApiStatusResponse>(
                path = "/api/v1/auth/logout",
                body = ApiRefreshTokenRequest(refreshToken = refreshToken, clientType = CLIENT_TYPE_APP),
                authenticated = true
            )
        }
    }
}
