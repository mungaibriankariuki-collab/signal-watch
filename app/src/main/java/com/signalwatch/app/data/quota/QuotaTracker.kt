package com.signalwatch.app.data.quota

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local estimate of today's YouTube Data API usage. Google resets the quota at midnight
 * Pacific time, so the counter does too.
 *
 * Costs from https://developers.google.com/youtube/v3/determine_quota_cost (checked 2026-10-25):
 * channels.list 1, videos.list 1, liveChatMessages.list 5. Default allowance 10,000 units/day.
 * (search.list now has its own 100-calls/day limit; this app never calls it.)
 */
@Singleton
class QuotaTracker @Inject constructor(private val store: DataStore<Preferences>) {

    val usedToday: Flow<Int> = store.data.map { prefs ->
        if (prefs[DAY] == today()) prefs[UNITS] ?: 0 else 0
    }

    suspend fun add(units: Int) {
        store.edit { prefs ->
            val day = today()
            if (prefs[DAY] != day) {
                prefs[DAY] = day
                prefs[UNITS] = 0
            }
            prefs[UNITS] = (prefs[UNITS] ?: 0) + units
        }
    }

    companion object {
        const val DAILY_LIMIT = 10_000
        const val COST_CHANNELS_LIST = 1
        const val COST_VIDEOS_LIST = 1
        const val COST_LIVE_CHAT_MESSAGES_LIST = 5

        val PACIFIC: ZoneId = ZoneId.of("America/Los_Angeles")

        private val DAY = stringPreferencesKey("quota_day")
        private val UNITS = intPreferencesKey("quota_units")

        fun today(): String = LocalDate.now(PACIFIC).toString()

        fun millisUntilReset(): Long {
            val now = ZonedDateTime.now(PACIFIC)
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay(PACIFIC)
            return Duration.between(now, midnight).toMillis().coerceAtLeast(60_000)
        }
    }
}
