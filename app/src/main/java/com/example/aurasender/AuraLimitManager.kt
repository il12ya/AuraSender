package com.example.aurasender

import android.content.Context
import android.content.SharedPreferences
import java.util.*
import kotlin.random.Random

class AuraLimitManager(private val context: Context) {
    private val sharedPref: SharedPreferences = context.getSharedPreferences("AuraLimits", Context.MODE_PRIVATE)

    fun getDailyLimit(): Int = sharedPref.getInt("daily_limit", 20)
    fun getMinDelay(): Int = sharedPref.getInt("min_delay", 100)
    fun getMaxDelay(): Int = sharedPref.getInt("max_delay", 700)

    private fun getTodayString(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH) + 1}-${calendar.get(Calendar.DAY_OF_MONTH)}"
    }

    fun canSendAura(username: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastClickTime = sharedPref.getLong("last_click_$username", 0)
        val clickCount = sharedPref.getInt("click_count_$username", 0)

        if (currentTime - lastClickTime > 24 * 60 * 60 * 1000) {
            with(sharedPref.edit()) {
                putInt("click_count_$username", 1)
                putLong("last_click_$username", currentTime)
                apply()
            }
            return true
        }

        if (clickCount < 5) {
            with(sharedPref.edit()) {
                putInt("click_count_$username", clickCount + 1)
                putLong("last_click_$username", currentTime)
                apply()
            }
            return true
        }
        return false
    }

    fun canSendToday(): Boolean {
        val today = getTodayString()
        val todayClicks = sharedPref.getInt("today_clicks_$today", 0)
        return todayClicks < getDailyLimit()
    }

    fun incrementDailyCount() {
        val today = getTodayString()
        val todayClicks = sharedPref.getInt("today_clicks_$today", 0)
        with(sharedPref.edit()) {
            putInt("today_clicks_$today", todayClicks + 1)
            apply()
        }
    }

    fun getRandomDelay(): Long {
        return Random.nextLong(getMinDelay().toLong(), getMaxDelay().toLong())
    }
}
