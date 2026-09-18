package com.example.pharmasync.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pharmasync.R
import com.example.pharmasync.appContainer
import com.example.pharmasync.data.repository.SignInResult
import com.example.pharmasync.databinding.ActivitySignInBinding
import com.example.pharmasync.util.UiMessage
import com.example.pharmasync.util.showMessage
import com.example.pharmasync.util.textValue
import com.example.pharmasync.util.validate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SignInActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignInBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val email = intent.getStringExtra(EXTRA_EMAIL)
        email?.let { binding.emailInput.setText(it) }
        if (intent.getBooleanExtra(EXTRA_VERIFY_EMAIL, false) && email != null) {
            binding.verifyBanner.visibility = View.VISIBLE
            binding.verifyBannerText.text = getString(R.string.verify_banner_message, email)
            binding.passwordInput.requestFocus()
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnGoSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
            finish()
        }
        binding.btnForgotPassword.setOnClickListener { resetPassword() }
        binding.btnSubmit.setOnClickListener { submit() }
        binding.passwordInput.setOnEditorActionListener { _, _, _ -> submit(); true }
    }

    private fun submit() {
        val email = binding.emailLayout.textValue
        val password = binding.passwordLayout.textValue
        val valid = listOf(
            binding.emailLayout.validate(if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) null else getString(R.string.error_invalid_email)),
            binding.passwordLayout.validate(if (password.isEmpty()) getString(R.string.error_required) else null),
        ).all { it }
        if (!valid) return

        setLoading(true)
        lifecycleScope.launch {
            try {
                when (val result = appContainer.authRepository.signIn(email, password)) {
                    is SignInResult.Success -> {
                        startActivity(homeIntent(this@SignInActivity, result.role))
                        finish()
                    }
                    SignInResult.ProfileMissing -> {
                        startActivity(Intent(this@SignInActivity, CompleteProfileActivity::class.java))
                        finish()
                    }
                    is SignInResult.EmailNotVerified -> {
                        setLoading(false)
                        binding.root.showMessage(getString(R.string.msg_email_not_verified, result.email))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setLoading(false)
                binding.root.showMessage(UiMessage.Error(e))
            }
        }
    }

    private fun resetPassword() {
        val email = binding.emailLayout.textValue
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.validate(getString(R.string.msg_enter_email_for_reset))
            binding.emailInput.requestFocus()
            return
        }
        binding.emailLayout.validate(null)
        lifecycleScope.launch {
            try {
                appContainer.authRepository.sendPasswordReset(email)
                binding.root.showMessage(getString(R.string.msg_reset_sent, email))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                binding.root.showMessage(UiMessage.Error(e))
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.INVISIBLE
        binding.btnSubmit.isEnabled = !loading
        binding.btnForgotPassword.isEnabled = !loading
    }

    companion object {
        const val EXTRA_EMAIL = "email"
        const val EXTRA_VERIFY_EMAIL = "verify_email"
    }
}
