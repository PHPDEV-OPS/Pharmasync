package com.example.pharmasync.data.repository

import com.example.pharmasync.data.local.AppDatabase
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.data.model.UserProfile
import com.example.pharmasync.util.SessionPrefs
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface SignInResult {
    data class Success(val role: Role) : SignInResult
    data class EmailNotVerified(val email: String) : SignInResult
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

class AuthRepository(
    private val auth: FirebaseAuth,
    private val users: UserRepository,
    private val session: SessionPrefs,
    private val database: AppDatabase,
) {
    val currentUid: String? get() = auth.currentUser?.uid

    /** Role for routing a returning user; uses the cached value when offline. */
    suspend fun resolveRole(uid: String): Role {
        val cached = session.cachedRole(uid)
        val fetched = runCatching { users.fetchProfile(uid)?.role }.getOrNull()
        val role = fetched ?: cached ?: Role.PHARMACIST
        session.cacheRole(uid, role)
        return role
    }

    fun isSignedInAndVerified(): Boolean = auth.currentUser?.isEmailVerified == true

    suspend fun signIn(email: String, password: String): SignInResult {
        val user = auth.signInWithEmailAndPassword(email, password).await().user
            ?: error("Sign-in returned no user")
        user.reload().await()
        if (!user.isEmailVerified) {
            runCatching { user.sendEmailVerification().await() }
            auth.signOut()
            return SignInResult.EmailNotVerified(email)
        }
        return SignInResult.Success(resolveRole(user.uid))
    }

    suspend fun signUp(form: SignUpForm) {
        val user = auth.createUserWithEmailAndPassword(form.email, form.password).await().user
            ?: error("Sign-up returned no user")
        runCatching { user.updateProfile(userProfileChangeRequest { displayName = form.name }).await() }
        val profile = UserProfile(
            uid = user.uid,
            email = form.email,
            name = form.name,
            businessName = form.businessName,
            address = form.address,
            phone = form.phone,
            role = form.role,
            photoUrl = "",
            photoVersion = 0L,
        )
        users.createProfile(profile)
        session.cacheRole(user.uid, form.role)
        user.sendEmailVerification().await()
        auth.signOut()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun signOut() {
        auth.signOut()
        session.clear()
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }
}
