package com.slasher.logic

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import kotlin.math.max
import kotlin.random.Random

/**
 * A cut-off slice: it slides down the cut face, tips over its edge and tumbles away (spec §4.2).
 * Deterministic, with no physics engine. The slice's mesh is in world coordinates at the moment of the cut;
 * [transform] maps it to where it is now.
 */
class SliceBody(
    val poly: ConvexPoly,
    val plane: CutPlane,
    /** The stub's cut face the slice rests on. */
    support: Face?,
    friction: Float,
    random: Random,
) {
    enum class Phase { SLIDE, TIP, TUMBLE, DONE }

    var phase = Phase.SLIDE
        private set
    var age = 0f
        private set

    /** True for shavings, which flutter off lighter. */
    val thin = poly.maxAlong(plane.n) - plane.d < CutRules.SHAVING_THICKNESS

    private val com = Vector3(poly.centroid)
    val position = Vector3(com)
    val rotation = Quaternion()

    /** Downhill direction within the plane. */
    private val downhill = Vector3(plane.n).scl(plane.n.y).sub(Tower.up).let { if (it.len2() < 1e-6f) Vector3(Tower.right) else it.nor() }
    private val tipAxis = Vector3(plane.n).crs(downhill).nor()
    private val sinT = Vector3(plane.n).crs(Tower.up).len()
    private val cosT = plane.n.y
    private val accel = max(MIN_ACCEL, G * (sinT - friction * cosT))
    private val edgeDistance: Float

    private var slide = 0f
    var speed = START_PUSH
        private set
    private var tipStartSlide = 0f
    private val pivotStart = Vector3()
    private val rel = Vector3()
    private var tipAngle = 0f
    private var tipOmega = 0f
    private val velocity = Vector3()
    private val angular = Vector3()
    private val prev = Vector3()
    private val spinJitter = Vector3(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f)
    private val tmpQ = Quaternion()
    private val tmp = Vector3()

    init {
        // How far the centre of mass can slide before it passes the support's edge.
        val cp = Vector3(com).mulAdd(plane.n, -plane.distance(com))
        edgeDistance = support?.let { exitDistance(cp, downhill, it) } ?: 0f
    }

    /** Advances the body. Returns true when the phase changed. */
    fun update(dt: Float): Boolean {
        if (phase == Phase.DONE || dt <= 0f) return false
        age += dt
        prev.set(position)
        val before = phase
        when (phase) {
            Phase.SLIDE -> {
                speed += accel * dt
                slide += speed * dt
                position.set(com).mulAdd(downhill, slide)
                if (slide >= edgeDistance) startTip()
            }
            Phase.TIP -> {
                speed += accel * 0.5f * dt
                slide += speed * dt
                tipOmega += G * 1.4f / max(rel.len(), 0.25f) * dt
                tipAngle += tipOmega * dt
                rotation.setFromAxisRad(tipAxis, tipAngle)
                val pivot = tmp.set(pivotStart).mulAdd(downhill, (slide - tipStartSlide) * 0.6f)
                position.set(rel).mul(rotation).add(pivot)
                if (tipAngle >= TIP_RELEASE) {
                    phase = Phase.TUMBLE
                    velocity.set(position).sub(prev).scl(1f / dt)
                    angular.set(tipAxis).scl(tipOmega).mulAdd(spinJitter, if (thin) 9f else 2.5f)
                }
            }
            Phase.TUMBLE -> {
                velocity.y -= G * (if (thin) 0.45f else 1f) * dt
                if (thin) velocity.scl(1f - 1.2f * dt)
                position.mulAdd(velocity, dt)
                val w = angular.len()
                if (w > 1e-5f) {
                    tmpQ.setFromAxisRad(angular.x / w, angular.y / w, angular.z / w, w * dt)
                    rotation.mulLeft(tmpQ)
                }
                if (position.y < -12f || age > 5f) phase = Phase.DONE
            }
            Phase.DONE -> Unit
        }
        return phase != before
    }

    private fun startTip() {
        phase = Phase.TIP
        tipStartSlide = slide
        val cp = Vector3(com).mulAdd(plane.n, -plane.distance(com))
        pivotStart.set(cp).mulAdd(downhill, edgeDistance)
        rel.set(position).sub(pivotStart)
        tipOmega = max(1.5f, speed / max(rel.len(), 0.15f) * 0.6f)
    }

    fun transform(out: Matrix4): Matrix4 = out.idt().translate(position).rotate(rotation).translate(-com.x, -com.y, -com.z)

    companion object {
        /** Gravity, tuned for weight rather than realism, with the tower 1 unit wide. */
        const val G = 12f
        const val START_PUSH = 1.3f
        const val MIN_ACCEL = 2f
        val TIP_RELEASE = 25f * com.badlogic.gdx.math.MathUtils.degreesToRadians

        /** Distance along [dir] from [p] (inside the face) to the face's boundary. */
        fun exitDistance(p: Vector3, dir: Vector3, face: Face): Float {
            val n = face.normal
            val side = Vector3(n).crs(dir)
            var best = 0f
            val v = face.verts
            val e = Vector3()
            val ap = Vector3()
            for (i in v.indices) {
                val a = v[i]
                val b = v[(i + 1) % v.size]
                // Solve p + dir·t = a + (b − a)·s in the plane's (dir, side) coordinates.
                e.set(b).sub(a)
                ap.set(a).sub(p)
                val e1 = e.dot(dir)
                val e2 = e.dot(side)
                val a1 = ap.dot(dir)
                val a2 = ap.dot(side)
                if (kotlin.math.abs(e2) < 1e-7f) continue
                val s = -a2 / e2
                if (s < 0f || s > 1f) continue
                val t = a1 + e1 * s
                if (t > best) best = t
            }
            return best
        }
    }
}
