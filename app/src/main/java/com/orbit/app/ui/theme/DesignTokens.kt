package com.orbit.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Small spacing scale for repeated layout rhythm; component-specific dimensions stay local. */
object OrbitSpacing {
    val ExtraSmall = 2.dp
    val Tiny = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val Comfortable = 20.dp
    val ExtraLarge = 24.dp
    val Huge = 32.dp
    val Giant = 48.dp
    val Display = 64.dp
}

/**
 * Limestone Harbour corner roles (proposal section 6.3): capture box 20 dp, cards 12 dp, chips and
 * small buttons 8 dp, sheets 24 dp at the top, tab bar 20 dp.
 */
object TalleleShapes {
    val CaptureBox = RoundedCornerShape(20.dp)
    val Card = RoundedCornerShape(12.dp)
    val Chip = RoundedCornerShape(8.dp)
    val SmallButton = RoundedCornerShape(8.dp)
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val TabBar = RoundedCornerShape(20.dp)
}

/**
 * Shared shapes for comparable surfaces in existing screens, pointed at the Limestone corner roles.
 *
 * Pills and circles remain explicit geometry exceptions and should continue to use
 * [androidx.compose.foundation.shape.CircleShape] or a component-specific pill shape.
 */
object OrbitShapes {
    val Small = RoundedCornerShape(8.dp)
    val Standard = RoundedCornerShape(12.dp)
    val Prominent = RoundedCornerShape(20.dp)
    val Modal = TalleleShapes.Sheet
}

/** Material shapes provided by the theme, so stock components follow the Limestone corner roles. */
internal val TalleleMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Layout constants from proposal section 6.3. */
object TalleleDimens {
    /** Every control has a touch area of at least 48 x 48 dp. */
    val MinTouchTarget = 48.dp

    /** The thin ink line down the left side of the capture box and the receipt. */
    val InkRuleWidth = 2.dp

    val SideMargin = 20.dp
    val SideMarginCompact = 16.dp

    /** Screens narrower than this use [SideMarginCompact]. */
    const val CompactWidthDp = 360
}

/** Side margin: 20 dp, or 16 dp on screens narrower than 360 dp. */
fun talleleSideMargin(screenWidthDp: Int): Dp =
    if (screenWidthDp < TalleleDimens.CompactWidthDp) TalleleDimens.SideMarginCompact else TalleleDimens.SideMargin

/**
 * Limestone motion roles (proposal section 6.5): quick 120 ms for small colour and opacity changes,
 * settle 220 ms for the thought settling into the receipt, move 300 ms for opening a screen, tabs
 * 180 ms fade (no sliding). No bounce, no looping animation, and nothing depends on an animation
 * finishing. Compose animation specs respect the system animator duration scale.
 */
object TalleleMotion {
    const val QuickMillis = 120
    const val SettleMillis = 220
    const val MoveMillis = 300
    const val TabsMillis = 180
}

/**
 * Calm motion roles shared by navigation and interactive surfaces.
 *
 * Compose animation specs respect Android's system animator duration scale, including
 * the Remove animations accessibility setting. Keep motion finite and purposeful.
 */
object OrbitMotion {
    const val QuickDurationMillis = TalleleMotion.QuickMillis
    const val StandardDurationMillis = TalleleMotion.SettleMillis
    const val EmphasizedDurationMillis = TalleleMotion.MoveMillis

    const val PressedScale = 0.97f

    /*
     * Springs from the Material 3 "standard" motion scheme: no overshoot, so routine actions
     * never bounce. Spatial springs move things; effects springs fade colour and opacity.
     */
    const val SpatialDamping = 0.9f
    const val SpatialFastStiffness = 1400f
    const val SpatialDefaultStiffness = 700f
    const val SpatialSlowStiffness = 300f
    const val EffectsDamping = 1f
    const val EffectsFastStiffness = 3800f
    const val EffectsDefaultStiffness = 1600f
}

object OrbitStateLayer {
    const val Hover = 0.08f
    const val Focus = 0.10f
    const val Pressed = 0.12f
    const val Dragged = 0.16f
    const val Selected = 0.14f
}

object CalendarDimensions {
    val ControlHorizontalPadding = 20.dp
    val ControlVerticalPadding = 16.dp
    val PagerVisualHeight = 44.dp
    val ActionRowHeight = 48.dp
    val WeekdayHeaderHeight = 30.dp
    val MonthRowHeight = 60.dp
    val DateVisualDiameter = 40.dp
    val EventDotDiameter = 4.dp
    val TimelineTimeColumnWidth = 52.dp
    val TimelineGridGap = 12.dp
}
