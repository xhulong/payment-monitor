package com.example.paymentmonitor.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PairingState {
    UNPAIRED,
    PAIRED,
    REPAIR_REQUIRED,
}

data class DeviceRuntimeConfig(
    val heartbeatIntervalSeconds: Int = 60,
    val onlineThresholdSeconds: Int = 180,
    val maxBatchSize: Int = 100,
    val maxRequestBytes: Int = 1_048_576,
    val rawPayloadUploadEnabled: Boolean = false,
)

data class DeviceCredentials(
    val serverUrl: String,
    val deviceId: Long,
    val deviceSecret: String,
    val credentialVersion: Int,
    val protocolVersion: Int,
    val merchantCode: String? = null,
    val merchantName: String? = null,
    val deviceRole: String? = null,
    val platformScope: String? = null,
    val clockOffsetSeconds: Long = 0,
    val config: DeviceRuntimeConfig = DeviceRuntimeConfig(),
)

data class DeviceConnectionState(
    val pairingState: PairingState = PairingState.UNPAIRED,
    val credentials: DeviceCredentials? = null,
    val lastHeartbeatAt: Long? = null,
    val lastSyncAt: Long? = null,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
)

class DeviceStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val state = MutableStateFlow(loadState())

    val stateFlow = state.asStateFlow()

    fun snapshot(): DeviceConnectionState = state.value

    @Synchronized
    fun savePairing(serverUrl: String, data: PairDeviceData, serverTime: String?, protocolVersion: Int) {
        val credentials = DeviceCredentials(
            serverUrl = serverUrl,
            deviceId = data.deviceId,
            deviceSecret = data.deviceSecret,
            credentialVersion = data.credentialVersion,
            protocolVersion = protocolVersion,
            merchantCode = data.merchantCode,
            merchantName = data.merchantName,
            deviceRole = data.deviceRole,
            platformScope = data.platformScope,
            clockOffsetSeconds = serverTime?.let(::calculateOffsetSeconds) ?: 0,
            config = data.toRuntimeConfig(),
        )
        persist(
            DeviceConnectionState(
                pairingState = PairingState.PAIRED,
                credentials = credentials,
            ),
        )
    }

    @Synchronized
    fun updateConfig(config: DeviceConfigData, serverTime: String?) {
        val current = state.value
        val credentials = current.credentials ?: return
        persist(
            current.copy(
                pairingState = PairingState.PAIRED,
                credentials = credentials.copy(
                    clockOffsetSeconds = serverTime
                        ?.let(::calculateOffsetSeconds)
                        ?: credentials.clockOffsetSeconds,
                    config = config.toRuntimeConfig(),
                    deviceRole = config.deviceRole ?: credentials.deviceRole,
                    platformScope = config.platformScope ?: credentials.platformScope,
                ),
                lastErrorCode = null,
                lastErrorMessage = null,
            ),
        )
    }

    @Synchronized
    fun updateClock(serverTime: String?) {
        if (serverTime == null) return
        val current = state.value
        val credentials = current.credentials ?: return
        persist(
            current.copy(
                credentials = credentials.copy(
                    clockOffsetSeconds = calculateOffsetSeconds(serverTime),
                ),
            ),
        )
    }

    @Synchronized
    fun markHeartbeat(timestamp: Long = System.currentTimeMillis()) {
        persist(
            state.value.copy(
                lastHeartbeatAt = timestamp,
                lastErrorCode = null,
                lastErrorMessage = null,
            ),
        )
    }

    @Synchronized
    fun markSynced(timestamp: Long = System.currentTimeMillis()) {
        persist(
            state.value.copy(
                lastSyncAt = timestamp,
                lastErrorCode = null,
                lastErrorMessage = null,
            ),
        )
    }

    @Synchronized
    fun markError(code: String?, message: String?) {
        persist(state.value.copy(lastErrorCode = code, lastErrorMessage = message))
    }

    @Synchronized
    fun markRepairRequired(code: String?, message: String?) {
        persist(
            state.value.copy(
                pairingState = PairingState.REPAIR_REQUIRED,
                lastErrorCode = code,
                lastErrorMessage = message,
            ),
        )
    }

    @Synchronized
    fun clearPairing() {
        preferences.edit().clear().commit()
        state.value = DeviceConnectionState()
    }

    private fun persist(newState: DeviceConnectionState) {
        val encoded = encrypt(gson.toJson(newState))
        // Pairing and revocation state must survive an immediate process stop.
        // SharedPreferences.apply() can still be pending when an instrumentation
        // process or a background worker exits, which could resurrect PAIRED.
        preferences.edit().putString(ENCRYPTED_STATE, encoded).commit()
        state.value = newState
    }

    private fun loadState(): DeviceConnectionState {
        val encoded = preferences.getString(ENCRYPTED_STATE, null) ?: return DeviceConnectionState()
        return runCatching {
            gson.fromJson(decrypt(encoded), DeviceConnectionState::class.java)
        }.getOrElse {
            preferences.edit().clear().apply()
            DeviceConnectionState(
                pairingState = PairingState.REPAIR_REQUIRED,
                lastErrorCode = "KEYSTORE_INVALIDATED",
                lastErrorMessage = "本机安全密钥已失效，请重新配对",
            )
        }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > IV_LENGTH)
        val iv = payload.copyOfRange(0, IV_LENGTH)
        val encrypted = payload.copyOfRange(IV_LENGTH, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun calculateOffsetSeconds(serverTime: String): Long = runCatching {
        java.time.Instant.parse(serverTime).epochSecond - java.time.Instant.now().epochSecond
    }.getOrDefault(0)

    private companion object {
        const val PREFERENCES_NAME = "device_sync_state"
        const val ENCRYPTED_STATE = "encrypted_state"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "payment_monitor_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
