package com.example.pharmasync.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.pharmasync.R
import com.example.pharmasync.data.model.Medicine
import com.example.pharmasync.data.model.Order
import com.example.pharmasync.ui.splash.StartActivity

object Notifications {
    private const val CHANNEL_STOCK = "stock_alerts"
    private const val CHANNEL_ORDERS = "order_updates"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STOCK, context.getString(R.string.channel_stock_alerts), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ORDERS, context.getString(R.string.channel_orders), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun lowStock(context: Context, medicine: Medicine) = post(
        context,
        channel = CHANNEL_STOCK,
        id = "low:${medicine.id}".hashCode(),
        title = context.getString(R.string.notif_low_stock_title, medicine.name),
        body = context.getString(R.string.notif_low_stock_body, medicine.stock, medicine.lowStockThreshold),
    )

    fun newOrder(context: Context, order: Order) = post(
        context,
        channel = CHANNEL_ORDERS,
        id = "order:${order.id}".hashCode(),
        title = context.getString(R.string.notif_new_order_title, order.pharmacistName),
        body = context.getString(R.string.notif_new_order_body, order.quantity, order.medicineName),
    )

    fun orderUpdate(context: Context, order: Order, statusLabel: String) = post(
        context,
        channel = CHANNEL_ORDERS,
        id = "order:${order.id}".hashCode(),
        title = context.getString(R.string.notif_order_update_title, statusLabel.lowercase()),
        body = context.getString(R.string.notif_order_update_body, order.medicineName, order.supplierName),
    )

    private fun post(context: Context, channel: String, id: Int, title: String, body: String) {
        if (!canPost(context)) return
        val intent = Intent(context, StartActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_medical_services)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
}
