package com.orbit.app.data.local

import android.content.Context
import androidx.core.content.edit

/** Learned-rule proposals the user answered "Not now" (review N016); they are not offered again. */
interface DeclinedRuleMemory {
    fun isDeclined(key: String): Boolean
    fun decline(key: String)
    fun clear()

    object None : DeclinedRuleMemory {
        override fun isDeclined(key: String) = false
        override fun decline(key: String) = Unit
        override fun clear() = Unit
    }
}

class SharedPreferencesDeclinedRuleMemory(context: Context) : DeclinedRuleMemory {
    private val preferences = context.applicationContext
        .getSharedPreferences("tallele_declined_rules", Context.MODE_PRIVATE)

    override fun isDeclined(key: String): Boolean = preferences.getBoolean(key, false)

    override fun decline(key: String) {
        preferences.edit { putBoolean(key, true) }
    }

    override fun clear() {
        preferences.edit { clear() }
    }
}
