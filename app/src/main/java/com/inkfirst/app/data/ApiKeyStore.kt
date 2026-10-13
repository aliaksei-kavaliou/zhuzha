package com.inkfirst.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.IOException
import java.security.GeneralSecurityException
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

    private val app = context.applicationContext

    /**
     * Null when the encrypted store could not be opened at all — see [open].
     */
    private val prefs: SharedPreferences? = open(app, retry = true)

    /**
     * Used only when [prefs] is null, and deliberately NOT persisted: a key
     * typed into this session works, and is forgotten when the process ends.
     *
     * The alternative — falling back to plain SharedPreferences — would store
     * the one secret this app holds in cleartext to avoid an inconvenience.
     * Forgetting it is the honest failure.
     */
    private val sessionOnly = mutableMapOf<String, String>()

    /**
     * Whether anything written here will survive a restart. False means the
     * device's Keystore would not give us a master key; the reader needs
     * telling, or they will re-enter an API key every morning and wonder why.
     */
    val isPersistent: Boolean get() = prefs != null

    private fun read(key: String): String? = prefs?.getString(key, null) ?: sessionOnly[key]

    private fun write(key: String, value: String) {
        if (prefs != null) prefs.edit().putString(key, value).apply() else sessionOnly[key] = value
    }

    private fun drop(key: String) {
        if (prefs != null) prefs.edit().remove(key).apply() else sessionOnly.remove(key)
    }

    fun getApiKey(): String? = read(KEY_API)?.ifBlank { null }

    fun setApiKey(value: String) = write(KEY_API, value.trim())

    fun clearApiKey() = drop(KEY_API)

    fun getModelName(): String = read(KEY_MODEL)?.ifBlank { null } ?: DEFAULT_GEMINI_MODEL

    fun setModelName(value: String) {
        write(KEY_MODEL, value.trim().ifEmpty { DEFAULT_GEMINI_MODEL })
    }

    /**
     * Model used to draw chapter figures. Separate from [getModelName]
     * because it must be an image-capable model — the text model can't draw
     * and an image model writes poor prose.
     */
    fun getImageModelName(): String =
        read(KEY_IMAGE_MODEL)?.ifBlank { null } ?: DEFAULT_GEMINI_IMAGE_MODEL

    fun setImageModelName(value: String) {
        write(KEY_IMAGE_MODEL, value.trim().ifEmpty { DEFAULT_GEMINI_IMAGE_MODEL })
    }

    /** Default language pre-filled on the New Course form. Not encrypted-secret but shares the same prefs file. */
    fun getPreferredLanguage(): String =
        read(KEY_LANGUAGE)?.ifBlank { null } ?: DEFAULT_LANGUAGE

    fun setPreferredLanguage(value: String) {
        write(KEY_LANGUAGE, value.trim().ifEmpty { DEFAULT_LANGUAGE })
    }

    private companion object {

        /**
         * Opening the encrypted store CAN FAIL, and used to take the app with
         * it: this ran in a property initializer with no catch, so the whole
         * object failed to construct and Settings crashed every time it was
         * opened — with the key unreadable and no way to enter a new one.
         *
         * The master key lives in the Android Keystore, and that entry goes
         * away on a lock-screen change, on a restore that brings back files
         * but not Keystore entries, and on OEM firmware quirks. A Boox is
         * modified AOSP, which is exactly where to expect the last of those.
         *
         * So: try, and on a crypto or IO failure delete the unreadable file
         * and try once more. A deleted file costs the reader their API key,
         * which they can re-enter; a crash costs them the app.
         */
        private fun open(app: Context, retry: Boolean): SharedPreferences? = try {
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                app,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: GeneralSecurityException) {
            recover(app, retry, e)
        } catch (e: IOException) {
            recover(app, retry, e)
        }

        private fun recover(app: Context, retry: Boolean, cause: Exception): SharedPreferences? {
            Log.w("Zhuzha", "encrypted prefs unreadable: ${cause.message}")
            if (!retry) return null
            app.deleteSharedPreferences(PREFS_NAME)
            return open(app, retry = false)
        }

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
