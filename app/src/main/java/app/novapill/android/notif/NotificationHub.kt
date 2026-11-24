package app.novapill.android.notif

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import app.novapill.android.core.PeekEntry
import app.novapill.android.core.PeekQueue
import app.novapill.android.core.PeekTimer
import app.novapill.android.data.SettingsRepository
import app.novapill.android.settings.QueueBehavior
import app.novapill.android.system.SystemFlags
import app.novapill.android.util.AppInfoCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An action the notification itself offers (used for calls only, labelled with the app's own text). */
data class PeekAction(val title: String, val intent: PendingIntent)

/**
 * What the peek needs to draw. [summary] is already privacy-filtered: with the default settings it holds
 * a short title only, never the message body.
 */
data class PeekPayload(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val summary: String,
    val isCall: Boolean,
    val contentIntent: PendingIntent?,
    val answer: PendingIntent?,
    val decline: PendingIntent?,
    val actions: List<PeekAction>,
)

/**
 * Turns system notifications into short peeks. All calls happen on the main thread (the listener
 * delivers there). Nothing is persisted: the queue lives in memory and is dropped when the island stops.
 */
class NotificationHub(
    private val context: Context,
    private val settings: SettingsRepository,
    private val appInfo: AppInfoCache,
    private val system: SystemFlags,
) {
    private val queue = PeekQueue<PeekPayload>()
    private var timer: PeekTimer<PeekPayload>? = null

    private val _current = MutableStateFlow<PeekEntry<PeekPayload>?>(null)
    val current: StateFlow<PeekEntry<PeekPayload>?> = _current.asStateFlow()

    fun start(scope: CoroutineScope) {
        timer?.clear()
        queue.clear()
        timer = PeekTimer(scope, queue, { settings.settings.value.peekDurationSec * 1000L }) { _current.value = it }
    }

    fun stop() {
        timer?.clear()
        timer = null
        queue.clear()
        _current.value = null
    }

    /** Drops anything queued or visible (used by "clear cached previews"). */
    fun clearPreviews() {
        timer?.clear()
    }

    fun dismissCurrent() {
        if (_current.value != null) timer?.advance()
    }

    /** While the user touches the peek the countdown is held; releasing restarts it. */
    fun hold(isHeld: Boolean) {
        timer?.hold(isHeld)
    }

    fun onRemoved(sbn: StatusBarNotification) {
        timer?.remove(sbn.key)
    }

    fun onPosted(sbn: StatusBarNotification) {
        val t = timer ?: return
        if (!system.screenOn.value) return
        val s = settings.settings.value
        val n = sbn.notification ?: return
        val extras: Bundle = n.extras ?: Bundle()

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            ?.toString().orEmpty()
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val isMedia = template.endsWith("MediaStyle") || extras.containsKey(Notification.EXTRA_MEDIA_SESSION)

        // Calls: only an *incoming* call is a peek. An ongoing call notification would otherwise stick forever.
        val callType = if (extras.containsKey(EXTRA_CALL_TYPE)) extras.getInt(EXTRA_CALL_TYPE) else -1
        var category: String? = n.category
        val isCallCategory = category == Notification.CATEGORY_CALL
        val incoming = isCallCategory && (callType == CALL_TYPE_INCOMING || (callType == -1 && n.fullScreenIntent != null))
        if (isCallCategory && !incoming) category = "call_active"

        val facts = NotifFacts(
            packageName = sbn.packageName,
            category = category,
            isOngoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0,
            isGroupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            hasTitle = title.isNotBlank(),
            hasText = text.isNotBlank(),
            isMediaStyle = isMedia,
            isOwnApp = sbn.packageName == context.packageName,
        )
        val verdict = NotificationFilter.decide(
            facts,
            FilterSettings(
                peekEnabled = s.peekEnabled,
                mode = s.notifMode,
                allowedApps = s.allowedApps,
                mutedApps = s.mutedApps,
                mutedCategories = s.mutedCategories,
            ),
        )
        if (!verdict.show) {
            // The same notification may have changed (an answered call becomes ongoing): drop its peek.
            t.remove(sbn.key)
            return
        }

        val label = appInfo.label(sbn.packageName)
        val preview = PreviewBuilder.build(
            PreviewInputs(
                appLabel = label,
                title = title,
                text = text,
                category = n.category,
                keyguardLocked = system.isKeyguardLocked(),
                secret = n.visibility == Notification.VISIBILITY_SECRET,
                hideSensitive = s.hideSensitiveCategories,
                privacy = s.privacy,
            ),
        )

        val answer = if (incoming) pendingExtra(extras, EXTRA_ANSWER_INTENT) else null
        val decline = if (incoming) pendingExtra(extras, EXTRA_DECLINE_INTENT) else null
        val actions = if (incoming && answer == null && decline == null) {
            n.actions.orEmpty().mapNotNull { a ->
                val pi = a.actionIntent
                val name = a.title?.toString().orEmpty()
                if (pi != null && name.isNotBlank()) PeekAction(name.take(16), pi) else null
            }.take(2)
        } else {
            emptyList()
        }

        val payload = PeekPayload(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = preview.appName,
            summary = preview.summary,
            isCall = incoming,
            contentIntent = n.contentIntent,
            answer = answer,
            decline = decline,
            actions = actions,
        )
        t.offer(
            PeekEntry(sbn.key, high = verdict.high, sticky = verdict.sticky, payload = payload),
            latestOnly = s.queueBehavior == QueueBehavior.LATEST_ONLY,
        )
    }

    private fun pendingExtra(extras: Bundle, key: String): PendingIntent? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelable(key, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable<PendingIntent>(key)
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        // Notification.CallStyle extras (API 31). Literals so older compile targets and lint stay quiet.
        const val EXTRA_CALL_TYPE = "android.callType"
        const val EXTRA_ANSWER_INTENT = "android.answerIntent"
        const val EXTRA_DECLINE_INTENT = "android.declineIntent"
        const val CALL_TYPE_INCOMING = 1
    }
}
