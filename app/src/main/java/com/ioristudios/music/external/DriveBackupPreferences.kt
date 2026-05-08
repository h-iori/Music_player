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
        set(value) = prefs.edit().putString(KEY_ACCOUNT_HINT, value).apply()

    var lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST_BACKUP_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP_AT, value).apply()

    var lastRestoreAt: Long
        get() = prefs.getLong(KEY_LAST_RESTORE_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_RESTORE_AT, value).apply()

    var lastStatus: String
        get() = prefs.getString(KEY_LAST_STATUS, "Drive backup is ready") ?: "Drive backup is ready"
        set(value) = prefs.edit().putString(KEY_LAST_STATUS, value).apply()

    fun clearLink() {
        prefs.edit()
            .putBoolean(KEY_DRIVE_LINKED, false)
            .remove(KEY_ACCOUNT_HINT)
            .putBoolean(KEY_AUTO_BACKUP, false)
            .apply()
    }

    companion object {
        private const val PREFS = "drive_backup_preferences"
        private const val KEY_DRIVE_LINKED = "drive_linked"
        private const val KEY_SEEN_FIRST_PROMPT = "seen_first_prompt"
        private const val KEY_AUTO_BACKUP = "auto_backup"
        private const val KEY_ACCOUNT_HINT = "account_hint"
        private const val KEY_LAST_BACKUP_AT = "last_backup_at"
        private const val KEY_LAST_RESTORE_AT = "last_restore_at"
        private const val KEY_LAST_STATUS = "last_status"
    }
}
