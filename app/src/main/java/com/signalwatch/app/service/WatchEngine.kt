package com.signalwatch.app.service

import com.signalwatch.app.data.api.ApiException
import com.signalwatch.app.data.quota.QuotaTracker
import com.signalwatch.app.data.repo.CaughtSignal
import com.signalwatch.app.data.repo.ChatItem
import com.signalwatch.app.data.repo.LiveVideo
import com.signalwatch.app.data.repo.RawMessage
import com.signalwatch.app.data.repo.Role
import com.signalwatch.app.data.repo.YouTubeRepository
import com.signalwatch.app.data.settings.Settings
import com.signalwatch.app.data.settings.SettingsRepository
import com.signalwatch.app.data.settings.TrustLevel
import com.signalwatch.app.debug.DebugTools
import com.signalwatch.app.di.ApplicationScope
import com.signalwatch.app.notify.Notifier
import com.signalwatch.parser.SignalDeduplicator
import com.signalwatch.parser.SignalParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

sealed interface WatchState {
    data class Offline(val lastCheck: Instant? = null, val message: String? = null) : WatchState
    data class Live(val video: LiveVideo) : WatchState
}

/**
 * The one polling loop. Offline it checks for a live stream every N seconds (RSS + videos.list).
 * Once live it stops that and polls the live chat instead, until the stream ends.
 *
 * It runs while anyone holds it: the foreground service (alerts on) and/or the visible UI.
 */
