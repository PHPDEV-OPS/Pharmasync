package com.example.pharmasync.util

import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.example.pharmasync.R
import com.google.firebase.storage.FirebaseStorage
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads images that are either https download URLs or legacy Firebase Storage paths.
 * Legacy paths are resolved to download URLs once and cached.
 */
object ImageLoader {
    private val resolvedUrls = ConcurrentHashMap<String, String>()

    fun load(
        view: ImageView,
        ref: String?,
        @DrawableRes placeholder: Int = R.drawable.img_medicine_placeholder,
        version: Long = 0L,
    ) {
        view.setTag(R.id.tag_image_ref, ref)
        if (ref.isNullOrBlank()) {
            Glide.with(view).clear(view)
            view.setImageResource(placeholder)
            return
        }
        val url = if (ref.startsWith("http")) ref else resolvedUrls[ref]
        if (url != null) {
            Glide.with(view)
                .load(url)
                .signature(ObjectKey(version))
                .placeholder(placeholder)
                .error(placeholder)
                .centerCrop()
                .into(view)
            return
        }
        view.setImageResource(placeholder)
        FirebaseStorage.getInstance().reference.child(ref).downloadUrl
            .addOnSuccessListener { uri ->
                resolvedUrls[ref] = uri.toString()
                // The view may have been recycled for another item meanwhile.
                if (view.getTag(R.id.tag_image_ref) == ref && view.isAttachedToWindow) {
                    load(view, ref, placeholder, version)
                }
            }
    }
}
