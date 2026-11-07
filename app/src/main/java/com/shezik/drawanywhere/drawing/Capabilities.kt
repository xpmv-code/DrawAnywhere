package com.shezik.drawanywhere.drawing

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import com.shezik.drawanywhere.util.distance
import com.shezik.drawanywhere.util.distancePointToLineSegment
import com.shezik.drawanywhere.util.hitTestRectEdge
import com.shezik.drawanywhere.model.Stroke

/** Renders a stroke onto [canvas] using [paint]. */
interface Renderer {
    fun render(stroke: Stroke, canvas: Canvas, paint: Paint, now: Long)
}

/** Tests whether [point] within [radius] hits this stroke. */
interface HitTester {
    fun hitTest(stroke: Stroke, point: Offset, radius: Float): Boolean
}

// ── Concrete renderers ──────────────────────────────────────────────

object PenRenderer : Renderer {
    /** Width factor at pressure 0 — keeps a faint trace instead of vanishing. */
    private const val PRESSURE_MIN_FACTOR = 0.25f
    /** Width gain per unit pressure: width = base * (MIN + GAIN * pressure). */
    private const val PRESSURE_GAIN = 1.5f
    /** Pressures within this tolerance of each other are treated as constant width. */
    private const val PRESSURE_UNIFORM_TOLERANCE = 0.02f

    fun pressureWidthFactor(pressure: Float): Float =
        PRESSURE_MIN_FACTOR + PRESSURE_GAIN * pressure

    override fun render(stroke: Stroke, canvas: Canvas, paint: Paint, now: Long) {
        val outline = variableOutline(stroke, widthScale = 1f)
        if (outline == null) {
            canvas.drawPath(buildPath(stroke._points), paint)
            return
        }
        val prevStyle = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawPath(outline, paint)
        paint.style = prevStyle
    }

    /**
     * Variable-width outline (filled polygon) for pressure-varying freehand strokes,
     * cached on the [Stroke]. Returns null when pressure data is absent or uniform —
     * caller should fall back to the legacy constant-width path.
     *
     * @param widthScale Multiplier applied to every width (laser glow uses GLOW_WIDTH).
     */
    fun variableOutline(stroke: Stroke, widthScale: Float = 1f): Path? {
        val pts = stroke._points
        val prs = stroke._pressures
        val n = pts.size
        if (n < 2 || prs.size < n) return null
        for (i in 1 until n) {
            if (kotlin.math.abs(prs[i] - prs[0]) > PRESSURE_UNIFORM_TOLERANCE) return buildOutlineCached(stroke, widthScale)
        }
        return null // uniform pressure → constant width legacy path
    }

    private fun buildOutlineCached(stroke: Stroke, widthScale: Float): Path {
        val n = stroke._points.size
        val cacheValid = stroke.outlineCachePoints == n && stroke.outlineCachePressures == n
        if (!cacheValid) {
            stroke.outlineCore = buildOutline(stroke._points, stroke._pressures, stroke.width)
            stroke.outlineGlow = null
            stroke.outlineCachePoints = n
            stroke.outlineCachePressures = n
        }
        return if (widthScale == 1f) stroke.outlineCore!!
        else stroke.outlineGlow ?: buildOutline(
            stroke._points, stroke._pressures, stroke.width * widthScale
        ).also { stroke.outlineGlow = it }
    }

    /** Builds a filled ribbon: per-point half-widths offset along averaged segment normals. */
    private fun buildOutline(points: List<Offset>, pressures: List<Float>, baseWidth: Float): Path {
        val n = points.size
        val leftX = FloatArray(n); val leftY = FloatArray(n)
        val rightX = FloatArray(n); val rightY = FloatArray(n)

        var prevDx = 0f; var prevDy = 0f
        var prevNx = 0f; var prevNy = 0f
        for (i in 0 until n) {
            // Segment direction toward next point (last point reuses previous segment).
            val dx: Float; val dy: Float
            if (i < n - 1) {
                dx = points[i + 1].x - points[i].x
                dy = points[i + 1].y - points[i].y
            } else {
                dx = prevDx; dy = prevDy
            }
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            var nx: Float; var ny: Float
            if (len > 1e-3f) {
                nx = -dy / len; ny = dx / len
                prevDx = dx / len; prevDy = dy / len
            } else {
                nx = prevNx; ny = prevNy // degenerate segment — reuse last normal
            }
            if (i > 0) {
                // Average with previous segment normal (miter-lite), renormalize.
                val ax = nx + prevNx; val ay = ny + prevNy
                val alen = kotlin.math.sqrt(ax * ax + ay * ay)
                if (alen > 1e-3f) { nx = ax / alen; ny = ay / alen }
            }
            prevNx = nx; prevNy = ny

            val half = 0.5f * baseWidth * pressureWidthFactor(pressures[i])
            leftX[i] = points[i].x + nx * half; leftY[i] = points[i].y + ny * half
            rightX[i] = points[i].x - nx * half; rightY[i] = points[i].y - ny * half
        }

        val p = Path()
        p.moveTo(leftX[0], leftY[0])
        for (i in 1 until n) p.lineTo(leftX[i], leftY[i])
        // Round end cap: quad through the tip.
        p.quadTo(points[n - 1].x, points[n - 1].y, rightX[n - 1], rightY[n - 1])
        for (i in n - 2 downTo 0) p.lineTo(rightX[i], rightY[i])
        // Round start cap.
        p.quadTo(points[0].x, points[0].y, leftX[0], leftY[0])
        p.close()
        return p
    }

