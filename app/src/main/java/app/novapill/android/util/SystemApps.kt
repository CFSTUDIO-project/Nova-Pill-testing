package app.novapill.android.util

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

enum class SystemAppKind { ALARMS, CALENDAR, PHONE }

/**
 * Alarm, calendar and call actions are owned by system apps, so the island opens them instead of
 * pretending it can change them from an overlay.
 */
object SystemApps {
    fun open(context: Context, kind: SystemAppKind): Boolean = when (kind) {
        SystemAppKind.ALARMS -> IntentLauncher.start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
        SystemAppKind.CALENDAR ->
            IntentLauncher.start(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
        SystemAppKind.PHONE -> IntentLauncher.start(context, Intent(Intent.ACTION_DIAL))
    }
}
