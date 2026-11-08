package com.example.jewelrymanagement.utils

import android.content.Context

/**
 * Compare list (max [MAX_ITEMS] product ids) kept per user in SharedPreferences, so it survives
 * navigation and app restarts. It is UI state only, not business data, hence not stored in SQLite.
 */
class CompareManager(context: Context) {

    enum class AddResult { ADDED, ALREADY_ADDED, FULL }

    companion object {
        const val MAX_ITEMS = 3
        private const val PREFS = "compare_prefs"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(userId: Long) = "compare_ids_$userId"

    fun getIds(userId: Long): List<Long> =
        prefs.getString(key(userId), "").orEmpty()
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .distinct()
            .take(MAX_ITEMS)

    fun contains(userId: Long, productId: Long): Boolean = productId in getIds(userId)

    fun add(userId: Long, productId: Long): AddResult {
        val ids = getIds(userId)
        if (productId in ids) return AddResult.ALREADY_ADDED
        if (ids.size >= MAX_ITEMS) return AddResult.FULL
        save(userId, ids + productId)
        return AddResult.ADDED
    }

    fun remove(userId: Long, productId: Long) = save(userId, getIds(userId) - productId)

    /** Drops ids of products that no longer exist. */
    fun retainOnly(userId: Long, existingIds: Collection<Long>) {
        val ids = getIds(userId)
        val kept = ids.filter { it in existingIds }
        if (kept.size != ids.size) save(userId, kept)
    }

    fun clear(userId: Long) {
        prefs.edit().remove(key(userId)).apply()
    }

    private fun save(userId: Long, ids: List<Long>) {
        prefs.edit().putString(key(userId), ids.joinToString(",")).apply()
    }
}
