package com.example.pharmasync.ui.session

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.pharmasync.R
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.databinding.ActivityHostBinding
import com.example.pharmasync.ui.auth.CompleteProfileActivity
import com.example.pharmasync.ui.auth.WelcomeActivity
import com.example.pharmasync.util.Notifications
import com.example.pharmasync.util.collectWhileStarted
import com.google.android.material.snackbar.Snackbar

/** Implemented by activities hosting [SessionViewModel] so fragments can build the same instance. */
interface SessionHost {
    val sessionRole: Role
}

/** Bottom-navigation home shared by the pharmacist and supplier experiences. */
abstract class SessionHostActivity : AppCompatActivity(), SessionHost {

    protected lateinit var binding: ActivityHostBinding
    protected val session: SessionViewModel by viewModels { SessionViewModel.factory(application, sessionRole) }

    @get:MenuRes
    protected abstract val menuRes: Int

    @get:IdRes
    protected abstract val startTab: Int

    protected abstract fun createTab(@IdRes itemId: Int): Fragment

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!session.isSignedIn) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        binding = ActivityHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavigation.inflateMenu(menuRes)
        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = startTab
            showTab(startTab)
        }
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }

        collectWhileStarted(session.messages) { message ->
            Snackbar.make(binding.root, message.resolve(this), Snackbar.LENGTH_LONG)
                .setAnchorView(binding.bottomNavigation)
                .show()
        }
        requestNotificationPermission()

        collectWhileStarted(session.profileMissing) { missing ->
            if (missing) {
                startActivity(Intent(this, CompleteProfileActivity::class.java))
                finish()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isFinishing) {
            session.setForeground(true)
            session.refreshAll()
        }
    }

    override fun onStop() {
        super.onStop()
        session.setForeground(false)
    }

    fun selectTab(@IdRes itemId: Int) {
        binding.bottomNavigation.selectedItemId = itemId
    }

    protected fun setBadge(@IdRes itemId: Int, count: Int) {
        if (count > 0) {
            binding.bottomNavigation.getOrCreateBadge(itemId).number = count
        } else {
            binding.bottomNavigation.removeBadge(itemId)
        }
    }

    private fun showTab(@IdRes itemId: Int) {
        val tag = "tab_$itemId"
        val fm = supportFragmentManager
        val transaction = fm.beginTransaction().setReorderingAllowed(true)
        fm.fragments.filter { it.tag?.startsWith("tab_") == true && it.tag != tag }.forEach { transaction.hide(it) }
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            transaction.add(R.id.fragment_container, createTab(itemId), tag)
        } else {
            transaction.show(existing)
        }
        transaction.commitNow()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
