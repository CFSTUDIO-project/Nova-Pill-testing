package app.novapill.android.overlay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.core.CardKind
import app.novapill.android.core.CountdownMath
import app.novapill.android.core.StopwatchMath
import app.novapill.android.core.StopwatchPhase
import app.novapill.android.core.TimeFormat
import app.novapill.android.core.TimerPhase
import app.novapill.android.overlay.IslandActions
import app.novapill.android.overlay.IslandUi
import app.novapill.android.ui.components.NovaIcon
import app.novapill.android.ui.components.NovaIconView
import app.novapill.android.ui.components.pressable
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType
import app.novapill.android.util.RecordMethod
import app.novapill.android.util.SystemAppKind
import app.novapill.android.util.TorchProblem
import app.novapill.android.util.WeatherCodes
import app.novapill.android.util.WeatherKind
import app.novapill.android.util.WeatherState

// ---------------------------------------------------------------------------------------------
// Timer
// ---------------------------------------------------------------------------------------------

@Composable
internal fun TimerPanel(ui: IslandUi, accent: Color, actions: IslandActions, topPadDp: Float) {
    val timer = ui.timer
    val finished = timer.phase == TimerPhase.FINISHED
    val now = rememberTicker(timer.phase == TimerPhase.RUNNING, 100L).value
    val left = CountdownMath.remaining(timer, now)
    val progress = if (finished) 1f else CountdownMath.progress(timer, now)
    val blink = rememberBlink(finished && !ui.reducedMotion)
    val caption = when (timer.phase) {
        TimerPhase.RUNNING -> "Timer · running"
        TimerPhase.PAUSED -> "Timer · paused"
        TimerPhase.FINISHED -> "Time's up"
        TimerPhase.NONE -> "Timer"
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(topPadDp.dp))
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Ring(progress, accent, Modifier.size(64.dp))
                NovaIconView(NovaIcon.TIMER, NovaColors.Text2, size = 22.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                PillText(
                    TimeFormat.countdown(left),
                    NovaType.NumberBig,
                    color = if (finished) accent.copy(alpha = blink) else if (timer.phase == TimerPhase.PAUSED) NovaColors.Text2 else NovaColors.Text1,
                )
                PillText(caption, NovaType.Caption)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (timer.phase) {
                TimerPhase.RUNNING -> {
                    PillButton("Pause", { actions.pauseTimer() }, Modifier.weight(1f), NovaIcon.PAUSE, Color.White, Color.Black, height = 48.dp, fontSize = 14)
                    PillButton("+1 min", { actions.addTimerMinute() }, Modifier.weight(1f), NovaIcon.PLUS, height = 48.dp, fontSize = 14)
                    RoundButton(NovaIcon.CLOSE, "Cancel timer", { actions.cancelTimer() }, container = NovaColors.Surface3)
                }
                TimerPhase.PAUSED -> {
                    PillButton("Resume", { actions.resumeTimer() }, Modifier.weight(1f), NovaIcon.PLAY, Color.White, Color.Black, height = 48.dp, fontSize = 14)
                    PillButton("+1 min", { actions.addTimerMinute() }, Modifier.weight(1f), NovaIcon.PLUS, height = 48.dp, fontSize = 14)
                    RoundButton(NovaIcon.CLOSE, "Cancel timer", { actions.cancelTimer() }, container = NovaColors.Surface3)
                }
                TimerPhase.FINISHED -> {
                    PillButton("Done", { actions.cancelTimer() }, Modifier.weight(1f), NovaIcon.CHECK, Color.White, Color.Black, height = 48.dp, fontSize = 14)
                    PillButton("+1 min", { actions.addTimerMinute() }, Modifier.weight(1f), NovaIcon.PLUS, height = 48.dp, fontSize = 14)
                }
                TimerPhase.NONE -> Unit
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (ui.cards.size > 1) CardDots(ui.cards.size, ui.cards.indexOf(CardKind.TIMER).coerceAtLeast(0), accent)
        }
    }
}

