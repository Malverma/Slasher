package com.slasher.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.EarClippingTriangulator
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import com.slasher.logic.Weapon
import com.slasher.logic.Weapons
import kotlin.math.max

/** One flat piece of a weapon: an outline in weapon space (x along the weapon from the grip, +y = leading edge). */
class WeaponPart(val outline: FloatArray, val rgb: Int, val metal: Float, val rough: Float, val thickness: Float)

/** Low-poly weapon designs (spec §7) and how they move. */
class WeaponShape(
    val parts: List<WeaponPart>,
    /** Thrown weapons spin around this point on the x axis. */
    val spinCenter: Float,
    /** Spin rate for thrown weapons, degrees per second. */
    val spinRate: Float,
    /** Swung weapons: distance from the hand to the tower. */
    val reach: Float,
    /** World size multiplier. */
    val scale: Float,
) {
    val minX = parts.minOf { p -> p.outline.filterIndexed { i, _ -> i % 2 == 0 }.min() }
    val maxX = parts.maxOf { p -> p.outline.filterIndexed { i, _ -> i % 2 == 0 }.max() }
    val minY = parts.minOf { p -> p.outline.filterIndexed { i, _ -> i % 2 == 1 }.min() }
    val maxY = parts.maxOf { p -> p.outline.filterIndexed { i, _ -> i % 2 == 1 }.max() }
}

object WeaponShapes {
    private const val STEEL = 0xD8DEE8
    private const val DARK_STEEL = 0x6B7280
    private const val WOOD = 0x8B5A2B
    private const val DARK_WOOD = 0x5A3A1E
    private const val BRONZE = 0xC8913A
    private const val LEATHER = 0x6B3E26
    private const val GOLD = 0xE8B83E
    private const val NAVY = 0x1B2A4A

