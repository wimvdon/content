package to.wim.content.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import to.wim.content.ui.theme.EmptyStateBadge
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Content's shape (2026-11-03, the owner's choice): Material 3 Expressive's nine-sided cookie,
 * drawn here (Material 3 1.4 has no Expressive shapes). Empty pages show their icon on it, and
 * Mark all as read turns into it when pulled far enough ([morph]). Android only: the web app keeps
 * its own design language. Plain Kotlin, tested on the JVM.
 */
object Cookie {
    const val SIDES = 9
    const val DEPTH = 0.075

    /** Points around the outline: enough for smooth curves through them. */
    const val POINTS = 144

    data class Point(
        val x: Float,
        val y: Float,
    )

    /** The cookie's radius at [angle] (radians), 1 at its bumps. */
    fun radius(angle: Double): Double = 1 - DEPTH * (1 - cos(SIDES * angle))

    private fun angleOf(i: Int): Double = i * 2 * PI / POINTS - PI / 2

    /** The cookie's outline around (0, 0), [size] across, turned by [turn] radians. */
    fun points(
        size: Float,
        turn: Double = 0.0,
    ): List<Point> =
        List(POINTS) { i ->
            val t = angleOf(i)
            val r = size / 2 * radius(t - turn)
            Point((cos(t) * r).toFloat(), (sin(t) * r).toFloat())
        }

    /** A pill [width] by [height] around (0, 0), at the same angles as [points], so one can turn into the other. */
    fun pill(
        width: Float,
        height: Float,
    ): List<Point> {
        val straight = max(0f, width / 2 - height / 2)
        val round = height / 2
        fun inside(
            x: Double,
            y: Double,
        ): Boolean {
            val dx = max(0.0, kotlin.math.abs(x) - straight)
            return dx * dx + y * y <= round.toDouble() * round
        }
        return List(POINTS) { i ->
            val t = angleOf(i)
            val cx = cos(t)
            val cy = sin(t)
            var lo = 0.0
            var hi = width.toDouble() + height
            repeat(24) {
                val m = (lo + hi) / 2
                if (inside(cx * m, cy * m)) lo = m else hi = m
            }
            Point((cx * lo).toFloat(), (cy * lo).toFloat())
        }
    }

    fun mix(
        a: List<Point>,
        b: List<Point>,
        k: Float,
    ): List<Point> = a.zip(b) { p, q -> Point(p.x + (q.x - p.x) * k, p.y + (q.y - p.y) * k) }

    /** Where Mark all as read is at pull [progress] (0 to 1): how much of its label, shrinking and cookie show. */
    data class Morph(
        /** The label and its icon, fading first. */
        val label: Float,
        /** Pill to circle. */
        val shrink: Float,
        /** Circle to cookie. */
        val form: Float,
        /** The tick inside the cookie. */
        val check: Float,
    )

    fun morph(progress: Float): Morph {
        val k = progress.coerceIn(0f, 1f)
        return Morph(
            label = (1 - k / 0.28f).coerceIn(0f, 1f),
            shrink = ease((k - 0.18f) / 0.47f),
            form = ease((k - 0.55f) / 0.45f),
            check = ((k - 0.6f) / 0.3f).coerceIn(0f, 1f),
        )
    }

    /** The outline at [m]: a pill [pillWidth] by [pillHeight], shrinking to a [circle] that becomes a [cookie]. */
    fun outline(
        m: Morph,
        pillWidth: Float,
        pillHeight: Float,
        circle: Float,
        cookie: Float,
        turn: Double = 0.0,
    ): List<Point> {
        val w = pillWidth + (circle - pillWidth) * m.shrink
        val h = pillHeight + (circle - pillHeight) * m.shrink
        return mix(pill(w, h), points(circle + (cookie - circle) * m.form, turn), m.form)
    }

    private fun ease(x: Float): Float = 1 - (1 - x.coerceIn(0f, 1f)).toDouble().pow(3).toFloat()
}

/** A smooth closed path through [points] (Catmull-Rom curves), moved to ([cx], [cy]). */
fun cookiePath(
    points: List<Cookie.Point>,
    cx: Float = 0f,
    cy: Float = 0f,
): Path =
    Path().apply {
        val n = points.size
        if (n == 0) return@apply
        moveTo(points[0].x + cx, points[0].y + cy)
        for (i in 0 until n) {
            val p0 = points[(i - 1 + n) % n]
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            val p3 = points[(i + 2) % n]
            cubicTo(
                p1.x + (p2.x - p0.x) / 6 + cx,
                p1.y + (p2.y - p0.y) / 6 + cy,
                p2.x - (p3.x - p1.x) / 6 + cx,
                p2.y - (p3.y - p1.y) / 6 + cy,
                p2.x + cx,
                p2.y + cy,
            )
        }
        close()
    }

/** The cookie as a Compose shape, filling the smaller side of what it shapes. */
object CookieShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(cookiePath(Cookie.points(min(size.width, size.height)), size.width / 2, size.height / 2))
}

/**
 * The cookie behind an empty page's icon (the kit's EmptyState): as the page appears it springs in
 * from a little smaller and turns a few degrees into place, then stays still.
 */
val CookieBadge = EmptyStateBadge(CookieShape) { Modifier.springTurnIn() }

@Composable
private fun Modifier.springTurnIn(): Modifier {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    return graphicsLayer {
        val k = grow.value
        scaleX = 0.6f + 0.4f * k
        scaleY = 0.6f + 0.4f * k
        rotationZ = -40f * (1f - k)
        alpha = (k * 2f).coerceIn(0f, 1f)
    }
}
