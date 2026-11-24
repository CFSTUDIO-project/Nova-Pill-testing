package app.novapill.android.overlay

import app.novapill.android.core.CardKind
import app.novapill.android.settings.NovaSettings
import kotlin.math.max
import kotlin.math.min

/** Everything here is in dp and in the coordinate space of the current display rotation. */
data class RectDp(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val centerX: Float get() = (left + right) / 2f
}

data class ScreenInfo(
    val widthDp: Float,
    val heightDp: Float,
    val density: Float,
    val landscape: Boolean,
    val statusBarDp: Float,
    /** Cutout touching the top edge of the current orientation, if any. */
    val topCutout: RectDp?,
) {
    companion object {
        val DEFAULT = ScreenInfo(400f, 860f, 2.75f, false, 28f, null)
    }
}

enum class PillKind { IDLE, COMPACT, PEEK, PEEK_CALL, EXPANDED }

data class PillSize(val widthDp: Float, val heightDp: Float, val cornerDp: Float)

data class CutoutInfo(
    /** Cutout centre minus pill centre (dp). Constant while the pill morphs. */
    val centerOffsetDp: Float,
    val widthDp: Float,
    /** Cutout bottom in screen coordinates (dp). */
    val bottomDp: Float,
)

data class Placement(
    /** Pill left/top in screen coordinates for the requested size. */
    val leftDp: Float,
    val topDp: Float,
    val centerXDp: Float,
    val cutout: CutoutInfo?,
)

data class LayoutRequest(
    val kind: PillKind,
    val card: CardKind? = null,
    val lyricsOpen: Boolean = false,
    val showVolume: Boolean = false,
    val showProgress: Boolean = true,
    val cardCount: Int = 1,
    val hasShortcuts: Boolean = false,
)

object IslandLayout {
    /** Transparent window margin that keeps the soft shadow from being clipped. */
    const val WINDOW_PAD_H_DP = 10f
    const val WINDOW_PAD_BOTTOM_DP = 14f
    private const val MIN_COMPACT_DP = 96f
    private const val EDGE_MARGIN_DP = 4f
    private const val CUTOUT_CONTENT_MARGIN_DP = 8f

    fun collapsedWidth(s: NovaSettings, screen: ScreenInfo): Float {
        val base = s.effectiveWidthDp(screen.landscape).toFloat()
        val adapted = if (screen.landscape && s.lsAutoAdapt && s.lsCompact) base * 0.86f else base
        return adapted.coerceIn(MIN_COMPACT_DP, max(MIN_COMPACT_DP, screen.widthDp * 0.6f))
    }

    fun collapsedHeight(s: NovaSettings): Float = s.heightDp.toFloat()

    fun expandedWidth(s: NovaSettings, screen: ScreenInfo): Float {
        val cap = if (screen.landscape && s.lsAutoAdapt && s.lsCompact) 320f else 344f
        val frac = if (screen.landscape) 0.6f else 0.92f
        return min(cap, screen.widthDp * frac).coerceAtLeast(MIN_COMPACT_DP)
    }

    fun size(req: LayoutRequest, s: NovaSettings, screen: ScreenInfo): PillSize {
        val cw = collapsedWidth(s, screen)
        val ch = collapsedHeight(s)
        val pct = s.cornerPercent / 100f
        return when (req.kind) {
            PillKind.IDLE -> {
                val w = max(84f, cw * 0.52f)
                PillSize(w, ch, corner(ch, 999f, pct))
            }
            PillKind.COMPACT -> PillSize(cw, ch, corner(ch, 999f, pct))
            PillKind.PEEK -> {
                val h = max(52f, ch + 14f)
                val w = min(max(cw + 124f, 300f), min(340f, screen.widthDp * 0.9f))
                PillSize(w, h, corner(h, 28f, pct))
            }
            PillKind.PEEK_CALL -> {
                val h = 68f
                val w = min(max(cw + 124f, 316f), min(344f, screen.widthDp * 0.92f))
                PillSize(w, h, corner(h, 30f, pct))
            }
            PillKind.EXPANDED -> {
                val w = expandedWidth(s, screen)
                var h = expandedHeight(req, s, screen)
                val room = screen.heightDp - 24f
                h = min(h, room)
                PillSize(w, h, corner(h, 34f, pct))
            }
        }
    }

    private fun corner(height: Float, maxRadius: Float, pct: Float): Float = min(height / 2f, maxRadius) * pct

    private fun expandedHeight(req: LayoutRequest, s: NovaSettings, screen: ScreenInfo): Float {
        val dots = if (req.cardCount > 1) 14f else 0f
        val compactLs = screen.landscape && s.lsAutoAdapt && s.lsCompact
        val body = when (req.card) {
            CardKind.MEDIA -> {
                val art = if (compactLs) min(s.artworkSizeDp.toFloat(), 44f) else s.artworkSizeDp.toFloat()
                var h = 16f + art + 10f + 56f + 16f
                if (req.showProgress) h += 40f
                if (req.lyricsOpen) h += 96f else if (req.showVolume) h += 44f
                h
            }
            CardKind.TIMER, CardKind.STOPWATCH -> 148f
            CardKind.RECORD -> 164f
            CardKind.TOOLS, null -> {
                // header 44, tool buttons 52, timer presets 48, system app links 48, optional user shortcuts 54
                var h = 16f + 44f + 8f + 52f + 4f + 48f + 4f + 48f + 16f
                if (req.hasShortcuts) h += 54f
                h
            }
        }
        return body + dots
    }

    /**
     * Anchor of the pill. With a top camera cutout the collapsed pill is vertically centred on it, so the
     * black capsule blends into the hole; without one it sits just below the status-bar edge.
     */
    fun placement(size: PillSize, s: NovaSettings, screen: ScreenInfo): Placement {
        val ch = collapsedHeight(s)
        val offsetX = s.effectiveXOffsetDp(screen.landscape).toFloat()
        val cutout = screen.topCutout
        val baseTop = if (cutout != null) {
            ((cutout.top + cutout.bottom) / 2f - ch / 2f).coerceAtLeast(2f)
        } else {
            max(4f, (screen.statusBarDp - ch) / 2f + 4f)
        }
        val top = (baseTop + s.effectiveTopOffsetDp(screen.landscape)).coerceAtLeast(0f)

        val wanted = screen.widthDp / 2f + offsetX
        val half = size.widthDp / 2f
        val minCenter = EDGE_MARGIN_DP + half
        val maxCenter = screen.widthDp - EDGE_MARGIN_DP - half
        val center = if (minCenter <= maxCenter) wanted.coerceIn(minCenter, maxCenter) else screen.widthDp / 2f

        val left = center - half
        val info = cutout?.let {
            val overlapsX = it.right > left && it.left < left + size.widthDp
            val overlapsY = it.top < top + size.heightDp
            if (overlapsX && overlapsY) CutoutInfo(it.centerX - center, it.width, it.bottom) else null
        }
        return Placement(left, top, center, info)
    }

    /** Extra top padding a tall panel needs so its content starts below the camera hole. */
    fun contentTopInset(placement: Placement): Float {
        val c = placement.cutout ?: return 0f
        return max(0f, c.bottomDp - placement.topDp + CUTOUT_CONTENT_MARGIN_DP)
    }

    fun windowWidthDp(size: PillSize): Float = size.widthDp + 2f * WINDOW_PAD_H_DP

    fun windowHeightDp(size: PillSize): Float = size.heightDp + WINDOW_PAD_BOTTOM_DP
}
