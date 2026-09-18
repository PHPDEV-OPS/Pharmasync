package com.example.pharmasync

import android.app.Application
import android.content.Context
import com.example.pharmasync.util.Notifications
import org.osmdroid.config.Configuration
import java.io.File

class PharmasyncApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        configureOpenStreetMap()
        removeLegacyData()
    }

    /** OSM's tile policy requires an identifying user agent; tiles are cached in app storage. */
    private fun configureOpenStreetMap() = with(Configuration.getInstance()) {
        userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
        osmdroidBasePath = File(cacheDir, "osmdroid")
        osmdroidTileCache = File(osmdroidBasePath, "tiles")
    }

    /** Earlier versions stored the password in plain text and used different local databases. */
    private fun removeLegacyData() {
        getSharedPreferences("USERDATA", MODE_PRIVATE).edit().clear().apply()
        deleteDatabase("pharmasync_room.db")
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PharmasyncApp).container
