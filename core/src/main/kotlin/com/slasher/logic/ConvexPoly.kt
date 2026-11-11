package com.slasher.logic

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.atan2

/** One flat face of a [ConvexPoly]. Vertices run counter-clockwise seen from outside; [cut] marks faces made by a cut. */
class Face(val verts: List<Vector3>, val normal: Vector3, val cut: Boolean)

/**
 * A convex polyhedron (spec §4.4). Slicing is a convex clip against a plane.
 * Vertices are shared between polyhedra and are never mutated.
 */
class ConvexPoly(val faces: List<Face>) {
    private var props: Pair<Float, Vector3>? = null

    val volume: Float get() = massProps().first

    /** Centre of mass (uniform density). Do not mutate. */
    val centroid: Vector3 get() = massProps().second

    /** Keeps the part with n·x ≤ d and closes the hole with a cap face facing +n. Null when nothing is left. */
    fun clip(n: Vector3, d: Float, capIsCut: Boolean = true): ConvexPoly? {
        var anyOut = false
        var anyIn = false
        for (f in faces) for (v in f.verts) {
            val s = n.dot(v) - d
            if (s > EPS) anyOut = true
            if (s < -EPS) anyIn = true
        }
        if (!anyOut) return this
        if (!anyIn) return null

        val out = ArrayList<Face>(faces.size + 1)
        val cap = ArrayList<Vector3>()
        for (f in faces) {
            val vs = f.verts
            if (vs.all { abs(n.dot(it) - d) <= EPS }) continue // lies in the plane; the cap replaces it
            val kept = ArrayList<Vector3>(vs.size + 1)
            for (i in vs.indices) {
                val a = vs[i]
                val b = vs[(i + 1) % vs.size]
                val da = n.dot(a) - d
                val db = n.dot(b) - d
                if (da <= EPS) {
                    kept += a
                    if (da >= -EPS) cap += a
                }
                if ((da < -EPS && db > EPS) || (da > EPS && db < -EPS)) {
                    val p = Vector3(a).lerp(b, da / (da - db))
                    kept += p
                    cap += p
                }
            }
            val clean = dedupeLoop(kept)
            if (clean.size >= 3) out += Face(clean, f.normal, f.cut)
        }
        val ring = orderAround(dedupe(cap), n)
        if (ring.size >= 3) out += Face(ring, Vector3(n), capIsCut)
        return if (out.size >= 4) ConvexPoly(out) else null
    }

    /** Splits along [plane]: first the part below it (n·x ≤ d), then the part above. */
    fun split(plane: CutPlane): Pair<ConvexPoly?, ConvexPoly?> =
        clip(plane.n, plane.d) to clip(Vector3(plane.n).scl(-1f), -plane.d)

    /** The cut face lying in [plane] with its normal along [normal], if there is one. */
    fun capFace(normal: Vector3): Face? = faces.lastOrNull { it.cut && it.normal.epsilonEquals(normal, 1e-4f) }

    fun maxAlong(dir: Vector3): Float = faces.maxOf { f -> f.verts.maxOf { dir.dot(it) } }

    fun minAlong(dir: Vector3): Float = faces.minOf { f -> f.verts.minOf { dir.dot(it) } }

    private fun massProps(): Pair<Float, Vector3> {
        props?.let { return it }
        val o = faces[0].verts[0]
        var vol = 0f
        var cx = 0f
        var cy = 0f
        var cz = 0f
        val ab = Vector3()
        val ac = Vector3()
        val ad = Vector3()
        for (f in faces) {
            val v = f.verts
            for (i in 1 until v.size - 1) {
                val a = v[0]
                val b = v[i]
                val c = v[i + 1]
                ab.set(a).sub(o)
                ac.set(b).sub(o)
                ad.set(c).sub(o)
                val tv = ab.dot(ac.crs(ad)) / 6f
                vol += tv
                cx += tv * (o.x + a.x + b.x + c.x) / 4f
                cy += tv * (o.y + a.y + b.y + c.y) / 4f
                cz += tv * (o.z + a.z + b.z + c.z) / 4f
            }
        }
        val result = if (vol > 1e-9f) vol to Vector3(cx / vol, cy / vol, cz / vol) else 0f to Vector3(o)
        props = result
        return result
    }

