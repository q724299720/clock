package com.smartclock.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartclock.util.CryptoUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "session")

@Singleton
class SessionStore @Inject constructor(
    private val context: Context
) {
    private val keyUserId = longPreferencesKey("user_id")
    private val keyAccessTokenEnc = stringPreferencesKey("access_token_enc")
    private val keyRefreshTokenEnc = stringPreferencesKey("refresh_token_enc")
    private val keyNickname = stringPreferencesKey("nickname")
    private val keyPermissionGuideDone = booleanPreferencesKey("permission_guide_done")

    val userIdFlow: Flow<Long> = context.sessionDataStore.data.map { it[keyUserId] ?: 0L }
    val permissionGuideDoneFlow: Flow<Boolean> =
        context.sessionDataStore.data.map { it[keyPermissionGuideDone] ?: false }
    val accessTokenFlow: Flow<String?> =
        context.sessionDataStore.data.map { it[keyAccessTokenEnc]?.let(::safeDecrypt) }
    val refreshTokenFlow: Flow<String?> =
        context.sessionDataStore.data.map { it[keyRefreshTokenEnc]?.let(::safeDecrypt) }

    suspend fun save(userId: Long, accessToken: String, refreshToken: String, nickname: String?) {
        context.sessionDataStore.edit {
            it[keyUserId] = userId
            it[keyAccessTokenEnc] = CryptoUtil.encrypt(accessToken)
            it[keyRefreshTokenEnc] = CryptoUtil.encrypt(refreshToken)
            if (nickname != null) it[keyNickname] = nickname
        }
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        context.sessionDataStore.edit {
            it[keyAccessTokenEnc] = CryptoUtil.encrypt(accessToken)
            it[keyRefreshTokenEnc] = CryptoUtil.encrypt(refreshToken)
        }
    }

    suspend fun accessToken(): String? = accessTokenFlow.first()

    suspend fun refreshToken(): String? = refreshTokenFlow.first()

    suspend fun markPermissionGuideCompleted() {
        context.sessionDataStore.edit { it[keyPermissionGuideDone] = true }
    }

    suspend fun clear() {
        context.sessionDataStore.edit {
            it.remove(keyUserId)
            it.remove(keyAccessTokenEnc)
            it.remove(keyRefreshTokenEnc)
            it.remove(keyNickname)
        }
    }

    private fun safeDecrypt(value: String): String? = runCatching {
        CryptoUtil.decrypt(value)
    }.getOrNull()
}