    private fun rect(x0: Float, y0: Float, x1: Float, y1: Float) = floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1)

    private fun poly(vararg p: Float) = p

    private fun disc(cx: Float, cy: Float, rx: Float, ry: Float, n: Int = 10) =
        FloatArray(n * 2) { i -> val a = (i / 2) * MathUtils.PI2 / n; if (i % 2 == 0) cx + rx * MathUtils.cos(a) else cy + ry * MathUtils.sin(a) }

    private fun blade(rgb: Int, vararg p: Float) = WeaponPart(p, rgb, 0.9f, 0.2f, 0.025f)
    private fun grip(rgb: Int, outline: FloatArray, t: Float = 0.08f) = WeaponPart(outline, rgb, 0f, 0.8f, t)
    private fun fitting(rgb: Int, outline: FloatArray, t: Float = 0.1f) = WeaponPart(outline, rgb, 0.7f, 0.35f, t)

    val knife = WeaponShape(
        listOf(
            grip(DARK_WOOD, rect(-0.12f, -0.065f, 0.42f, 0.065f), 0.08f),
            fitting(DARK_STEEL, rect(0.42f, -0.08f, 0.47f, 0.08f), 0.07f),
            blade(STEEL, 0.47f, 0.1f, 1.2f, 0.1f, 1.45f, 0.06f, 1.38f, -0.02f, 1.2f, -0.08f, 0.85f, -0.11f, 0.47f, -0.11f),
        ),
        spinCenter = 0.65f, spinRate = 1080f, reach = 0f, scale = 0.85f,
    )

    val gladius = WeaponShape(
        listOf(
            fitting(BRONZE, disc(-0.06f, 0f, 0.1f, 0.1f), 0.12f),
            grip(LEATHER, rect(0f, -0.055f, 0.36f, 0.055f)),
            fitting(BRONZE, rect(0.36f, -0.17f, 0.43f, 0.17f), 0.1f),
            blade(STEEL, 0.43f, -0.1f, 1.0f, -0.085f, 1.5f, -0.12f, 1.85f, -0.07f, 2.05f, 0f, 1.85f, 0.07f, 1.5f, 0.12f, 1.0f, 0.085f, 0.43f, 0.1f),
        ),
        spinCenter = 0.9f, spinRate = 1500f, reach = 0f, scale = 0.75f,
    )

    val axe = WeaponShape(
        listOf(
            grip(WOOD, rect(0f, -0.05f, 2.45f, 0.05f), 0.09f),
            fitting(DARK_STEEL, poly(2.05f, 0.05f, 2.42f, 0.05f, 2.55f, 0.25f, 2.7f, 0.55f, 2.55f, 0.62f, 2.3f, 0.66f, 2.05f, 0.62f, 1.9f, 0.55f, 2.0f, 0.25f), 0.07f),
            blade(STEEL, 2.7f, 0.55f, 2.56f, 0.64f, 2.3f, 0.68f, 2.04f, 0.64f, 1.9f, 0.55f, 2.0f, 0.53f, 2.3f, 0.58f, 2.6f, 0.53f),
            fitting(DARK_STEEL, poly(2.12f, -0.05f, 2.38f, -0.05f, 2.25f, -0.25f), 0.06f),
        ),
        spinCenter = 1.2f, spinRate = 0f, reach = 2.05f, scale = 1f,
    )

    val katana = WeaponShape(
        listOf(
            grip(NAVY, rect(-0.02f, -0.055f, 0.8f, 0.055f), 0.08f),
            fitting(GOLD, disc(0.83f, 0f, 0.035f, 0.15f, 12), 0.1f),
            WeaponPart(katanaBlade(), STEEL, 0.95f, 0.12f, 0.022f),
        ),
        spinCenter = 1.4f, spinRate = 0f, reach = 1.9f, scale = 1f,
    )

    val claymore = WeaponShape(
        listOf(
            fitting(DARK_STEEL, disc(-0.1f, 0f, 0.11f, 0.11f), 0.12f),
            grip(LEATHER, rect(-0.02f, -0.055f, 0.92f, 0.055f)),
            fitting(DARK_STEEL, poly(0.92f, -0.44f, 0.98f, -0.4f, 0.99f, 0.4f, 0.92f, 0.44f, 1.04f, 0.46f, 1.07f, 0.0f, 1.04f, -0.46f), 0.1f),
            blade(STEEL, 1.05f, -0.13f, 1.35f, -0.13f, 1.4f, -0.11f, 3.4f, -0.09f, 3.8f, 0f, 3.4f, 0.09f, 1.4f, 0.11f, 1.35f, 0.13f, 1.05f, 0.13f),
        ),
        spinCenter = 1.8f, spinRate = 0f, reach = 2.5f, scale = 1f,
    )

    /** A long blade that curves gently away from its edge, edge on +y. */
    private fun katanaBlade(): FloatArray {
        val n = 10
        val x0 = 0.86f
        val x1 = 3.0f
        val edge = ArrayList<Float>()
        val spine = ArrayList<Float>()
        for (i in 0..n) {
            val t = i / n.toFloat()
            val x = MathUtils.lerp(x0, x1 - 0.12f, t)
            val curve = -0.12f * t * t
            val w = MathUtils.lerp(0.085f, 0.06f, t)
            edge += x; edge += curve + w
            spine += x; spine += curve - 0.03f
        }
        val out = ArrayList<Float>()
        out += spine
        out += x1; out += -0.12f + 0.0f
        for (i in n downTo 0) { out += edge[i * 2]; out += edge[i * 2 + 1] }
        return out.toFloatArray()
    }

    fun of(w: Weapon): WeaponShape = when (w) {
        Weapons.gladius -> gladius
        Weapons.axe -> axe
        Weapons.katana -> katana
        Weapons.claymore -> claymore
        else -> knife
    }

    private val triangulator = EarClippingTriangulator()
    private val tmpColor = Color()

    /** Draws [w] as a flat icon fitted in the box, tilted 45°. [silhouette] draws it dark (not owned yet). */
    fun drawIcon(shapes: ShapeRenderer, w: Weapon, x: Float, y: Float, size: Float, silhouette: Boolean, alpha: Float = 1f) {
        val shape = of(w)
        val cx = (shape.minX + shape.maxX) / 2f
        val cy = (shape.minY + shape.maxY) / 2f
        val len = max(shape.maxX - shape.minX, (shape.maxY - shape.minY) * 1.2f)
        val s = size * 1.25f / len
        val cos = MathUtils.cosDeg(45f)
        val sin = MathUtils.sinDeg(45f)
        val ox = x + size / 2f
        val oy = y + size / 2f
        fun tx(px: Float, py: Float) = ox + ((px - cx) * cos - (py - cy) * sin) * s
        fun ty(px: Float, py: Float) = oy + ((px - cx) * sin + (py - cy) * cos) * s
        for (part in shape.parts) {
            val o = part.outline
            val tris = triangulator.computeTriangles(o)
            if (silhouette) tmpColor.set(0.05f, 0.08f, 0.18f, 0.85f * alpha) else tmpColor.set(part.rgb shl 8 or 0xFF).also { it.a = alpha }
            shapes.color = tmpColor
            for (t in 0 until tris.size / 3) {
                val a = tris[t * 3].toInt()
                val b = tris[t * 3 + 1].toInt()
                val c = tris[t * 3 + 2].toInt()
                shapes.triangle(
                    tx(o[a * 2], o[a * 2 + 1]), ty(o[a * 2], o[a * 2 + 1]),
                    tx(o[b * 2], o[b * 2 + 1]), ty(o[b * 2], o[b * 2 + 1]),
                    tx(o[c * 2], o[c * 2 + 1]), ty(o[c * 2], o[c * 2 + 1]),
                )
            }
        }
    }
}

/** GPU meshes for one weapon's parts. */
class WeaponModel(val weapon: Weapon) : Disposable {
    val shape = WeaponShapes.of(weapon)
    val meshes: List<Mesh> = shape.parts.map { Meshes.extrude(it.outline, it.thickness) }

    override fun dispose() = meshes.forEach { it.dispose() }
}
