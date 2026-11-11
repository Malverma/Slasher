package com.slasher.render

import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.EarClippingTriangulator
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.FloatArray as GdxFloats
import com.slasher.logic.ConvexPoly
import com.slasher.logic.Tower
import kotlin.math.max
import kotlin.math.min

/** Builds GPU meshes: position, normal and a "cut face" flag per vertex. */
object Meshes {
    private const val STRIDE = 7

    private fun attributes() = arrayOf(
        VertexAttribute(Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
        VertexAttribute(Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE),
        VertexAttribute(Usage.Generic, 1, "a_cut"),
    )

    private fun upload(data: GdxFloats): Mesh {
        val mesh = Mesh(true, data.size / STRIDE, 0, *attributes())
        mesh.setVertices(data.items, 0, data.size)
        return mesh
    }

    /**
     * A slice, stub or tower. Original faces use the rounded box's smooth normals, so the facets read as
     * soft rounded edges; cut faces stay perfectly flat.
     */
    fun fromPoly(poly: ConvexPoly): Mesh {
        val data = GdxFloats(poly.faces.sumOf { (it.verts.size - 2) * 3 } * STRIDE)
        val n = Vector3()
        for (f in poly.faces) {
            val v = f.verts
            for (i in 1 until v.size - 1) {
                for (p in arrayOf(v[0], v[i], v[i + 1])) {
                    if (f.cut) n.set(f.normal) else roundedNormal(p, f.normal, n)
                    data.add(p.x, p.y, p.z)
                    data.add(n.x, n.y, n.z)
                    data.add(if (f.cut) 1f else 0f)
                }
            }
        }
        return upload(data)
    }

    /** A faceted gem (a cut-cornered octahedron) of radius [r], with flat faces. */
    fun gem(r: Float): Mesh {
        var poly: ConvexPoly = ConvexPoly.box(-r, -r * 1.2f, -r, r, r * 1.2f, r)
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) {
            val n = Vector3(sx.toFloat(), sy * 0.8f, sz.toFloat()).nor()
            poly = poly.clip(n, r * 0.95f) ?: poly
        }
        for (k in 0 until 4) {
            val a = k * 90f + 45f
            poly = poly.clip(Vector3(MathUtils.cosDeg(a), 0f, MathUtils.sinDeg(a)), r * 0.85f) ?: poly
        }
        val data = GdxFloats(poly.faces.sumOf { (it.verts.size - 2) * 3 } * STRIDE)
        for (f in poly.faces) {
            val v = f.verts
            for (i in 1 until v.size - 1) for (p in arrayOf(v[0], v[i], v[i + 1])) {
                data.add(p.x, p.y, p.z)
                data.add(f.normal.x, f.normal.y, f.normal.z)
                data.add(0f)
            }
        }
        return upload(data)
    }

    /** Gradient of the rounded tower's distance field at [p], falling back to [faceNormal]. */
    private fun roundedNormal(p: Vector3, faceNormal: Vector3, out: Vector3): Vector3 {
        val a = Tower.HALF - Tower.ROUND
        out.set(
            p.x - MathUtils.clamp(p.x, -a, a),
            p.y - min(p.y, Tower.TOP - Tower.ROUND),
            p.z - MathUtils.clamp(p.z, -a, a),
        )
        return if (out.len2() < 1e-8f || out.dot(faceNormal) <= 0f) out.set(faceNormal) else out.nor()
    }

    /**
     * Extrudes a flat 2D outline (x, y pairs, any winding, may be concave) into a slab [thickness] thick,
     * centered on z = 0. Used for weapon parts.
     */
    fun extrude(outline: FloatArray, thickness: Float): Mesh {
        val pts = if (signedArea(outline) < 0f) reverse(outline) else outline
        val count = pts.size / 2
        val tris = EarClippingTriangulator().computeTriangles(pts)
        val data = GdxFloats((tris.size * 2 + count * 6) * STRIDE)
        val z = thickness / 2f
        for (side in intArrayOf(1, -1)) {
            for (t in 0 until tris.size / 3) {
                val idx = if (side > 0) intArrayOf(tris[t * 3].toInt(), tris[t * 3 + 1].toInt(), tris[t * 3 + 2].toInt())
                else intArrayOf(tris[t * 3].toInt(), tris[t * 3 + 2].toInt(), tris[t * 3 + 1].toInt())
                for (k in idx) {
                    data.add(pts[k * 2], pts[k * 2 + 1], z * side)
                    data.add(0f, 0f, side.toFloat())
                    data.add(0f)
                }
            }
        }
        for (i in 0 until count) {
            val j = (i + 1) % count
            val ax = pts[i * 2]
            val ay = pts[i * 2 + 1]
            val bx = pts[j * 2]
            val by = pts[j * 2 + 1]
            var nx = by - ay
            var ny = -(bx - ax)
            val len = max(1e-6f, kotlin.math.sqrt(nx * nx + ny * ny))
            nx /= len
            ny /= len
            val quad = arrayOf(floatArrayOf(ax, ay, -z), floatArrayOf(bx, by, -z), floatArrayOf(bx, by, z), floatArrayOf(ax, ay, z))
            for (k in intArrayOf(0, 1, 2, 0, 2, 3)) {
                data.add(quad[k][0], quad[k][1], quad[k][2])
                data.add(nx, ny, 0f)
                data.add(0f)
            }
        }
        return upload(data)
    }

    fun signedArea(p: FloatArray): Float {
        var a = 0f
        val n = p.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            a += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1]
        }
        return a / 2f
    }

    private fun reverse(p: FloatArray): FloatArray {
        val n = p.size / 2
        val out = FloatArray(p.size)
        for (i in 0 until n) {
            out[i * 2] = p[(n - 1 - i) * 2]
            out[i * 2 + 1] = p[(n - 1 - i) * 2 + 1]
        }
        return out
    }
}
