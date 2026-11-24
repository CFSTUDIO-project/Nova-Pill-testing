package app.novapill.android.settings

import app.novapill.android.util.MiniJson
import app.novapill.android.util.asJsonList
import app.novapill.android.util.asJsonMap
import app.novapill.android.util.jsonBool
import app.novapill.android.util.jsonInt
import app.novapill.android.util.jsonString

enum class NotificationMode { ALL_EXCEPT_MUTED, ONLY_ALLOWED }

/** What a peek is allowed to show. Default is SUMMARY: app name + short title only. */
enum class PreviewPrivacy { APP_ONLY, SUMMARY, FULL }

enum class QueueBehavior { QUEUE, LATEST_ONLY }

enum class PauseBehavior { KEEP_VISIBLE, COLLAPSE, HIDE }

enum class LyricsProviderChoice { LOCAL_ONLY, LRCLIB }

enum class LyricsHighlight { SOFT, ACCENT, BAR }

enum class ClockFormat { SYSTEM, H12, H24 }

/**
 * Every user-facing preference. Persisted as a single JSON string (see [NovaSettingsCodec]).
 * Ranges are enforced on decode so a corrupted or hand-edited file can never produce an
 * unreadable island (for example zero opacity or a 5 px wide pill).
 */
data class NovaSettings(
    // A. General
    val islandEnabled: Boolean = false,
    val restoreOnLaunch: Boolean = true,
    val autoStartOnBoot: Boolean = false,
    val onboardingDone: Boolean = false,

    // B. Appearance
    val widthDp: Int = 176,
    val heightDp: Int = 38,
    val topOffsetDp: Int = 0,
    val xOffsetDp: Int = 0,
    val cornerPercent: Int = 100,
    val borderIntensity: Int = 30,
    val artworkSizeDp: Int = 56,
    val fontScalePercent: Int = 100,
    val opacityPercent: Int = 100,
    val accentIntensity: Int = 70,
    val haptics: Boolean = true,

    // C. Behavior
    val tapToExpand: Boolean = true,
    val longPressReposition: Boolean = false,
    val swipeGestures: Boolean = true,
    val idleTimeoutSec: Int = 6,
    val showMinimalIdlePill: Boolean = false,
    val hideAfterMediaStops: Boolean = true,
    val hideInFullscreen: Boolean = true,
    val hideWithKeyboard: Boolean = true,
    val hiddenApps: Set<String> = emptySet(),
    val reduceMotionOnBatterySaver: Boolean = true,
    val reducedMotion: Boolean = false,

    // D. Notifications
    val peekEnabled: Boolean = true,
    val peekDurationSec: Int = 2,
    val notifMode: NotificationMode = NotificationMode.ALL_EXCEPT_MUTED,
    val allowedApps: Set<String> = emptySet(),
    val mutedApps: Set<String> = emptySet(),
    val mutedCategories: Set<String> = emptySet(),
    val privacy: PreviewPrivacy = PreviewPrivacy.SUMMARY,
    val hideSensitiveCategories: Boolean = true,
    val queueBehavior: QueueBehavior = QueueBehavior.QUEUE,

    // E. Music
    val preferredPlayer: String = "",
    val autoOpenLyrics: Boolean = false,
    val showArtwork: Boolean = true,
    val showTitleArtist: Boolean = true,
    val showProgress: Boolean = true,
    val visualizerEnabled: Boolean = true,
    val visualizerIntensity: Int = 60,
    val pauseBehavior: PauseBehavior = PauseBehavior.COLLAPSE,
    val pauseGraceSec: Int = 8,
    val lyricsProvider: LyricsProviderChoice = LyricsProviderChoice.LOCAL_ONLY,
    val lyricsFontPercent: Int = 100,
    val lyricsSpacingPercent: Int = 100,
    val lyricsHighlight: LyricsHighlight = LyricsHighlight.SOFT,
    val lyricsOffsetMs: Int = 0,
    val trackOffsets: Map<String, Int> = emptyMap(),

    // F. Record indicator
    val recordUseOs: Boolean = true,
    val recordManualEnabled: Boolean = true,
    val recordShowDuration: Boolean = true,
    val recordResetOnStop: Boolean = true,

    // G. Landscape
    val lsAutoAdapt: Boolean = true,
    val lsCompact: Boolean = true,
    val lsIndependent: Boolean = false,
    val lsTopOffsetDp: Int = 0,
    val lsXOffsetDp: Int = 0,
    val lsWidthDp: Int = 176,
    val hideInLandscapeFullscreen: Boolean = true,

    // H. Utilities
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
    val timerPresetsMin: List<Int> = listOf(1, 5, 10, 25),
    val volumeInMusicCard: Boolean = true,
    val flashShortcut: Boolean = true,
    val weatherCity: String = "",
    val weatherUseLocation: Boolean = false,
    val weatherFahrenheit: Boolean = false,
    val weatherRefreshMin: Int = 60,
    val shortcutApps: List<String> = emptyList(),
) {
    fun effectiveWidthDp(landscape: Boolean): Int = if (landscape && lsIndependent) lsWidthDp else widthDp
    fun effectiveTopOffsetDp(landscape: Boolean): Int = if (landscape && lsIndependent) lsTopOffsetDp else topOffsetDp
    fun effectiveXOffsetDp(landscape: Boolean): Int = if (landscape && lsIndependent) lsXOffsetDp else xOffsetDp

    /** Offset in ms for one track: per-track value wins, else the global one. */
    fun offsetFor(trackOffsetKey: String?): Int =
        (if (trackOffsetKey != null) trackOffsets[trackOffsetKey] else null) ?: lyricsOffsetMs

    companion object {
        val PEEK_DURATIONS_SEC = listOf(2, 3, 4, 6)
        const val MAX_TRACK_OFFSETS = 200
        const val MAX_SHORTCUTS = 4
        const val OFFSET_MIN_MS = -1500
        const val OFFSET_MAX_MS = 1500
        const val OFFSET_STEP_MS = 50
    }
}

