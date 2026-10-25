package com.signalwatch.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.signalwatch.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

enum class TrustLevel(val label: String) {
    OWNER_AND_MODS("Owner + mods"),
    OWNER_ONLY("Owner only"),
}

data class Settings(
    val handle: String = "",
    val channelId: String? = null,
    /** The channel's uploads playlist ("UU..."), cached alongside [channelId]. */
    val uploadsPlaylistId: String? = null,
    val channelTitle: String? = null,
    val channelThumbUrl: String? = null,
    val goLiveAlert: Boolean = true,
    val signalAlerts: Boolean = true,
    val intervalSec: Int = DEFAULT_INTERVAL_SEC,
    val trust: TrustLevel = TrustLevel.OWNER_AND_MODS,
    val alertSoundUri: String? = null,
    val alertSoundTitle: String? = null,
    val simulate: Boolean = false,
    val lastNotifiedLiveId: String? = null,
) {
    val displayName: String get() = channelTitle ?: handle.ifBlank { "Channel" }

    companion object {
        const val DEFAULT_INTERVAL_SEC = 60
        const val MIN_INTERVAL_SEC = 30
        const val MAX_INTERVAL_SEC = 300
    }
}

@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    @ApplicationScope scope: CoroutineScope,
) {
    val settings: Flow<Settings> = store.data
        .catch { emit(emptyPreferences()) }
        .map { it.toSettings() }

    /** Null until DataStore has loaded once. */
    val current: StateFlow<Settings?> = settings.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun snapshot(): Settings = current.value ?: settings.first()

    suspend fun setChannel(handle: String, id: String, title: String, thumbUrl: String?, uploadsPlaylistId: String?) = edit {
        it[HANDLE] = handle
        it[CHANNEL_ID] = id
        it[CHANNEL_TITLE] = title
        if (thumbUrl != null) it[CHANNEL_THUMB] = thumbUrl else it.remove(CHANNEL_THUMB)
        if (uploadsPlaylistId != null) it[UPLOADS_ID] = uploadsPlaylistId else it.remove(UPLOADS_ID)
    }

    /** For channels saved before the uploads playlist was cached. Ignored if the channel changed meanwhile. */
    suspend fun setUploadsPlaylist(channelId: String, uploadsPlaylistId: String) = edit {
        if (it[CHANNEL_ID] == channelId) it[UPLOADS_ID] = uploadsPlaylistId
    }

    suspend fun setGoLiveAlert(on: Boolean) = edit { it[GO_LIVE] = on }
    suspend fun setSignalAlerts(on: Boolean) = edit { it[SIGNAL_ALERTS] = on }
    suspend fun setTrust(level: TrustLevel) = edit { it[TRUST] = level.name }
    suspend fun setSimulate(on: Boolean) = edit { it[SIMULATE] = on }
    suspend fun setLastNotifiedLive(videoId: String) = edit { it[LAST_LIVE_ID] = videoId }

    suspend fun setInterval(seconds: Int) = edit {
        it[INTERVAL] = seconds.coerceIn(Settings.MIN_INTERVAL_SEC, Settings.MAX_INTERVAL_SEC)
    }

    suspend fun setAlertSound(uri: String?, title: String?) = edit {
        if (uri != null) it[SOUND_URI] = uri else it.remove(SOUND_URI)
        if (title != null) it[SOUND_TITLE] = title else it.remove(SOUND_TITLE)
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    private fun Preferences.toSettings() = Settings(
        handle = this[HANDLE].orEmpty(),
        channelId = this[CHANNEL_ID],
        uploadsPlaylistId = this[UPLOADS_ID],
        channelTitle = this[CHANNEL_TITLE],
        channelThumbUrl = this[CHANNEL_THUMB],
        goLiveAlert = this[GO_LIVE] ?: true,
        signalAlerts = this[SIGNAL_ALERTS] ?: true,
        intervalSec = this[INTERVAL] ?: Settings.DEFAULT_INTERVAL_SEC,
        trust = this[TRUST]?.let { name -> TrustLevel.entries.firstOrNull { it.name == name } }
            ?: TrustLevel.OWNER_AND_MODS,
        alertSoundUri = this[SOUND_URI],
        alertSoundTitle = this[SOUND_TITLE],
        simulate = this[SIMULATE] ?: false,
        lastNotifiedLiveId = this[LAST_LIVE_ID],
    )

    private companion object {
        val HANDLE = stringPreferencesKey("handle")
        val CHANNEL_ID = stringPreferencesKey("channel_id")
        val UPLOADS_ID = stringPreferencesKey("uploads_playlist_id")
        val CHANNEL_TITLE = stringPreferencesKey("channel_title")
        val CHANNEL_THUMB = stringPreferencesKey("channel_thumb")
        val GO_LIVE = booleanPreferencesKey("go_live_alert")
        val SIGNAL_ALERTS = booleanPreferencesKey("signal_alerts")
        val INTERVAL = intPreferencesKey("interval_sec")
        val TRUST = stringPreferencesKey("trust")
        val SOUND_URI = stringPreferencesKey("alert_sound_uri")
        val SOUND_TITLE = stringPreferencesKey("alert_sound_title")
        val SIMULATE = booleanPreferencesKey("simulate_live")
        val LAST_LIVE_ID = stringPreferencesKey("last_notified_live_id")
    }
}
