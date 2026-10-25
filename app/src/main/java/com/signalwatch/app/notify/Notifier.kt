package com.signalwatch.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.signalwatch.app.MainActivity
import com.signalwatch.app.R
import com.signalwatch.app.data.repo.CaughtSignal
import com.signalwatch.app.ui.alert.AlertActivity
import com.signalwatch.parser.Direction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    private val _activeAlert = MutableStateFlow<CaughtSignal?>(null)
    /** The signal on the Alert screen; cleared by "Open stream" / "Dismiss". */
    val activeAlert: StateFlow<CaughtSignal?> = _activeAlert.asStateFlow()

    fun createBaseChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WATCH, "Watching", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The small persistent notification while Signal Watch is polling"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LIVE, "Go-live alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When the channel starts a live stream"
            }
        )
    }

    /**
     * The "Signals" channel. A channel's sound can't change after creation, so the id carries a
     * hash of the chosen sound and older versions are deleted when the sound changes.
     */
    private fun ensureSignalChannel(soundUri: String?): String {
        val sound = soundUri?.let(Uri::parse) ?: defaultAlarmUri()
        val id = CHANNEL_SIGNALS_PREFIX + Integer.toHexString(sound.toString().hashCode())
        if (manager.getNotificationChannel(id) == null) {
            manager.notificationChannels
                .filter { it.id.startsWith(CHANNEL_SIGNALS_PREFIX) && it.id != id }
                .forEach { manager.deleteNotificationChannel(it.id) }
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            manager.createNotificationChannel(
                NotificationChannel(id, "Signals", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Loud alarm when the owner or a moderator posts a trade signal"
                    setSound(sound, attrs)
                    enableVibration(true)
                    vibrationPattern = VIBRATION
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                }
            )
        }
        return id
    }

    fun watchNotification(text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_stat_signal)
            .setContentTitle("Signal Watch")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(mainIntent(openLive = false))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    fun updateWatch(text: String) = post(ID_WATCH, watchNotification(text))

    fun showLive(channelName: String) {
        val n = NotificationCompat.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_signal)
            .setContentTitle("$channelName is live")
            .setContentText("Tap to watch and follow the chat")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(mainIntent(openLive = true))
            .build()
        post(ID_LIVE, n)
    }

    /**
     * Loud, repeating alarm with a full-screen Alert screen. Stops on Open/Dismiss, or after
     * 60 s via setTimeoutAfter (the Alert screen itself stays up).
     */
    fun showSignal(caught: CaughtSignal, soundUri: String?, silentUpdate: Boolean = false) {
        _activeAlert.value = caught
        val s = caught.signal
        val levels = listOfNotNull(
            s.entryLabel?.let { "Entry $it" },
            s.stopLoss?.let { "SL $it" },
            s.takeProfits.firstOrNull()?.let { "TP $it" },
        ).joinToString(" · ")
        val alertIntent = PendingIntent.getActivity(
            context, REQ_ALERT,
            Intent(context, AlertActivity::class.java)
                .putExtras(caught.toBundle())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissIntent = PendingIntent.getBroadcast(
            context, REQ_DISMISS,
            Intent(context, AlertActionReceiver::class.java).setAction(AlertActionReceiver.ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, ensureSignalChannel(soundUri))
            .setSmallIcon(R.drawable.ic_stat_signal)
            .setContentTitle("${if (s.direction == Direction.BUY) "BUY" else "SELL"} ${s.symbol}")
            .setContentText(levels.ifBlank { s.raw })
            .setStyle(NotificationCompat.BigTextStyle().bigText("$levels\n${caught.author} (${caught.role.label})"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(alertIntent, true)
            .setContentIntent(alertIntent)
            .addAction(0, "Open stream", mainIntent(openLive = true))
            .addAction(0, "Dismiss", dismissIntent)
            .setOnlyAlertOnce(silentUpdate)
            .setTimeoutAfter(MAX_ALARM_MS)
            .build()
        // Insistent = the channel sound and vibration loop until the notification goes away.
        n.flags = n.flags or Notification.FLAG_INSISTENT
        post(ID_SIGNAL, n)
    }

    /** Stop the sound but keep the Alert screen's content. */
    fun silenceSignal() {
        NotificationManagerCompat.from(context).cancel(ID_SIGNAL)
    }

    fun dismissSignal() {
        silenceSignal()
        _activeAlert.value = null
    }

    fun notificationsAllowed(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Android 14+ lets users revoke full-screen intents; before that it's always granted. */
    fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || manager.canUseFullScreenIntent()

    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun mainIntent(openLive: Boolean): PendingIntent = PendingIntent.getActivity(
        context, if (openLive) REQ_OPEN_LIVE else REQ_MAIN,
        MainActivity.intent(context, openLive),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun defaultAlarmUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    companion object {
        const val CHANNEL_WATCH = "watch"
        const val CHANNEL_LIVE = "live"
        const val CHANNEL_SIGNALS_PREFIX = "signals_"

        const val ID_WATCH = 1
        const val ID_LIVE = 2
        const val ID_SIGNAL = 3

        private const val REQ_MAIN = 10
        private const val REQ_OPEN_LIVE = 11
        private const val REQ_ALERT = 12
        private const val REQ_DISMISS = 13

        const val MAX_ALARM_MS = 60_000L
        private val VIBRATION = longArrayOf(0, 900, 250, 900, 250, 900, 600)
    }
}