object NovaSettingsCodec {
    private const val SCHEMA = 1

    fun encode(s: NovaSettings): String {
        val m = LinkedHashMap<String, Any?>()
        m["schema"] = SCHEMA
        m["islandEnabled"] = s.islandEnabled
        m["restoreOnLaunch"] = s.restoreOnLaunch
        m["autoStartOnBoot"] = s.autoStartOnBoot
        m["onboardingDone"] = s.onboardingDone

        m["widthDp"] = s.widthDp
        m["heightDp"] = s.heightDp
        m["topOffsetDp"] = s.topOffsetDp
        m["xOffsetDp"] = s.xOffsetDp
        m["cornerPercent"] = s.cornerPercent
        m["borderIntensity"] = s.borderIntensity
        m["artworkSizeDp"] = s.artworkSizeDp
        m["fontScalePercent"] = s.fontScalePercent
        m["opacityPercent"] = s.opacityPercent
        m["accentIntensity"] = s.accentIntensity
        m["haptics"] = s.haptics

        m["tapToExpand"] = s.tapToExpand
        m["longPressReposition"] = s.longPressReposition
        m["swipeGestures"] = s.swipeGestures
        m["idleTimeoutSec"] = s.idleTimeoutSec
        m["showMinimalIdlePill"] = s.showMinimalIdlePill
        m["hideAfterMediaStops"] = s.hideAfterMediaStops
        m["hideInFullscreen"] = s.hideInFullscreen
        m["hideWithKeyboard"] = s.hideWithKeyboard
        m["hiddenApps"] = s.hiddenApps.sorted()
        m["reduceMotionOnBatterySaver"] = s.reduceMotionOnBatterySaver
        m["reducedMotion"] = s.reducedMotion

        m["peekEnabled"] = s.peekEnabled
        m["peekDurationSec"] = s.peekDurationSec
        m["notifMode"] = s.notifMode
        m["allowedApps"] = s.allowedApps.sorted()
        m["mutedApps"] = s.mutedApps.sorted()
        m["mutedCategories"] = s.mutedCategories.sorted()
        m["privacy"] = s.privacy
        m["hideSensitiveCategories"] = s.hideSensitiveCategories
        m["queueBehavior"] = s.queueBehavior

        m["preferredPlayer"] = s.preferredPlayer
        m["autoOpenLyrics"] = s.autoOpenLyrics
        m["showArtwork"] = s.showArtwork
        m["showTitleArtist"] = s.showTitleArtist
        m["showProgress"] = s.showProgress
        m["visualizerEnabled"] = s.visualizerEnabled
        m["visualizerIntensity"] = s.visualizerIntensity
        m["pauseBehavior"] = s.pauseBehavior
        m["pauseGraceSec"] = s.pauseGraceSec
        m["lyricsProvider"] = s.lyricsProvider
        m["lyricsFontPercent"] = s.lyricsFontPercent
        m["lyricsSpacingPercent"] = s.lyricsSpacingPercent
        m["lyricsHighlight"] = s.lyricsHighlight
        m["lyricsOffsetMs"] = s.lyricsOffsetMs
        m["trackOffsets"] = s.trackOffsets

        m["recordUseOs"] = s.recordUseOs
        m["recordManualEnabled"] = s.recordManualEnabled
        m["recordShowDuration"] = s.recordShowDuration
        m["recordResetOnStop"] = s.recordResetOnStop

        m["lsAutoAdapt"] = s.lsAutoAdapt
        m["lsCompact"] = s.lsCompact
        m["lsIndependent"] = s.lsIndependent
        m["lsTopOffsetDp"] = s.lsTopOffsetDp
        m["lsXOffsetDp"] = s.lsXOffsetDp
        m["lsWidthDp"] = s.lsWidthDp
        m["hideInLandscapeFullscreen"] = s.hideInLandscapeFullscreen

        m["clockFormat"] = s.clockFormat
        m["timerPresetsMin"] = s.timerPresetsMin
        m["volumeInMusicCard"] = s.volumeInMusicCard
        m["flashShortcut"] = s.flashShortcut
        m["weatherCity"] = s.weatherCity
        m["weatherUseLocation"] = s.weatherUseLocation
        m["weatherFahrenheit"] = s.weatherFahrenheit
        m["weatherRefreshMin"] = s.weatherRefreshMin
        m["shortcutApps"] = s.shortcutApps
        return MiniJson.stringify(m)
    }

