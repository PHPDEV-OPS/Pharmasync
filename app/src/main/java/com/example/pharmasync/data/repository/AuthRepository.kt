package com.example.pharmasync.data.repository

import com.example.pharmasync.data.local.AppDatabase
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.util.SessionPrefs
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SignInResult {
    data class Success(val role: Role) : SignInResult
    data class EmailNotVerified(val email: String) : SignInResult

    /** Signed in, but this account has no Pharmasync profile yet (finish setting it up). */
    data object ProfileMissing : SignInResult
}

data class SignUpForm(
    val role: Role,
    val name: String,
    val email: String,
    val password: String,
    val businessName: String,
    val address: String,
    val phone: String,
)

/**
 * Firebase Authentication handles sign-in only. Profiles and all other data live in Neon; the
 * Firebase ID token is what the backend verifies.
 */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val users: UserRepository,
    private val session: SessionPrefs,
    private val database: AppDatabase,
) {
    val currentUid: String? get() = auth.currentUser?.uid

    fun isSignedInAndVerified(): Boolean = auth.currentUser?.isEmailVerified == true

    /** Role for routing a returning user; falls back to the cached value when offline. */
    suspend fun resolveRole(uid: String): Role? {
        val fetched = runCatching { withTimeoutOrNull(PROFILE_TIMEOUT_MS) { users.refresh()?.role } }.getOrNull()
        val role = fetched ?: session.cachedRole(uid)
        if (role != null) session.cacheRole(uid, role)
        return role
    }

    suspend fun signIn(email: String, password: String): SignInResult {
        val user = auth.signInWithEmailAndPassword(email, password).await().user
            ?: error("Sign-in returned no user")
        user.reload().await()
        if (!user.isEmailVerified) {
            runCatching { user.sendEmailVerification().await() }
            auth.signOut()
            return SignInResult.EmailNotVerified(email)
        }
        // Refresh the token so the backend sees the verified email straight away.
        runCatching { user.getIdToken(true).await() }
        val profile = users.refresh() ?: return SignInResult.ProfileMissing
        session.cacheRole(user.uid, profile.role)
        return SignInResult.Success(profile.role)
    }

    suspend fun signUp(form: SignUpForm) {
        val user = auth.createUserWithEmailAndPassword(form.email, form.password).await().user
            ?: error("Sign-up returned no user")
        runCatching { user.updateProfile(userProfileChangeRequest { displayName = form.name }).await() }
        try {
            users.createOrUpdate(form.role, form.name, form.businessName, form.address, form.phone)
            session.cacheRole(user.uid, form.role)
        } catch (e: Exception) {
            // The login exists but the profile didn't save; the next sign-in offers to finish setup.
            auth.signOut()
            throw e
        }
        try {
            user.sendEmailVerification().await()
        } finally {
            auth.signOut()
        }
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun signOut() {
        auth.signOut()
        session.clear()
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }

    private companion object {
        const val PROFILE_TIMEOUT_MS = 8_000L
    }
}
