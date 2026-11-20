package com.example.data.repository

import android.content.Context

class SeedTracker(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "pet_registry_seed_prefs",
        Context.MODE_PRIVATE
    )

    fun hasAutoSeeded(userId: String): Boolean =
        prefs.getBoolean("auto_seeded_$userId", false)

    fun markAutoSeeded(userId: String) {
        prefs.edit().putBoolean("auto_seeded_$userId", true).apply()
    }
}
