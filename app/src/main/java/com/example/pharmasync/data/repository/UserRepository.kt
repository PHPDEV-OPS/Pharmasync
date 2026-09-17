package com.example.pharmasync.data.repository

import android.net.Uri
import android.util.Log
import com.example.pharmasync.data.firestore.Fs
import com.example.pharmasync.data.firestore.toUserProfile
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.data.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The signed-in user's profile. `Users/{uid}` is the primary record; pharmacies are mirrored to
 * `Medical-Store/{uid}` (public listing) and suppliers to `Suppliers-Data/{uid}`.
 */
class UserRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: StorageRepository,
    private val location: LocationRepository,
) {
    fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = firestore.collection(Fs.USERS).document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Profile listener failed", error)
                return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.toUserProfile())
        }
        awaitClose { registration.remove() }
    }

    suspend fun fetchProfile(uid: String): UserProfile? =
        firestore.collection(Fs.USERS).document(uid).get().await().takeIf { it.exists() }?.toUserProfile()

    /** Writes a brand-new profile (sign-up). Awaited, since sign-up is online anyway. */
    suspend fun createProfile(profile: UserProfile) {
        val data = mapOf(
            "Uid" to profile.uid,
            "Email" to profile.email,
            "Uname" to profile.name,
            "Shop-Name" to profile.businessName,
            "Address" to profile.address,
            "Phone" to profile.phone,
            "Role" to profile.role.firestoreValue,
            "CreatedAt" to Timestamp.now(),
        )
        firestore.collection(Fs.USERS).document(profile.uid).set(data, SetOptions.merge()).await()
        mirrorDoc(profile.uid, profile.role).set(mirrorData(profile), SetOptions.merge()).await()
        geocodeAndSave(profile)
    }

    fun updateProfile(profile: UserProfile) {
        val data = mapOf(
            "Uname" to profile.name,
            "Shop-Name" to profile.businessName,
            "Address" to profile.address,
            "Phone" to profile.phone,
        )
        firestore.collection(Fs.USERS).document(profile.uid).set(data, SetOptions.merge())
        mirrorDoc(profile.uid, profile.role).set(mirrorData(profile), SetOptions.merge())
    }

    /** Re-geocodes the address so the pharmacy map pin follows address edits. */
    suspend fun geocodeAndSave(profile: UserProfile) {
        val point = location.geocode(profile.address) ?: return
        saveLocation(profile, point, profile.address)
    }

    fun saveLocation(profile: UserProfile, point: GeoPoint, address: String) {
        val uid = profile.uid
        val coordinates = mapOf(
            "Latitude" to point.latitude,
            "Longitude" to point.longitude,
            "Address" to address,
            "Shopname" to profile.businessName,
            "UserID" to uid,
            "CreatedAt" to Timestamp.now(),
        )
        firestore.collection(Fs.COORDINATES).document(uid)
            .collection(Fs.COORDINATES_SUB).document(Fs.COORDINATES_DOC)
            .set(coordinates, SetOptions.merge())
        val location = mapOf("Latitude" to point.latitude, "Longitude" to point.longitude, "Address" to address)
        firestore.collection(Fs.USERS).document(uid).set(location, SetOptions.merge())
        mirrorDoc(uid, profile.role).set(location, SetOptions.merge())
    }

    /**
     * Compresses and uploads a profile photo to `profile_images/{uid}/avatar.jpg`, then stores the
     * download URL on the profile documents and the Firebase Auth user.
     */
    suspend fun uploadProfilePhoto(profile: UserProfile, image: Uri, onProgress: (Int) -> Unit): String {
        val url = storage.uploadJpeg("profile_images/${profile.uid}/avatar.jpg", image, maxDimension = 512, onProgress = onProgress)
        val data = mapOf("ProfilePic" to url, "ProfilePicUpdatedAt" to Timestamp.now())
        firestore.collection(Fs.USERS).document(profile.uid).set(data, SetOptions.merge()).await()
        mirrorDoc(profile.uid, profile.role).set(mapOf("ProfilePic" to url), SetOptions.merge())
        auth.currentUser?.updateProfile(userProfileChangeRequest { photoUri = Uri.parse(url) })
        return url
    }

    private fun mirrorDoc(uid: String, role: Role): DocumentReference = when (role) {
        Role.PHARMACIST -> firestore.collection(Fs.MEDICAL_STORES).document(uid)
        Role.SUPPLIER -> firestore.collection(Fs.SUPPLIERS_DATA).document(uid)
    }

    private fun mirrorData(profile: UserProfile): Map<String, Any> = when (profile.role) {
        Role.PHARMACIST -> mapOf(
            "Uid" to profile.uid,
            "Uname" to profile.name,
            "ShopName" to profile.businessName,
            "Address" to profile.address,
            "Phone" to profile.phone,
        )
        Role.SUPPLIER -> mapOf(
            "Uid" to profile.uid,
            "Uname" to profile.name,
            "Name" to profile.businessName,
            "Address" to profile.address,
            "Phone" to profile.phone,
            "Email" to profile.email,
        )
    }

    private companion object {
        const val TAG = "UserRepository"
    }
}
