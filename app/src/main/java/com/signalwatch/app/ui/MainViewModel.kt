package com.signalwatch.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signalwatch.app.data.api.ApiException
import com.signalwatch.app.data.quota.QuotaTracker
import com.signalwatch.app.data.repo.YouTubeRepository
import com.signalwatch.app.data.settings.Settings
import com.signalwatch.app.data.settings.SettingsRepository
import com.signalwatch.app.data.settings.TrustLevel
import com.signalwatch.app.service.WatchEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HandleStatus {
    data object Idle : HandleStatus
    data object Resolving : HandleStatus
    data class Resolved(val title: String) : HandleStatus
    data class Failed(val message: String) : HandleStatus
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val engine: WatchEngine,
    private val settingsRepo: SettingsRepository,
    private val repo: YouTubeRepository,
    quota: QuotaTracker,
) : ViewModel() {

    val state = engine.state
    val chat = engine.chat
    val latestSignal = engine.latestSignal
    val settings: StateFlow<Settings?> = settingsRepo.current
    val quotaUsed: StateFlow<Int> = quota.usedToday.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Video ID -> videos.list status.embeddable, for streams checked this session. */
    private val _embeddable = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val embeddable: StateFlow<Map<String, Boolean>> = _embeddable.asStateFlow()
    private val embedChecked = mutableSetOf<String>()

    private val _handleStatus = MutableStateFlow<HandleStatus>(HandleStatus.Idle)
    val handleStatus: StateFlow<HandleStatus> = _handleStatus.asStateFlow()

    /** Resolve @handle -> channel ID once (channels.list?forHandle, 1 unit) and cache it. */
    fun saveHandle(input: String) {
        val handle = normalizeHandle(input) ?: run {
            _handleStatus.value = HandleStatus.Failed("Enter a handle like @channelname")
            return
        }
        _handleStatus.value = HandleStatus.Resolving
        viewModelScope.launch {
            _handleStatus.value = try {
                val channel = repo.resolveHandle(handle)
                settingsRepo.setChannel(handle, channel.id, channel.title, channel.thumbUrl, channel.uploadsPlaylistId)
                engine.wakeUp()
                HandleStatus.Resolved(channel.title)
            } catch (e: ApiException) {
                HandleStatus.Failed(e.message ?: "Couldn't resolve $handle")
            } catch (e: Exception) {
                HandleStatus.Failed("Network error: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Once per stream: log whether the owner allows embedding, so the player can fall back early if not. */
    fun checkEmbed(videoId: String) {
        if (!embedChecked.add(videoId)) return
        viewModelScope.launch {
            try {
                val s = repo.embedStatus(videoId)
                Log.i(
                    PLAYER_TAG,
                    "videos.list $videoId: status.embeddable=${s.embeddable}, " +
                        "regionRestriction allowed=${s.regionAllowed} blocked=${s.regionBlocked}",
                )
                s.embeddable?.let { e -> _embeddable.value += videoId to e }
            } catch (e: Exception) {
                Log.w(PLAYER_TAG, "videos.list $videoId status check failed", e)
            }
        }
    }

    fun setGoLiveAlert(on: Boolean) = launch { settingsRepo.setGoLiveAlert(on) }
    fun setSignalAlerts(on: Boolean) = launch { settingsRepo.setSignalAlerts(on) }
    fun setTrust(level: TrustLevel) = launch { settingsRepo.setTrust(level) }
    fun setInterval(seconds: Int) = launch { settingsRepo.setInterval(seconds); engine.wakeUp() }
    fun setAlertSound(uri: String?, title: String?) = launch { settingsRepo.setAlertSound(uri, title) }
    fun setSimulate(on: Boolean) = launch { settingsRepo.setSimulate(on); engine.wakeUp() }
    fun sendTestAlert() = engine.sendTestAlert()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        const val PLAYER_TAG = "SignalWatchPlayer"
    }

    private fun normalizeHandle(input: String): String? {
        val trimmed = input.trim().substringAfterLast("youtube.com/").substringBefore('/').substringBefore('?')
        val bare = trimmed.removePrefix("@")
        if (bare.isBlank() || bare.any { it.isWhitespace() }) return null
        return "@$bare"
    }
}
