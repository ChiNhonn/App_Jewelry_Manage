package com.example.jewelrymanagement.utils

import java.text.NumberFormat
import java.util.Locale

object CurrencyUtils {
    fun formatVnd(amount: Double): String {
        val localeVN = Locale.forLanguageTag("vi-VN")
        val currencyFormatter = NumberFormat.getCurrencyInstance(localeVN)
        return try {
            currencyFormatter.format(amount)
        } catch (e: Exception) {
            String.format(Locale.getDefault(), "%,.0f đ", amount)
        }
    }
}
