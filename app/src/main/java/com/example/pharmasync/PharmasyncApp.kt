package com.example.pharmasync

import android.app.Application
import android.content.Context
import com.example.pharmasync.util.Notifications
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

class PharmasyncApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(persistentCacheSettings { })
            .build()
        container = AppContainer(this)
        Notifications.createChannels(this)
        removeLegacyData()
    }

    /** Earlier versions stored the password in plain text and used a different Room file. */
    private fun removeLegacyData() {
        getSharedPreferences("USERDATA", MODE_PRIVATE).edit().clear().apply()
        deleteDatabase("pharmasync_room.db")
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PharmasyncApp).container
