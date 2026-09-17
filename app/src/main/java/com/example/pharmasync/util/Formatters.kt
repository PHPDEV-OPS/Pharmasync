package com.example.pharmasync.util

import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val currency: NumberFormat get() = NumberFormat.getCurrencyInstance(Locale.getDefault())

    fun money(amount: Double): String = currency.format(amount)

    fun date(millis: Long): String =
        if (millis <= 0) "" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

    fun dateTime(millis: Long): String =
        if (millis <= 0) "" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

    fun initials(name: String): String = name.split(' ', '-', '.')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

    /** "PAIN RELIEF" -> "Pain Relief" */
    fun titleCase(text: String): String = text.lowercase(Locale.getDefault())
        .split(' ')
        .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.getDefault()) } }
}
