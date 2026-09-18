package com.example.pharmasync.util

import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.example.pharmasync.R

/** Loads images stored in Neon Object Storage (public URLs, or signed URLs for invoices). */
object ImageLoader {

    fun load(
        view: ImageView,
        url: String?,
        @DrawableRes placeholder: Int = R.drawable.img_medicine_placeholder,
        version: Long = 0L,
    ) {
        if (url.isNullOrBlank()) {
            Glide.with(view).clear(view)
            view.setImageResource(placeholder)
            return
        }
        Glide.with(view)
            .load(url)
            .signature(ObjectKey(version))
            .placeholder(placeholder)
            .error(placeholder)
            .centerCrop()
            .into(view)
    }
}
