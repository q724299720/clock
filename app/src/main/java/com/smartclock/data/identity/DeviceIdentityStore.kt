package com.smartclock.data.identity

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext

private val Context.deviceIdentityDataStore by preferencesDataStore(name = "device_identity")

@Singleton
class DeviceIdentityStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyDeviceId = stringPreferencesKey("device_id")

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.deviceIdentityDataStore.data.map { it[keyDeviceId] }.first()
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        context.deviceIdentityDataStore.edit { it[keyDeviceId] = generated }
        return generated
    }
}
