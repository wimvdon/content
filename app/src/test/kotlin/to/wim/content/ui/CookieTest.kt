package to.wim.content.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class CookieTest {
    private fun radiusOf(p: Cookie.Point) = hypot(p.x.toDouble(), p.y.toDouble())

    @Test
    fun `a cookie has nine bumps, its full size across, and dips between them`() {
        val points = Cookie.points(100f)
        assertEquals(Cookie.POINTS, points.size)
        val radii = points.map(::radiusOf)
        assertEquals(50.0, radii.max(), 0.01)
        assertEquals(50 * (1 - 2 * Cookie.DEPTH), radii.min(), 0.05)
        // Nine bumps: the radius rises to its top nine times around.
        val tops = radii.indices.count { i -> radii[i] > radii[(i - 1 + radii.size) % radii.size] && radii[i] >= radii[(i + 1) % radii.size] }
        assertEquals(9, tops)
    }

    @Test
    fun `a pill reaches its ends and its top`() {
        val pill = Cookie.pill(200f, 40f)
        // The first point is straight up, a quarter round later straight right.
        assertEquals(-20f, pill[0].y, 0.01f)
        assertEquals(100f, pill[Cookie.POINTS / 4].x, 0.01f)
    }

    @Test
    fun `pulling fades the label first, then shrinks to a circle, then becomes the cookie`() {
        val start = Cookie.morph(0f)
        assertEquals(Cookie.Morph(label = 1f, shrink = 0f, form = 0f, check = 0f), start)
        val early = Cookie.morph(0.2f)
        assertTrue(early.label < 1f && early.form == 0f)
        val half = Cookie.morph(0.5f)
        assertEquals(0f, half.label, 0f)
        assertTrue(half.shrink > 0.5f && half.form == 0f)
        assertEquals(Cookie.Morph(label = 0f, shrink = 1f, form = 1f, check = 1f), Cookie.morph(1f))
        assertEquals(Cookie.morph(1f), Cookie.morph(3f))
    }

    @Test
    fun `the outline goes from the pill to the cookie`() {
        val pill = Cookie.outline(Cookie.morph(0f), 200f, 40f, 64f, 76f)
        assertEquals(100f, pill[Cookie.POINTS / 4].x, 0.01f)
        val cookie = Cookie.outline(Cookie.morph(1f), 200f, 40f, 64f, 76f)
        assertEquals(38.0, cookie.maxOf(::radiusOf), 0.01)
    }
}
