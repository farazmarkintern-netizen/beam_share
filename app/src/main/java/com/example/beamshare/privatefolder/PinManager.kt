package com.example.beamshare.privatefolder

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object PinManager {

    private const val PREFS_FILE = "private_folder_prefs"
    private const val KEY_PIN = "pf_pin"
    private const val KEY_PIN_SET = "pf_pin_set"
    private const val KEY_SECURITY_QUESTION = "pf_security_question"
    private const val KEY_SECURITY_ANSWER = "pf_security_answer"

    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        val appContext = context.applicationContext
        val p = try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        }
        cachedPrefs = p
        return p
    }

    fun savePin(context: Context, pin: String) {
        prefs(context).edit()
            .putString(KEY_PIN, pin)
            .putBoolean(KEY_PIN_SET, true)
            .commit()
    }

    fun getPin(context: Context): String? =
        prefs(context).getString(KEY_PIN, null)

    fun isPinSet(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PIN_SET, false)

    fun clearPin(context: Context) {
        prefs(context).edit()
            .remove(KEY_PIN)
            .putBoolean(KEY_PIN_SET, false)
            .commit()
    }

    // ── Security Question ─────────────────────────────────────────────────────

    fun saveSecurityQuestion(context: Context, question: String, answer: String) {
        prefs(context).edit()
            .putString(KEY_SECURITY_QUESTION, question)
            .putString(KEY_SECURITY_ANSWER, answer.trim().lowercase())
            .commit()
    }

    fun getSecurityQuestion(context: Context): String? =
        prefs(context).getString(KEY_SECURITY_QUESTION, null)

    fun isSecurityQuestionSet(context: Context): Boolean =
        getSecurityQuestion(context) != null

    /** Returns true if the provided answer matches (case-insensitive, trimmed) */
    fun verifySecurityAnswer(context: Context, answer: String): Boolean {
        val saved = prefs(context).getString(KEY_SECURITY_ANSWER, null) ?: return false
        return answer.trim().lowercase() == saved
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().commit()
        cachedPrefs = null
    }
}