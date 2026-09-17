package com.example.pharmasync.util

import android.content.Context
import androidx.core.content.edit
import com.example.pharmasync.data.model.Role

/**
 * Small non-sensitive session cache. Firebase Auth persists the login itself, so credentials are
 * never stored here.
 */
class SessionPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("pharmasync_session", Context.MODE_PRIVATE)

    fun cachedRole(uid: String): Role? = prefs.getString("role_$uid", null)?.let(Role::from)

    fun cacheRole(uid: String, role: Role) = prefs.edit { putString("role_$uid", role.firestoreValue) }

    /**
     * Returns true the first time [key] is seen for [uid]. While [seedOnly] is true keys are
     * recorded silently, so opening the app doesn't replay notifications for old events.
     */
    fun markAlert(uid: String, key: String, seedOnly: Boolean): Boolean {
        val setKey = "alerts_$uid"
        val seen = prefs.getStringSet(setKey, emptySet()).orEmpty()
        if (key in seen) return false
        prefs.edit { putStringSet(setKey, seen + key) }
        return !seedOnly
    }

    fun clearAlert(uid: String, key: String) {
        val setKey = "alerts_$uid"
        val seen = prefs.getStringSet(setKey, emptySet()).orEmpty()
        if (key in seen) prefs.edit { putStringSet(setKey, seen - key) }
    }

    fun alertsSeeded(uid: String): Boolean = prefs.getBoolean("alerts_seeded_$uid", false)

    fun markAlertsSeeded(uid: String) = prefs.edit { putBoolean("alerts_seeded_$uid", true) }

    fun clear() = prefs.edit { clear() }
}
