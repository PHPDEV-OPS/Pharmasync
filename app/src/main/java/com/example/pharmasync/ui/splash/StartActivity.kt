package com.example.pharmasync.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.pharmasync.appContainer
import com.example.pharmasync.ui.auth.WelcomeActivity
import com.example.pharmasync.ui.auth.homeIntent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Routes to the right home screen. Firebase Auth keeps the session, so returning users go straight
 * in — including offline, using the cached role.
 */
class StartActivity : AppCompatActivity() {

    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !ready }

        val auth = appContainer.authRepository
        val uid = auth.currentUid
        lifecycleScope.launch {
            val target = if (uid != null && auth.isSignedInAndVerified()) {
                val role = withTimeoutOrNull(ROLE_TIMEOUT_MS) { auth.resolveRole(uid) }
                    ?: appContainer.session.cachedRole(uid)
                if (role != null) homeIntent(this@StartActivity, role) else Intent(this@StartActivity, WelcomeActivity::class.java)
            } else {
                Intent(this@StartActivity, WelcomeActivity::class.java)
            }
            ready = true
            startActivity(target)
            finish()
        }
    }

    private companion object {
        const val ROLE_TIMEOUT_MS = 4_000L
    }
}
