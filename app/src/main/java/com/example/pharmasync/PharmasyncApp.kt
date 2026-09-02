package com.example.pharmasync

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class PharmasyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Enable Firestore offline persistence for smooth local storage sync
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        FirebaseFirestore.getInstance().setFirestoreSettings(settings)
    }
}