    /** Never throws. Unknown/invalid fields fall back to defaults, numbers are clamped. */
    fun decode(json: String?): NovaSettings {
        val root = MiniJson.parseOrNull(json).asJsonMap() ?: return NovaSettings()
        val d = NovaSettings()
        fun int(key: String, def: Int, min: Int, max: Int): Int = (root.jsonInt(key) ?: def).coerceIn(min, max)
        fun bool(key: String, def: Boolean): Boolean = root.jsonBool(key) ?: def
        fun strSet(key: String): Set<String> =
            root[key].asJsonList()?.mapNotNull { it as? String }?.filter { it.isNotBlank() && it.length <= 200 }?.toSet() ?: emptySet()
        val peek = root.jsonInt("peekDurationSec")
        val peekSec = if (peek != null && peek in NovaSettings.PEEK_DURATIONS_SEC) peek else d.peekDurationSec

        val offsets = LinkedHashMap<String, Int>()
        root["trackOffsets"].asJsonMap()?.let { map ->
            for ((k, v) in map) {
                if (offsets.size >= NovaSettings.MAX_TRACK_OFFSETS) break
                val n = (v as? Long)?.toInt() ?: (v as? Double)?.toInt() ?: continue
                if (k.isNotBlank() && k.length <= 300) {
                    offsets[k] = snapOffset(n)
                }
            }
        }

        val presets = root["timerPresetsMin"].asJsonList()
            ?.mapNotNull { (it as? Long)?.toInt() ?: (it as? Double)?.toInt() }
            ?.map { it.coerceIn(1, 180) }
            ?.take(4)
            ?.takeIf { it.isNotEmpty() }
            ?: d.timerPresetsMin

        val shortcuts = root["shortcutApps"].asJsonList()
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() && it.length <= 200 }
            ?.distinct()
            ?.take(NovaSettings.MAX_SHORTCUTS)
            ?: emptyList()

        val refresh = int("weatherRefreshMin", d.weatherRefreshMin, 15, 720)

