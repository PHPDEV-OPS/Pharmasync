package com.example.pharmasync.data.repository

import android.net.Uri
import com.example.pharmasync.data.local.ProfileDao
import com.example.pharmasync.data.local.ProfileEntity
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.data.model.UserProfile
import com.example.pharmasync.data.remote.ApiException
import com.example.pharmasync.data.remote.LocationRequest
import com.example.pharmasync.data.remote.PharmasyncApi
import com.example.pharmasync.data.remote.PhotoRequest
import com.example.pharmasync.data.remote.SaveProfileRequest
import com.example.pharmasync.data.remote.apiCall
import com.example.pharmasync.data.remote.toProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The signed-in user's profile, stored in Neon Postgres and cached locally for offline use. */
class UserRepository(
    private val api: PharmasyncApi,
    private val dao: ProfileDao,
    private val storage: StorageRepository,
    private val location: LocationRepository,
) {
    /** Cached profile; updated by [refresh] and by every profile write. */
    fun observe(uid: String): Flow<UserProfile?> = dao.observe(uid).map { it?.toModel() }

    /** Returns null when the account has no profile yet (it still needs to be completed). */
    suspend fun refresh(): UserProfile? = try {
        apiCall { api.me() }.toProfile().also { cache(it) }
    } catch (e: ApiException) {
        if (e.isProfileMissing) null else throw e
    }

    suspend fun createOrUpdate(
        role: Role?,
        name: String,
        businessName: String,
        address: String,
        phone: String,
    ): UserProfile {
        // Geocode the address so the pharmacy appears on the public map.
        val point = location.geocode(address)
        val profile = apiCall {
            api.saveMe(
                SaveProfileRequest(
                    role = role?.apiValue,
                    name = name,
                    businessName = businessName,
                    address = address,
                    phone = phone,
                    latitude = point?.latitude,
                    longitude = point?.longitude,
                )
            )
        }.toProfile()
        cache(profile)
        return profile
    }

    suspend fun saveLocation(point: GeoPoint, address: String): UserProfile {
        val profile = apiCall { api.saveLocation(LocationRequest(point.latitude, point.longitude, address)) }.toProfile()
        cache(profile)
        return profile
    }

    suspend fun uploadPhoto(image: Uri, onProgress: (Int) -> Unit): UserProfile {
        val uploaded = storage.upload(ImageKind.AVATAR, image, onProgress)
        val url = uploaded.url ?: error("Avatar upload returned no public URL")
        val profile = apiCall { api.savePhoto(PhotoRequest(url)) }.toProfile()
        cache(profile)
        return profile
    }

    private suspend fun cache(profile: UserProfile) = dao.upsert(ProfileEntity.from(profile))
}
