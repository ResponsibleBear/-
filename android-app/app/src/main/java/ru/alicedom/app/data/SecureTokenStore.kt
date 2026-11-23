package ru.alicedom.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureTokenStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "alice_dom_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_YANDEX_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_YANDEX_TOKEN, null)

    fun saveClientId(clientId: String) {
        prefs.edit().putString(KEY_CLIENT_ID, clientId).apply()
    }

    fun getClientId(): String = prefs.getString(KEY_CLIENT_ID, "") ?: ""

    fun clear() {
        prefs.edit().remove(KEY_YANDEX_TOKEN).apply()
    }

    companion object {
        private const val KEY_YANDEX_TOKEN = "yandex_oauth_token"
        private const val KEY_CLIENT_ID = "yandex_client_id"
    }
}