/** Ring that shows [fraction] (1 = full) clockwise from the top. */
@Composable
private fun Ring(fraction: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 5.dp.toPx()
        val inset = stroke / 2f
        val arc = Size(size.width - stroke, size.height - stroke)
        drawArc(Color.White.copy(alpha = 0.14f), -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        if (fraction > 0.002f) {
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Stopwatch
// ---------------------------------------------------------------------------------------------

@Composable
internal fun StopwatchPanel(ui: IslandUi, accent: Color, actions: IslandActions, topPadDp: Float) {
    val sw = ui.stopwatch
    val running = sw.phase == StopwatchPhase.RUNNING
    val now = rememberTicker(running, 100L).value
    val elapsed = StopwatchMath.elapsed(sw, now)
    val minuteFraction = (elapsed % 60_000L).toFloat() / 60_000f

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(topPadDp.dp))
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Ring(if (elapsed == 0L) 0f else minuteFraction.coerceAtLeast(0.004f), accent, Modifier.size(64.dp))
                NovaIconView(NovaIcon.STOPWATCH, NovaColors.Text2, size = 22.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                PillText(
                    TimeFormat.stopwatch(elapsed),
                    NovaType.NumberBig,
                    color = if (sw.phase == StopwatchPhase.PAUSED) NovaColors.Text2 else NovaColors.Text1,
                )
                PillText(if (running) "Stopwatch · running" else "Stopwatch · paused", NovaType.Caption)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (running) {
                PillButton("Pause", { actions.stopwatchPause() }, Modifier.weight(1f), NovaIcon.PAUSE, Color.White, Color.Black, height = 48.dp, fontSize = 14)
            } else {
                PillButton("Resume", { actions.stopwatchResume() }, Modifier.weight(1f), NovaIcon.PLAY, Color.White, Color.Black, height = 48.dp, fontSize = 14)
            }
            PillButton("Reset", { actions.stopwatchReset() }, Modifier.weight(1f), NovaIcon.RESET, height = 48.dp, fontSize = 14)
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (ui.cards.size > 1) CardDots(ui.cards.size, ui.cards.indexOf(CardKind.STOPWATCH).coerceAtLeast(0), accent)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Record indicator
// ---------------------------------------------------------------------------------------------

@Composable
internal fun RecordPanel(ui: IslandUi, accent: Color, actions: IslandActions, topPadDp: Float) {
    val record = ui.record
    val manual = record?.manual
    val isManual = record?.method == RecordMethod.MANUAL && manual != null
    val running = record != null && (record.method == RecordMethod.OS || manual?.phase == StopwatchPhase.RUNNING)
    val paused = isManual && manual?.phase == StopwatchPhase.PAUSED
    val now = rememberTicker(running && ui.settings.recordShowDuration, 250L).value
    val elapsed = if (record != null) recordElapsed(record, now) else 0L

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(topPadDp.dp))
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            RecordDot(ui.reducedMotion, size = 12, paused = paused)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                PillText(
                    if (isManual) (if (paused) "Recording timer · paused" else "Recording timer") else "Screen recording visible",
                    NovaType.Title.copy(fontSize = 15.sp, lineHeight = 19.sp),
                )
                PillText(if (isManual) "MANUAL" else "SYSTEM SIGNAL", NovaType.Label.copy(fontSize = 10.sp, lineHeight = 12.sp))
            }
            if (ui.settings.recordShowDuration) {
                PillText(TimeFormat.hms(elapsed), NovaType.NumberBig.copy(fontSize = 22.sp, lineHeight = 26.sp), align = TextAlign.End)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isManual) {
                if (running) {
                    PillButton("Pause", { actions.recordPause() }, Modifier.weight(1f), NovaIcon.PAUSE, Color.White, Color.Black, height = 48.dp, fontSize = 14)
                } else {
                    PillButton("Resume", { actions.recordResume() }, Modifier.weight(1f), NovaIcon.PLAY, Color.White, Color.Black, height = 48.dp, fontSize = 14)
                }
                PillButton("Stop", { actions.recordStop() }, Modifier.weight(1f), NovaIcon.DOT, NovaColors.Danger, Color.White, height = 48.dp, fontSize = 14)
                RoundButton(NovaIcon.RESET, "Reset recording timer", { actions.recordReset() }, container = NovaColors.Surface3)
            } else {
                PillButton("Dismiss", { actions.recordDismiss() }, Modifier.weight(1f), NovaIcon.CLOSE, height = 48.dp, fontSize = 14)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(28.dp)) {
            PillText(
                if (isManual) "A timer you control. It is not connected to the system screen recorder."
                else "Reported by Android for this app's own windows only. Recordings of other apps are not detected.",
                NovaType.Caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
                maxLines = 2,
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (ui.cards.size > 1) CardDots(ui.cards.size, ui.cards.indexOf(CardKind.RECORD).coerceAtLeast(0), accent)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tools
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ToolsPanel(ui: IslandUi, accent: Color, actions: IslandActions, topPadDp: Float) {
    val s = ui.settings
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(topPadDp.dp))
        ToolsHeader(ui)
        Spacer(Modifier.height(8.dp))
        ToolButtons(ui, actions)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (minutes in s.timerPresetsMin) {
                PillButton(
                    text = TimeFormat.presetLabel(minutes),
                    onClick = { actions.startTimer(minutes) },
                    modifier = Modifier.weight(1f),
                    height = 40.dp,
                    fontSize = 12,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton("Alarms", { actions.openSystemApp(SystemAppKind.ALARMS) }, Modifier.weight(1f), NovaIcon.ALARM, height = 40.dp, fontSize = 12)
            PillButton("Calendar", { actions.openSystemApp(SystemAppKind.CALENDAR) }, Modifier.weight(1f), NovaIcon.CALENDAR, height = 40.dp, fontSize = 12)
            PillButton("Phone", { actions.openSystemApp(SystemAppKind.PHONE) }, Modifier.weight(1f), NovaIcon.PHONE, height = 40.dp, fontSize = 12)
        }
        if (s.shortcutApps.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().height(54.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (pkg in s.shortcutApps) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .pressable(label = "Open app", onClick = { actions.openApp(pkg) }),
                    ) {
                        AppIcon(pkg, 44.dp, 12.dp)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (ui.cards.size > 1) CardDots(ui.cards.size, ui.cards.indexOf(CardKind.TOOLS).coerceAtLeast(0), accent)
        }
    }
}

@Composable
private fun ToolsHeader(ui: IslandUi) {
    val clock = rememberClockReading()
    val t = ui.tools
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        PillText(TimeFormat.clock(clock.hour24, clock.minute, t.use24h), NovaType.NumberBig.copy(fontSize = 28.sp, lineHeight = 32.sp))
        if (!t.use24h) {
            Spacer(Modifier.width(4.dp))
            PillText(TimeFormat.meridiem(clock.hour24), NovaType.NumberSmall.copy(fontSize = 11.sp), color = NovaColors.Text3)
        }
        Spacer(Modifier.weight(1f))
        if (t.battery.known) {
            val low = t.battery.percent <= 15 && !t.battery.charging
            val tint = when {
                t.battery.charging -> NovaColors.Green
                low -> NovaColors.Danger
                else -> NovaColors.Text2
            }
            NovaIconView(if (t.battery.charging) NovaIcon.BOLT else NovaIcon.BATTERY, tint, size = 18.dp)
            Spacer(Modifier.width(3.dp))
            PillText("${t.battery.percent}%", NovaType.NumberMid.copy(fontSize = 13.sp), color = tint)
        }
        Spacer(Modifier.width(12.dp))
        WeatherChip(ui)
    }
}

@Composable
private fun WeatherChip(ui: IslandUi) {
    when (val w = ui.tools.weather) {
        is WeatherState.Ready -> {
            val kind = WeatherCodes.kind(w.info.code, w.info.isDay)
            val tint = if (w.stale) NovaColors.Text3 else NovaColors.Text1
            NovaIconView(weatherIcon(kind), tint, size = 20.dp)
            Spacer(Modifier.width(4.dp))
            PillText(WeatherCodes.formatTemp(w.info.tempC, ui.settings.weatherFahrenheit), NovaType.NumberMid.copy(fontSize = 14.sp), color = tint)
        }
        WeatherState.Loading -> PillText("…", NovaType.NumberMid, color = NovaColors.Text3)
        is WeatherState.Unavailable -> {
            NovaIconView(NovaIcon.CLOUD, NovaColors.Text4, size = 20.dp)
            Spacer(Modifier.width(4.dp))
            PillText("–", NovaType.NumberMid, color = NovaColors.Text4)
        }
        WeatherState.Idle -> Unit
    }
}

private fun weatherIcon(kind: WeatherKind): NovaIcon = when (kind) {
    WeatherKind.CLEAR_DAY -> NovaIcon.SUN
    WeatherKind.CLEAR_NIGHT -> NovaIcon.MOON
    WeatherKind.PARTLY_CLOUDY -> NovaIcon.PARTLY
    WeatherKind.CLOUDY -> NovaIcon.CLOUD
    WeatherKind.FOG -> NovaIcon.FOG
    WeatherKind.DRIZZLE, WeatherKind.RAIN -> NovaIcon.RAIN
    WeatherKind.SNOW -> NovaIcon.SNOW
    WeatherKind.THUNDER -> NovaIcon.STORM
    WeatherKind.UNKNOWN -> NovaIcon.CLOUD
}

@Composable
private fun ToolButtons(ui: IslandUi, actions: IslandActions) {
    val t = ui.tools
    val flashLabel = when {
        !t.flashAvailable || t.flashProblem == TorchProblem.NO_FLASH -> "No flash"
        t.flashProblem == TorchProblem.IN_USE -> "In use"
        t.flashProblem == TorchProblem.ERROR -> "Unavailable"
        t.flashOn -> "On"
        else -> "Flashlight"
    }
    Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolCell(
            icon = NovaIcon.FLASHLIGHT,
            label = flashLabel,
            active = t.flashOn,
            enabled = t.flashAvailable && t.flashProblem == TorchProblem.NONE,
            onClick = { actions.toggleFlashlight() },
            modifier = Modifier.weight(1f),
        )
        ToolCell(
            icon = NovaIcon.STOPWATCH,
            label = "Stopwatch",
            active = ui.stopwatch.phase != StopwatchPhase.NONE,
            enabled = true,
            onClick = { actions.stopwatchStart() },
            modifier = Modifier.weight(1f),
        )
        ToolCell(
            icon = NovaIcon.RECORD,
            label = if (t.recordManualEnabled) "Rec timer" else "Rec off",
            active = ui.record?.method == RecordMethod.MANUAL,
            enabled = t.recordManualEnabled,
            onClick = { actions.recordStart() },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ToolCell(icon: NovaIcon, label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val bg = if (active) Color.White else NovaColors.Surface3
    val fg = if (active) Color.Black else NovaColors.Text1
    Column(
        modifier
            .height(52.dp)
            .pressable(enabled = enabled, label = label, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.45f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NovaIconView(icon, if (enabled) fg else fg.copy(alpha = 0.5f), size = 20.dp)
        Spacer(Modifier.height(3.dp))
        PillText(label, NovaType.Caption.copy(fontSize = 10.sp, lineHeight = 12.sp), color = if (enabled) fg else fg.copy(alpha = 0.5f))
    }
}