    fun buildPath(points: List<Offset>): Path {
        val p = Path()
        if (points.isEmpty()) return p
        p.moveTo(points[0].x, points[0].y)
        points.zipWithNext().forEachIndexed { i, (a, b) ->
            val mx = (a.x + b.x) / 2f; val my = (a.y + b.y) / 2f
            if (i == 0) p.lineTo(mx, my) else p.quadTo(a.x, a.y, mx, my)
        }
        p.lineTo(points.last().x, points.last().y)
        return p
    }
}

object RectRenderer : Renderer {
    override fun render(stroke: Stroke, canvas: Canvas, paint: Paint, now: Long) {
        val pts = stroke.points
        if (pts.size < 2) return
        val (p0, p1) = pts[0] to pts[1]
        canvas.drawRect(minOf(p0.x, p1.x), minOf(p0.y, p1.y), maxOf(p0.x, p1.x), maxOf(p0.y, p1.y), paint)
    }
}

object OvalRenderer : Renderer {
    override fun render(stroke: Stroke, canvas: Canvas, paint: Paint, now: Long) {
        val pts = stroke.points
        if (pts.size < 2) return
        val (p0, p1) = pts[0] to pts[1]
        canvas.drawOval(RectF(minOf(p0.x, p1.x), minOf(p0.y, p1.y), maxOf(p0.x, p1.x), maxOf(p0.y, p1.y)), paint)
    }
}

object LaserRenderer : Renderer {
    /** Fraction of TTL before fade begins. */
    private const val FADE_START = 0.7f
    /** Glow width multiplier relative to stroke width. */
    private const val GLOW_WIDTH = 2.5f
    /** Glow alpha as fraction of the stroke's own alpha. */
    private const val GLOW_ALPHA = 0.4f

    override fun render(stroke: Stroke, canvas: Canvas, paint: Paint, now: Long) {
        val ttl = stroke.penType.ttlMs
        val elapsed = now - stroke.modifiedAt

        val fade = if (ttl == null) 1f
            else if (elapsed > ttl) return
            else if (elapsed < ttl * FADE_START) 1f
            else ((ttl - elapsed) / (ttl * (1f - FADE_START))).coerceIn(0f, 1f)

        val baseAlpha = paint.alpha.toFloat()
        val coreOutline = PenRenderer.variableOutline(stroke, widthScale = 1f)

        if (coreOutline != null) {
            // Pressure-varying: filled outlines, glow drawn first.
            val prevStyle = paint.style
            paint.style = Paint.Style.FILL
            val glowOutline = PenRenderer.variableOutline(stroke, widthScale = GLOW_WIDTH)
            paint.alpha = (baseAlpha * GLOW_ALPHA * fade).toInt().coerceIn(0, 255)
            // core outline is non-null here, so the glow outline is too (same points/pressures)
            canvas.drawPath(glowOutline!!, paint)
            paint.alpha = (baseAlpha * fade).toInt().coerceIn(0, 255)
            canvas.drawPath(coreOutline, paint)
            paint.style = prevStyle
            return
        }

        val path = PenRenderer.buildPath(stroke.points)

        // Glow: wider and dimmer, derived from the stroke's own color/alpha
        paint.strokeWidth = stroke.width * GLOW_WIDTH
        paint.alpha = (baseAlpha * GLOW_ALPHA * fade).toInt().coerceIn(0, 255)
        canvas.drawPath(path, paint)

        // Core: normal width, stroke's full alpha
        paint.strokeWidth = stroke.width
        paint.alpha = (baseAlpha * fade).toInt().coerceIn(0, 255)
        canvas.drawPath(path, paint)
    }
}

// ── Concrete hit testers ────────────────────────────────────────────

object SegmentHitTester : HitTester {
    override fun hitTest(stroke: Stroke, point: Offset, radius: Float): Boolean {
        val pts = stroke.points
        if (pts.size == 1) return distance(pts[0], point) <= radius
        return pts.zipWithNext().any { (a, b) -> distancePointToLineSegment(point, a, b) <= radius }
    }
}

object EdgeHitTester : HitTester {
    override fun hitTest(stroke: Stroke, point: Offset, radius: Float): Boolean {
        val pts = stroke.points
        if (pts.size < 2) return false
        val p0 = pts[0]; val p1 = pts[1]
        val left = minOf(p0.x, p1.x); val top = minOf(p0.y, p1.y)
        val right = maxOf(p0.x, p1.x); val bottom = maxOf(p0.y, p1.y)
        return hitTestRectEdge(point, left, top, right, bottom, radius)
    }
}