@Singleton
class WatchEngine @Inject constructor(
    private val repo: YouTubeRepository,
    private val settingsRepo: SettingsRepository,
    private val notifier: Notifier,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val parser = SignalParser()
    private val dedup = SignalDeduplicator()

    private val _state = MutableStateFlow<WatchState>(WatchState.Offline())
    val state: StateFlow<WatchState> = _state.asStateFlow()

    private val _chat = MutableStateFlow<List<ChatItem>>(emptyList())
    val chat: StateFlow<List<ChatItem>> = _chat.asStateFlow()

    private val _latestSignal = MutableStateFlow<CaughtSignal?>(null)
    val latestSignal: StateFlow<CaughtSignal?> = _latestSignal.asStateFlow()

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private var holders = 0
    private var sessionVideoId: String? = null

    @Synchronized
    fun acquire() {
        holders++
        if (job?.isActive != true) job = scope.launch { runLoop() }
    }

    @Synchronized
    fun release() {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders == 0) {
            job?.cancel()
            job = null
        }
    }

    /** Cut the current offline wait short (settings changed). Never shortens chat polling. */
    fun wakeUp() {
        wake.trySend(Unit)
    }

    /** Fire a fake owner signal through parser -> de-dup -> notification -> Alert screen. */
    fun sendTestAlert() {
        scope.launch {
            val s = settingsRepo.snapshot()
            val p = 2360 + Random.nextInt(0, 40) + Random.nextInt(0, 100) / 100.0
            fun f(v: Double) = String.format(Locale.US, "%.2f", v)
            val raw = RawMessage(
                id = "test-" + UUID.randomUUID(),
                author = s.displayName,
                isOwner = true,
                isModerator = false,
                text = "SELL XAUUSD @ ${f(p)} | SL ${f(p + 7)} | TP1 ${f(p - 8)} | TP2 ${f(p - 16)}",
                publishedAt = Instant.now(),
            )
            val item = toChatItem(raw, s)
            handleSignal(item, s, alert = true, force = true, isTest = true)
        }
    }

    private suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            val s = settingsRepo.snapshot()
            if (s.simulate && DebugTools.AVAILABLE) {
                runSimulation()
                continue
            }
            val channelId = s.channelId
            if (channelId == null) {
                _state.value = WatchState.Offline(message = "Set the channel handle in Settings")
                sleep(s.intervalSec * 1000L)
                continue
            }
            val previous = _state.value as? WatchState.Offline
            val live = try {
                repo.findLiveVideo(channelId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = WatchState.Offline(previous?.lastCheck, describe(e))
                if (e is ApiException && e.isQuotaExceeded) sleep(QuotaTracker.millisUntilReset())
                else sleep(s.intervalSec * 1000L)
                continue
            }
            if (live == null) {
                _state.value = WatchState.Offline(lastCheck = Instant.now())
                sleep(s.intervalSec * 1000L)
                continue
            }
            onWentLive(live, s)
            runLiveSession(live)
            _state.value = WatchState.Offline(lastCheck = Instant.now())
        }
    }

    private suspend fun onWentLive(live: LiveVideo, s: Settings) {
        startSession(live)
        if (s.goLiveAlert && s.lastNotifiedLiveId != live.videoId) {
            notifier.showLive(s.displayName)
            settingsRepo.setLastNotifiedLive(live.videoId)
        }
    }

    private fun startSession(live: LiveVideo) {
        if (sessionVideoId != live.videoId) {
            sessionVideoId = live.videoId
            _chat.value = emptyList()
            _latestSignal.value = null
        }
        _state.value = WatchState.Live(live)
    }

    private suspend fun runLiveSession(live: LiveVideo) {
        val chatId = live.liveChatId ?: return watchUntilEnded(live)
        var pageToken: String? = null
        var firstPage = true
        var failures = 0
        while (currentCoroutineContext().isActive) {
            val page = try {
                repo.chatPage(chatId, pageToken)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                when {
                    e.isChatGone -> return
                    e.isQuotaExceeded -> {
                        delay(QuotaTracker.millisUntilReset())
                        continue
                    }
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
            if (page == null) {
                failures++
                if (failures >= 3 && !stillLive(live)) return
                delay((15_000L * failures).coerceAtMost(60_000L))
                continue
            }
            failures = 0
            pageToken = page.nextPageToken
            ingest(page.messages, backlog = firstPage)
            firstPage = false
            if (page.ended) return
            // Respect the server's interval, but never faster than 8 s, to protect the daily quota.
            delay(page.pollingIntervalMillis.coerceAtLeast(MIN_CHAT_POLL_MS))
        }
    }

    /** Stream without a live chat: just watch for it to end. */
    private suspend fun watchUntilEnded(live: LiveVideo) {
        while (currentCoroutineContext().isActive) {
            delay(settingsRepo.snapshot().intervalSec * 1000L)
            if (!stillLive(live)) return
        }
    }

    private suspend fun stillLive(live: LiveVideo): Boolean = try {
        repo.video(live.videoId)?.isLive == true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        true // can't tell; keep trying
    }

    private suspend fun runSimulation() {
        val s = settingsRepo.snapshot()
        val live = LiveVideo(
            videoId = DebugTools.SAMPLE_VIDEO_ID,
            title = "Simulated stream",
            startedAt = Instant.now(),
            liveChatId = null,
            isLive = true,
            simulated = true,
        )
        sessionVideoId = null
        startSession(live)
        if (s.goLiveAlert) notifier.showLive(s.displayName)
        var round = 0
        while (currentCoroutineContext().isActive) {
            for (m in DebugTools.script(round, s.displayName)) {
                delay(m.delayMs)
                if (settingsRepo.snapshot().simulate.not()) {
                    _state.value = WatchState.Offline(lastCheck = Instant.now())
                    return
                }
                ingest(
                    listOf(RawMessage("sim-" + UUID.randomUUID(), m.author, m.isOwner, m.isModerator, m.text, Instant.now())),
                    backlog = false,
                )
            }
            round++
        }
    }

    private suspend fun ingest(messages: List<RawMessage>, backlog: Boolean) {
        if (messages.isEmpty()) return
        val s = settingsRepo.snapshot()
        val known = _chat.value.mapTo(HashSet()) { it.id }
        val fresh = messages.filter { it.id !in known }.map { toChatItem(it, s) }
        if (fresh.isEmpty()) return
        _chat.update { (it + fresh).takeLast(MAX_CHAT_ITEMS) }
        val now = Instant.now()
        for (item in fresh) {
            if (item.signal == null) continue
            // The first page is history; only alert for what was said in the last couple of minutes.
            val recent = Duration.between(item.publishedAt, now) < BACKLOG_ALERT_WINDOW
            handleSignal(item, s, alert = !backlog || recent)
        }
    }

    private fun toChatItem(raw: RawMessage, s: Settings): ChatItem {
        val role = when {
            raw.isOwner -> Role.OWNER
            raw.isModerator -> Role.MODERATOR
            else -> Role.VIEWER
        }
        val trusted = role == Role.OWNER || (role == Role.MODERATOR && s.trust == TrustLevel.OWNER_AND_MODS)
        return ChatItem(
            id = raw.id,
            author = raw.author,
            role = role,
            text = raw.text,
            publishedAt = raw.publishedAt,
            signal = if (trusted) parser.parse(raw.text) else null,
        )
    }

    private fun handleSignal(item: ChatItem, s: Settings, alert: Boolean, force: Boolean = false, isTest: Boolean = false) {
        val signal = item.signal ?: return
        val caught = CaughtSignal(signal, item.author, item.role, item.publishedAt, item.id, isTest)
        val ring = alert && (force || s.signalAlerts)
        when (val result = dedup.offer(signal, item.publishedAt.toEpochMilli())) {
            is SignalDeduplicator.Result.New -> {
                _latestSignal.value = caught
                if (ring) notifier.showSignal(caught, s.alertSoundUri)
            }
            is SignalDeduplicator.Result.Improved -> {
                // Same call reposted with more levels: update what's shown, don't ring again.
                val upgraded = caught.copy(signal = result.signal)
                _latestSignal.value = upgraded
                if (notifier.activeAlert.value != null) notifier.showSignal(upgraded, s.alertSoundUri, silentUpdate = true)
            }
            is SignalDeduplicator.Result.Duplicate -> Unit
        }
    }

    private suspend fun sleep(ms: Long) {
        withTimeoutOrNull(ms) { wake.receive() }
    }

    private fun describe(e: Exception): String = when {
        e is ApiException && e.isQuotaExceeded -> "Daily API quota used up. Resumes after midnight Pacific."
        e is ApiException -> e.message ?: "YouTube API error"
        else -> "Can't reach YouTube: ${e.message ?: e.javaClass.simpleName}"
    }

    private companion object {
        const val MIN_CHAT_POLL_MS = 8_000L
        const val MAX_CHAT_ITEMS = 500
        val BACKLOG_ALERT_WINDOW: Duration = Duration.ofMinutes(2)
    }
}
