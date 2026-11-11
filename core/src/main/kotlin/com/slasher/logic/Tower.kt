package com.slasher.logic

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3

/** Fixed tower and view geometry shared by the logic and the renderer (spec §4, §12). */
object Tower {
    /** Height of the tower's flat top. A reconnect always restores it. */
    const val TOP = 0f
    const val HALF = 0.5f
    /** Radius of the rounded edges. */
    const val ROUND = 0.1f
    const val BOTTOM = -40f
    /** Deepest a cut may start, measured down from the top where the swipe crosses the tower's front. */
    const val MAX_CUT_DEPTH = 1.5f
    const val VIEW_YAW_DEG = 40f
    /** Cut planes lean this far toward the camera, so slices slide out toward the viewer as in the video. */
    const val VIEW_LEAN_DEG = 22f
    /** Swipes are placed at this depth toward the camera, roughly on the visible front faces. */
    const val FRONT_DEPTH = 0.4f

    /** Area of the rounded-square cross-section; turns a removed volume into a depth. */
    val CROSS_AREA = 4f * HALF * HALF - (4f - MathUtils.PI) * ROUND * ROUND

    val up: Vector3 get() = Vector3(0f, 1f, 0f)

    /** Horizontal unit vector from the tower toward the camera. */
    val toCamera: Vector3 get() = Vector3(MathUtils.sinDeg(VIEW_YAW_DEG), 0f, MathUtils.cosDeg(VIEW_YAW_DEG))

    /** Horizontal unit vector pointing to screen-right. */
    val right: Vector3 get() = Vector3(MathUtils.cosDeg(VIEW_YAW_DEG), 0f, -MathUtils.sinDeg(VIEW_YAW_DEG))
}

/** The plane n·x = d. The slice is the side with n·x > d, and n always points upward. */
class CutPlane(val n: Vector3, val d: Float) {
    /** Angle of the plane from horizontal, in degrees. */
    val tiltDeg: Float get() = MathUtils.acos(n.y.coerceIn(-1f, 1f)) * MathUtils.radiansToDegrees

    fun distance(p: Vector3) = n.dot(p) - d

    /** Angle between this plane and [other], in degrees (0 = parallel). */
    fun angleTo(other: CutPlane) = angleBetween(n, other.n)

    companion object {
        /**
         * The plane of a swipe line drawn at [screenAngleDeg] (positive rises to the right) that crosses the
         * tower's front at [height]. The plane contains the line and leans [Tower.VIEW_LEAN_DEG] toward the camera.
         */
        fun fromLine(screenAngleDeg: Float, height: Float): CutPlane {
            val n = normalFor(screenAngleDeg)
            val p = Tower.up.scl(height).mulAdd(Tower.toCamera, Tower.FRONT_DEPTH)
            return CutPlane(n, n.dot(p))
        }

        fun normalFor(screenAngleDeg: Float): Vector3 {
            val a = screenAngleDeg
            // Perpendicular to the line within the screen-aligned vertical plane, pointing up…
            val n0 = Tower.up.scl(MathUtils.cosDeg(a)).mulAdd(Tower.right, -MathUtils.sinDeg(a))
            // …then tipped toward the camera around the line.
            val lean = Tower.VIEW_LEAN_DEG
            return n0.scl(MathUtils.cosDeg(lean)).mulAdd(Tower.toCamera, MathUtils.sinDeg(lean)).nor()
        }

        /** Unit direction of a line at [screenAngleDeg] in the screen-aligned vertical plane. */
        fun lineDirection(screenAngleDeg: Float): Vector3 =
            Tower.right.scl(MathUtils.cosDeg(screenAngleDeg)).mulAdd(Tower.up, MathUtils.sinDeg(screenAngleDeg))
    }
}

/** Angle between two planes given by their normals, ignoring which way they face, in degrees. */
fun angleBetween(a: Vector3, b: Vector3): Float {
    val c = kotlin.math.abs(a.dot(b) / (a.len() * b.len())).coerceIn(0f, 1f)
    return MathUtils.acos(c) * MathUtils.radiansToDegrees
}
