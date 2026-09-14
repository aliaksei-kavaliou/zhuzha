package com.inkfirst.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.inkfirst.core.DEFAULT_GEMINI_IMAGE_MODEL
import com.inkfirst.core.DEFAULT_GEMINI_MODEL

/**
 * Keys live in EncryptedSharedPreferences — file is AES-256-GCM encrypted at rest,
 * backed by an Android Keystore-held master key. Never write the key to logs or
 * to plain files.
 */
class ApiKeyStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getApiKey(): String? = prefs.getString(KEY_API, null)?.ifBlank { null }

    fun setApiKey(value: String) {
        prefs.edit().putString(KEY_API, value.trim()).apply()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API).apply()
    }

    fun getModelName(): String = prefs.getString(KEY_MODEL, null)?.ifBlank { null } ?: DEFAULT_GEMINI_MODEL

    fun setModelName(value: String) {
        prefs.edit().putString(KEY_MODEL, value.trim().ifEmpty { DEFAULT_GEMINI_MODEL }).apply()
    }

    /**
     * Model used to draw chapter figures. Separate from [getModelName]
     * because it must be an image-capable model — the text model can't draw
     * and an image model writes poor prose.
     */
    fun getImageModelName(): String =
        prefs.getString(KEY_IMAGE_MODEL, null)?.ifBlank { null } ?: DEFAULT_GEMINI_IMAGE_MODEL

    fun setImageModelName(value: String) {
        prefs.edit()
            .putString(KEY_IMAGE_MODEL, value.trim().ifEmpty { DEFAULT_GEMINI_IMAGE_MODEL })
            .apply()
    }

    /** Default language pre-filled on the New Course form. Not encrypted-secret but shares the same prefs file. */
    fun getPreferredLanguage(): String =
        prefs.getString(KEY_LANGUAGE, null)?.ifBlank { null } ?: DEFAULT_LANGUAGE

    fun setPreferredLanguage(value: String) {
        prefs.edit().putString(KEY_LANGUAGE, value.trim().ifEmpty { DEFAULT_LANGUAGE }).apply()
    }

    private companion object {
        const val PREFS_NAME = "inkfirst_secrets"
        const val KEY_API = "gemini_api_key"
        const val KEY_MODEL = "gemini_model"
        const val KEY_IMAGE_MODEL = "gemini_image_model"
        const val KEY_LANGUAGE = "preferred_language"
        const val DEFAULT_LANGUAGE = "English"
    }
}

/** Show last 4 characters of a key, mask the rest. Empty/null → blank. */
fun maskApiKey(key: String?): String {
    if (key.isNullOrBlank()) return ""
    val visible = key.takeLast(4)
    return "•".repeat(8) + visible
}
