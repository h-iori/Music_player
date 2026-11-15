package com.ioristudios.music.external

import android.content.Context

class DriveBackupPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var isDriveLinked: Boolean
        get() = prefs.getBoolean(KEY_DRIVE_LINKED, false)
        set(value) = prefs.edit().putBoolean(KEY_DRIVE_LINKED, value).apply()

    var hasSeenFirstLaunchPrompt: Boolean
        get() = prefs.getBoolean(KEY_SEEN_FIRST_PROMPT, false)
        set(value) = prefs.edit().putBoolean(KEY_SEEN_FIRST_PROMPT, value).apply()

    var isAutoBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BACKUP, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BACKUP, value).apply()

    var linkedAccountHint: String?
        get() = prefs.getString(KEY_ACCOUNT_HINT, null)
        set(value) = prefs.edit().putString(KEY_ACCOUNT_HINT, value).commit().let { }

    var cachedAccessToken: String?
        get() = prefs.getString(KEY_CACHED_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_CACHED_TOKEN, value).commit().let { }

    var tokenExpiresAt: Long
        get() = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_TOKEN_EXPIRES_AT, value).commit().let { }

    var lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST_BACKUP_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP_AT, value).commit().let { }

    var lastRestoreAt: Long
        get() = prefs.getLong(KEY_LAST_RESTORE_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_RESTORE_AT, value).commit().let { }

    var lastStatus: String
        get() = prefs.getString(KEY_LAST_STATUS, "Drive backup is ready") ?: "Drive backup is ready"
        set(value) = prefs.edit().putString(KEY_LAST_STATUS, value).commit().let { }

    fun clearLink() {
        prefs.edit()
            .putBoolean(KEY_DRIVE_LINKED, false)
            .remove(KEY_ACCOUNT_HINT)
            .remove(KEY_CACHED_TOKEN)
            .remove(KEY_TOKEN_EXPIRES_AT)
            .putBoolean(KEY_AUTO_BACKUP, false)
            .commit()
    }

    companion object {
        private const val PREFS = "drive_backup_preferences"
        private const val KEY_DRIVE_LINKED = "drive_linked"
        private const val KEY_SEEN_FIRST_PROMPT = "seen_first_prompt"
        private const val KEY_AUTO_BACKUP = "auto_backup"
        private const val KEY_ACCOUNT_HINT = "account_hint"
        private const val KEY_CACHED_TOKEN = "cached_token"
        private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
        private const val KEY_LAST_BACKUP_AT = "last_backup_at"
        private const val KEY_LAST_RESTORE_AT = "last_restore_at"
        private const val KEY_LAST_STATUS = "last_status"
    }
}
