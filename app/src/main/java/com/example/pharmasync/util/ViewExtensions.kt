package com.example.pharmasync.util

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.pharmasync.R
import com.example.pharmasync.data.model.OrderStatus
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

fun View.showMessage(message: UiMessage, duration: Int = Snackbar.LENGTH_LONG) {
    Snackbar.make(this, message.resolve(context), duration).show()
}

fun View.showMessage(text: String, duration: Int = Snackbar.LENGTH_LONG) {
    Snackbar.make(this, text, duration).show()
}

/** Collects [flow] while the owner is at least STARTED. */
fun <T> LifecycleOwner.collectWhileStarted(flow: Flow<T>, action: suspend (T) -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { action(it) }
        }
    }
}

fun <T> Fragment.collectWhileViewStarted(flow: Flow<T>, action: suspend (T) -> Unit) =
    viewLifecycleOwner.collectWhileStarted(flow, action)

fun TextView.setPillColors(@ColorRes foreground: Int, @ColorRes background: Int) {
    setTextColor(ContextCompat.getColor(context, foreground))
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, background))
}

fun TextView.bindStatus(status: OrderStatus) {
    val (label, fg, bg) = when (status) {
        OrderStatus.PENDING -> Triple(R.string.status_pending, R.color.status_pending, R.color.status_pending_bg)
        OrderStatus.ACCEPTED -> Triple(R.string.status_accepted, R.color.status_accepted, R.color.status_accepted_bg)
        OrderStatus.DISPATCHED -> Triple(R.string.status_dispatched, R.color.status_dispatched, R.color.status_dispatched_bg)
        OrderStatus.DELIVERED -> Triple(R.string.status_delivered, R.color.status_delivered, R.color.status_delivered_bg)
        OrderStatus.DECLINED -> Triple(R.string.status_declined, R.color.status_declined, R.color.status_declined_bg)
        OrderStatus.CANCELLED -> Triple(R.string.status_cancelled, R.color.status_neutral, R.color.status_neutral_bg)
    }
    setText(label)
    setPillColors(fg, bg)
}

val TextInputLayout.textValue: String get() = editText?.text?.toString()?.trim().orEmpty()

/** Sets or clears the field error; returns true when the field is valid. */
fun TextInputLayout.validate(message: String?): Boolean {
    error = message
    isErrorEnabled = message != null
    return message == null
}

fun View.visibleIf(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}
