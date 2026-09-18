package com.example.pharmasync.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pharmasync.R
import com.example.pharmasync.appContainer
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.databinding.ActivitySignUpBinding
import com.example.pharmasync.util.UiMessage
import com.example.pharmasync.util.showMessage
import com.example.pharmasync.util.textValue
import com.example.pharmasync.util.validate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Shown when someone is signed in but has no Pharmasync profile yet — for example an account made
 * before the Neon backend existed, or one whose sign-up was interrupted.
 */
class CompleteProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding

    private val selectedRole: Role
        get() = if (binding.roleToggle.checkedButtonId == R.id.role_supplier) Role.SUPPLIER else Role.PHARMACIST

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = appContainer.auth.currentUser
        if (user == null) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        // Reuse the sign-up form without the credential fields; the account already exists.
        binding.title.setText(R.string.complete_profile_title)
        binding.subtitle.text = getString(R.string.complete_profile_subtitle, user.email.orEmpty())
        binding.emailLayout.visibility = View.GONE
        binding.passwordLayout.visibility = View.GONE
        binding.confirmLayout.visibility = View.GONE
        binding.promptRow.visibility = View.GONE
        binding.btnSubmit.setText(R.string.action_save)
        binding.nameInput.setText(user.displayName.orEmpty())

        binding.btnBack.setOnClickListener { signOut() }
        binding.roleToggle.addOnButtonCheckedListener { _, _, isChecked -> if (isChecked) renderRole() }
        renderRole()
        binding.btnSubmit.setOnClickListener { submit() }
    }

    private fun renderRole() {
        val supplier = selectedRole == Role.SUPPLIER
        binding.roleDescription.setText(if (supplier) R.string.role_supplier_desc else R.string.role_pharmacist_desc)
        binding.businessLayout.hint = getString(if (supplier) R.string.label_business_name else R.string.label_pharmacy_name)
    }

    private fun submit() {
        val required = getString(R.string.error_required)
        val valid = listOf(
            binding.businessLayout.validate(if (binding.businessLayout.textValue.isEmpty()) required else null),
            binding.addressLayout.validate(if (binding.addressLayout.textValue.isEmpty()) required else null),
            binding.nameLayout.validate(if (binding.nameLayout.textValue.isEmpty()) required else null),
        ).all { it }
        if (!valid) return

        setLoading(true)
        lifecycleScope.launch {
            try {
                val profile = appContainer.userRepository.createOrUpdate(
                    role = selectedRole,
                    name = binding.nameLayout.textValue,
                    businessName = binding.businessLayout.textValue,
                    address = binding.addressLayout.textValue,
                    phone = binding.phoneLayout.textValue,
                )
                appContainer.session.cacheRole(profile.uid, profile.role)
                startActivity(homeIntent(this@CompleteProfileActivity, profile.role))
                finish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setLoading(false)
                binding.root.showMessage(UiMessage.Error(e))
            }
        }
    }

    private fun signOut() {
        lifecycleScope.launch {
            appContainer.authRepository.signOut()
            startActivity(
                Intent(this@CompleteProfileActivity, WelcomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.INVISIBLE
        binding.btnSubmit.isEnabled = !loading
    }
}
