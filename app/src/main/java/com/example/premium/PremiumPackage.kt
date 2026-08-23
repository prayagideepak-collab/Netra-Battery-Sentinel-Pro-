package com.example.premium

import android.content.Context

/**
 * Netra Token Economy and Subscriptions
 * Made with ❤️ by Prayagi Ji
 */

object TokenEngine {
    fun getTokens(context: Context): Int {
        val prefs = context.getSharedPreferences("netra_rewards_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("battery_tokens", 150) // Default starting tokens
    }

    fun earnTokensForHealthyHabit(context: Context, amount: Int) {
        val prefs = context.getSharedPreferences("netra_rewards_prefs", Context.MODE_PRIVATE)
        val current = prefs.getInt("battery_tokens", 150)
        prefs.edit().putInt("battery_tokens", current + amount).apply()
    }
}

object PremiumManager {
    fun isPremiumUser(context: Context): Boolean {
        return false // Freemium structure
    }
}

object RewardsTracker {
    fun claimDailyCheckIn(context: Context): Int {
        TokenEngine.earnTokensForHealthyHabit(context, 10)
        return 10
    }
}

object CashbackEngine {
    fun calculateAvailableCashback(tokens: Int): Double {
        return tokens * 0.05 // e.g. 5 paise per token
    }
}

object SubscriptionsManager {
    fun getAvailableTiers(): List<String> {
        return listOf("Free Ad-supported", "Pro Battery Sentinel Month Pass", "Netra Lifetime Guardian")
    }
}