    companion object {
        private const val EPS = 1e-5f

        /** An axis-aligned box. */
        fun box(minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float): ConvexPoly {
            val p = arrayOf(
                Vector3(minX, minY, minZ), Vector3(maxX, minY, minZ), Vector3(maxX, maxY, minZ), Vector3(minX, maxY, minZ),
                Vector3(minX, minY, maxZ), Vector3(maxX, minY, maxZ), Vector3(maxX, maxY, maxZ), Vector3(minX, maxY, maxZ),
            )
            fun quad(a: Int, b: Int, c: Int, d: Int, n: Vector3) = orientedFace(listOf(p[a], p[b], p[c], p[d]), n)
            return ConvexPoly(
                listOf(
                    quad(1, 2, 6, 5, Vector3(1f, 0f, 0f)),
                    quad(0, 4, 7, 3, Vector3(-1f, 0f, 0f)),
                    quad(3, 7, 6, 2, Vector3(0f, 1f, 0f)),
                    quad(0, 1, 5, 4, Vector3(0f, -1f, 0f)),
                    quad(4, 5, 6, 7, Vector3(0f, 0f, 1f)),
                    quad(0, 3, 2, 1, Vector3(0f, 0f, -1f)),
                ),
            )
        }

        /**
         * The tower: a square column with rounded vertical edges and a rounded top edge, built as the
         * intersection of planes tangent to a rounded box. Its faces are not marked as cut.
         */
        fun roundedColumn(top: Float = Tower.TOP, bottom: Float = Tower.BOTTOM): ConvexPoly {
            val h = Tower.HALF
            val r = Tower.ROUND
            val a = h - r
            var poly = box(-h, bottom, -h, h, top, h)
            for (ei in 0..4) {
                val beta = ei * 18f
                for (k in 0 until 24) {
                    if (ei == 0 && k % 6 == 0) continue // the flat sides are the box's own faces
                    val phi = k * 15f
                    val n = Vector3(
                        MathUtils.cosDeg(beta) * MathUtils.cosDeg(phi),
                        MathUtils.sinDeg(beta),
                        MathUtils.cosDeg(beta) * MathUtils.sinDeg(phi),
                    ).nor()
                    val support = a * (abs(n.x) + abs(n.z)) + n.y * (top - r) + r
                    poly = poly.clip(n, support, capIsCut = false) ?: poly
                }
            }
            return poly
        }

        /** A fresh, uncut tower. Shared; never mutated. */
        val freshColumn: ConvexPoly by lazy { roundedColumn() }

        private fun orientedFace(verts: List<Vector3>, n: Vector3): Face {
            val e1 = Vector3(verts[1]).sub(verts[0])
            val e2 = Vector3(verts[2]).sub(verts[0])
            return if (e1.crs(e2).dot(n) >= 0f) Face(verts, n, false) else Face(verts.reversed(), n, false)
        }

        private fun dedupe(points: List<Vector3>): List<Vector3> {
            val out = ArrayList<Vector3>(points.size)
            for (p in points) if (out.none { it.dst2(p) < 1e-8f }) out += p
            return out
        }

        private fun dedupeLoop(points: List<Vector3>): List<Vector3> {
            val out = ArrayList<Vector3>(points.size)
            for (p in points) if (out.isEmpty() || out.last().dst2(p) >= 1e-8f) out += p
            while (out.size > 1 && out.first().dst2(out.last()) < 1e-8f) out.removeAt(out.size - 1)
            return out
        }

        /** Sorts points lying in a plane counter-clockwise as seen from +n. */
        private fun orderAround(points: List<Vector3>, n: Vector3): List<Vector3> {
            if (points.size < 3) return points
            val c = Vector3()
            points.forEach { c.add(it) }
            c.scl(1f / points.size)
            val u = if (abs(n.x) < 0.9f) Vector3(n).crs(1f, 0f, 0f).nor() else Vector3(n).crs(0f, 1f, 0f).nor()
            val v = Vector3(n).crs(u)
            val tmp = Vector3()
            return points.sortedBy { p ->
                tmp.set(p).sub(c)
                atan2(tmp.dot(v), tmp.dot(u))
            }
        }
    }
}