        return NovaSettings(
            islandEnabled = bool("islandEnabled", d.islandEnabled),
            restoreOnLaunch = bool("restoreOnLaunch", d.restoreOnLaunch),
            autoStartOnBoot = bool("autoStartOnBoot", d.autoStartOnBoot),
            onboardingDone = bool("onboardingDone", d.onboardingDone),

            widthDp = int("widthDp", d.widthDp, 120, 260),
            heightDp = int("heightDp", d.heightDp, 30, 52),
            topOffsetDp = int("topOffsetDp", d.topOffsetDp, -24, 160),
            xOffsetDp = int("xOffsetDp", d.xOffsetDp, -300, 300),
            cornerPercent = int("cornerPercent", d.cornerPercent, 50, 100),
            borderIntensity = int("borderIntensity", d.borderIntensity, 0, 100),
            artworkSizeDp = int("artworkSizeDp", d.artworkSizeDp, 40, 72),
            fontScalePercent = int("fontScalePercent", d.fontScalePercent, 80, 130),
            opacityPercent = int("opacityPercent", d.opacityPercent, 70, 100),
            accentIntensity = int("accentIntensity", d.accentIntensity, 0, 100),
            haptics = bool("haptics", d.haptics),

            tapToExpand = bool("tapToExpand", d.tapToExpand),
            longPressReposition = bool("longPressReposition", d.longPressReposition),
            swipeGestures = bool("swipeGestures", d.swipeGestures),
            idleTimeoutSec = int("idleTimeoutSec", d.idleTimeoutSec, 2, 60),
            showMinimalIdlePill = bool("showMinimalIdlePill", d.showMinimalIdlePill),
            hideAfterMediaStops = bool("hideAfterMediaStops", d.hideAfterMediaStops),
            hideInFullscreen = bool("hideInFullscreen", d.hideInFullscreen),
            hideWithKeyboard = bool("hideWithKeyboard", d.hideWithKeyboard),
            hiddenApps = strSet("hiddenApps"),
            reduceMotionOnBatterySaver = bool("reduceMotionOnBatterySaver", d.reduceMotionOnBatterySaver),
            reducedMotion = bool("reducedMotion", d.reducedMotion),

            peekEnabled = bool("peekEnabled", d.peekEnabled),
            peekDurationSec = peekSec,
            notifMode = root.enumOf("notifMode", d.notifMode),
            allowedApps = strSet("allowedApps"),
            mutedApps = strSet("mutedApps"),
            mutedCategories = strSet("mutedCategories"),
            privacy = root.enumOf("privacy", d.privacy),
            hideSensitiveCategories = bool("hideSensitiveCategories", d.hideSensitiveCategories),
            queueBehavior = root.enumOf("queueBehavior", d.queueBehavior),

            preferredPlayer = (root.jsonString("preferredPlayer") ?: "").take(200),
            autoOpenLyrics = bool("autoOpenLyrics", d.autoOpenLyrics),
            showArtwork = bool("showArtwork", d.showArtwork),
            showTitleArtist = bool("showTitleArtist", d.showTitleArtist),
            showProgress = bool("showProgress", d.showProgress),
            visualizerEnabled = bool("visualizerEnabled", d.visualizerEnabled),
            visualizerIntensity = int("visualizerIntensity", d.visualizerIntensity, 10, 100),
            pauseBehavior = root.enumOf("pauseBehavior", d.pauseBehavior),
            pauseGraceSec = int("pauseGraceSec", d.pauseGraceSec, 2, 120),
            lyricsProvider = root.enumOf("lyricsProvider", d.lyricsProvider),
            lyricsFontPercent = int("lyricsFontPercent", d.lyricsFontPercent, 80, 140),
            lyricsSpacingPercent = int("lyricsSpacingPercent", d.lyricsSpacingPercent, 80, 140),
            lyricsHighlight = root.enumOf("lyricsHighlight", d.lyricsHighlight),
            lyricsOffsetMs = snapOffset(int("lyricsOffsetMs", d.lyricsOffsetMs, NovaSettings.OFFSET_MIN_MS, NovaSettings.OFFSET_MAX_MS)),
            trackOffsets = offsets,

            recordUseOs = bool("recordUseOs", d.recordUseOs),
            recordManualEnabled = bool("recordManualEnabled", d.recordManualEnabled),
            recordShowDuration = bool("recordShowDuration", d.recordShowDuration),
            recordResetOnStop = bool("recordResetOnStop", d.recordResetOnStop),

            lsAutoAdapt = bool("lsAutoAdapt", d.lsAutoAdapt),
            lsCompact = bool("lsCompact", d.lsCompact),
            lsIndependent = bool("lsIndependent", d.lsIndependent),
            lsTopOffsetDp = int("lsTopOffsetDp", d.lsTopOffsetDp, -24, 160),
            lsXOffsetDp = int("lsXOffsetDp", d.lsXOffsetDp, -400, 400),
            lsWidthDp = int("lsWidthDp", d.lsWidthDp, 120, 320),
            hideInLandscapeFullscreen = bool("hideInLandscapeFullscreen", d.hideInLandscapeFullscreen),

            clockFormat = root.enumOf("clockFormat", d.clockFormat),
            timerPresetsMin = presets,
            volumeInMusicCard = bool("volumeInMusicCard", d.volumeInMusicCard),
            flashShortcut = bool("flashShortcut", d.flashShortcut),
            weatherCity = (root.jsonString("weatherCity") ?: "").trim().take(80),
            weatherUseLocation = bool("weatherUseLocation", d.weatherUseLocation),
            weatherFahrenheit = bool("weatherFahrenheit", d.weatherFahrenheit),
            weatherRefreshMin = refresh,
            shortcutApps = shortcuts,
        )
    }

    private inline fun <reified E : Enum<E>> Map<String, Any?>.enumOf(key: String, def: E): E {
        val name = this.jsonString(key) ?: return def
        return enumValues<E>().firstOrNull { it.name == name } ?: def
    }

    /** Snap to the 50 ms grid used by the calibration control. */
    fun snapOffset(value: Int): Int {
        val clamped = value.coerceIn(NovaSettings.OFFSET_MIN_MS, NovaSettings.OFFSET_MAX_MS)
        val step = NovaSettings.OFFSET_STEP_MS
        val q = Math.floorDiv(clamped + step / 2, step)
        return (q * step).coerceIn(NovaSettings.OFFSET_MIN_MS, NovaSettings.OFFSET_MAX_MS)
    }
}
