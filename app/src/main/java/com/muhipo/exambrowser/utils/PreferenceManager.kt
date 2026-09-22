package com.muhipo.exambrowser.utils

import android.content.Context
import android.content.SharedPreferences
import com.muhipo.exambrowser.security.CryptoUtils

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "exam_browser_prefs"

        private const val KEY_EXAM_ACTIVE = "key_exam_active"
        private const val KEY_EXAM_URL = "key_exam_url"
        private const val KEY_EXAM_START_TIME = "key_exam_start_time"

        private const val KEY_KIOSK_ENABLED = "key_kiosk_enabled"
        private const val KEY_SCREENSHOT_PROTECTED = "key_screenshot_protected"
        private const val KEY_ALLOW_BACK = "key_allow_back"
        private const val KEY_ALLOW_DOWNLOADS = "key_allow_downloads"

        private const val KEY_WHITELIST_DOMAINS = "key_whitelist_domains"
        private const val KEY_ADMIN_PIN_HASH = "key_admin_pin_hash"
        private const val KEY_ADMIN_PIN_SALT = "key_admin_pin_salt"

        private const val DEFAULT_PIN = "123456"
    }

    init {
        // Initialize default hashed PIN if not set
        if (!prefs.contains(KEY_ADMIN_PIN_HASH)) {
            val salt = CryptoUtils.generateSalt()
            val hash = CryptoUtils.hashPin(DEFAULT_PIN, salt)
            prefs.edit()
                .putString(KEY_ADMIN_PIN_SALT, salt)
                .putString(KEY_ADMIN_PIN_HASH, hash)
                .apply()
        }
    }

    // --- Exam Session Management ---

    var isExamActive: Boolean
        get() = prefs.getBoolean(KEY_EXAM_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_EXAM_ACTIVE, value).apply()

    var activeExamUrl: String?
        get() = prefs.getString(KEY_EXAM_URL, null)
        set(value) = prefs.edit().putString(KEY_EXAM_URL, value).apply()

    var examStartTime: Long
        get() = prefs.getLong(KEY_EXAM_START_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_EXAM_START_TIME, value).apply()

    fun startExamSession(url: String) {
        prefs.edit()
            .putBoolean(KEY_EXAM_ACTIVE, true)
            .putString(KEY_EXAM_URL, url)
            .putLong(KEY_EXAM_START_TIME, System.currentTimeMillis())
            .apply()
    }

    fun clearExamSession() {
        prefs.edit()
            .putBoolean(KEY_EXAM_ACTIVE, false)
            .remove(KEY_EXAM_URL)
            .remove(KEY_EXAM_START_TIME)
            .apply()
    }

    // --- Security & Policy Toggles ---

    var isKioskEnabled: Boolean
        get() = prefs.getBoolean(KEY_KIOSK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_KIOSK_ENABLED, value).apply()

    var isScreenshotProtectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREENSHOT_PROTECTED, true)
        set(value) = prefs.edit().putBoolean(KEY_SCREENSHOT_PROTECTED, value).apply()

    var isBackAllowed: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_BACK, false)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_BACK, value).apply()

    var isDownloadAllowed: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_DOWNLOADS, false)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_DOWNLOADS, value).apply()

    // --- Whitelist Management ---

    fun getWhitelistDomains(): MutableSet<String> {
        val set = prefs.getStringSet(KEY_WHITELIST_DOMAINS, emptySet()) ?: emptySet()
        return set.toMutableSet()
    }

    fun addWhitelistDomain(domain: String) {
        val cleanDomain = domain.trim().lowercase()
        if (cleanDomain.isNotEmpty()) {
            val domains = getWhitelistDomains()
            domains.add(cleanDomain)
            prefs.edit().putStringSet(KEY_WHITELIST_DOMAINS, domains).apply()
        }
    }

    fun removeWhitelistDomain(domain: String) {
        val cleanDomain = domain.trim().lowercase()
        val domains = getWhitelistDomains()
        if (domains.remove(cleanDomain)) {
            prefs.edit().putStringSet(KEY_WHITELIST_DOMAINS, domains).apply()
        }
    }

    // --- Admin PIN Management ---

    fun verifyAdminPin(enteredPin: String): Boolean {
        val salt = prefs.getString(KEY_ADMIN_PIN_SALT, null) ?: return false
        val expectedHash = prefs.getString(KEY_ADMIN_PIN_HASH, null) ?: return false
        return CryptoUtils.verifyPin(enteredPin, salt, expectedHash)
    }

    fun updateAdminPin(newPin: String) {
        val newSalt = CryptoUtils.generateSalt()
        val newHash = CryptoUtils.hashPin(newPin, newSalt)
        prefs.edit()
            .putString(KEY_ADMIN_PIN_SALT, newSalt)
            .putString(KEY_ADMIN_PIN_HASH, newHash)
            .apply()
    }
}
