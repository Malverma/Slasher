package com.slasher.logic

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils

/** A material at one key depth (spec §6.5). Colors are 0xRRGGBB. */
class MaterialKey(
    val depth: Float,
    val name: String,
    val hardness: Float,
    val friction: Float,
    val base: Int,
    val accent: Int,
    val cutColor: Int,
    val roughness: Float,
    val metal: Float,
    val translucency: Float,
    /** Contrast of the grain lines. */
    val grainStrength: Float,
    /** Grain lines per unit, measured across the lines. */
    val grainFreq: Float,
    /** Amount of surface mottling (noise texture that does not follow the grain). */
    val mottle: Float,
    val glow: Float = 0f,
    /** Which cut sound family the material uses. */
    val sound: CutSound,
)

enum class CutSound { SOFT, WOOD, HARD, METAL }

/** A material blended for one depth. Colors are r, g, b in 0..1. */
class MaterialSample {
    var name = ""
    var hardness = 0f
    var friction = 0f
    val base = FloatArray(3)
    val accent = FloatArray(3)
    val cut = FloatArray(3)
    var roughness = 0f
    var metal = 0f
    var translucency = 0f
    var grainStrength = 0f
    var grainFreq = 0f
    var mottle = 0f
    var glow = 0f

    fun set(o: MaterialSample) = apply {
        name = o.name
        hardness = o.hardness
        friction = o.friction
        o.base.copyInto(base)
        o.accent.copyInto(accent)
        o.cut.copyInto(cut)
        roughness = o.roughness
        metal = o.metal
        translucency = o.translucency
        grainStrength = o.grainStrength
        grainFreq = o.grainFreq
        mottle = o.mottle
        glow = o.glow
    }
}

/** Hardness and look by depth, blended smoothly between key depths (spec §6.5). */
object MaterialCurve {
    val keys = listOf(
        MaterialKey(0f, "SOAP", 6f, 0.15f, 0x9A4BE0, 0xC28BF2, 0xD9B3FF, 0.55f, 0f, 0.6f, 0.5f, 4.5f, 0.5f, sound = CutSound.SOFT),
        MaterialKey(12f, "CLAY", 11f, 0.16f, 0xC8693F, 0xA9512C, 0xE08B5E, 0.85f, 0f, 0.1f, 0.55f, 5f, 0.9f, sound = CutSound.SOFT),
        MaterialKey(25f, "WOOD", 17f, 0.13f, 0xD9B27C, 0x8A5A2B, 0xF0D3A0, 0.7f, 0f, 0.05f, 0.75f, 7f, 0.4f, sound = CutSound.WOOD),
        MaterialKey(40f, "ICE", 24f, 0.05f, 0x9FD8F5, 0xE8F8FF, 0xDDF4FF, 0.15f, 0.1f, 0.8f, 0.55f, 3.5f, 0.5f, sound = CutSound.HARD),
        MaterialKey(55f, "STONE", 32f, 0.1f, 0xA6A9AE, 0x5B5F66, 0xD3D5D8, 0.6f, 0f, 0.05f, 0.6f, 4f, 1.2f, sound = CutSound.HARD),
        MaterialKey(75f, "IRON", 42f, 0.08f, 0x5A5450, 0x8A4B2A, 0xB8B4AE, 0.65f, 0.55f, 0f, 0.5f, 6f, 1.0f, sound = CutSound.METAL),
        MaterialKey(95f, "STEEL", 54f, 0.05f, 0xC9CED6, 0x9AA2AE, 0xF2F5FA, 0.18f, 0.95f, 0f, 0.45f, 9f, 0.2f, sound = CutSound.METAL),
        MaterialKey(120f, "OBSIDIAN", 68f, 0.05f, 0x1A1424, 0x6A3FA0, 0x3A2E4F, 0.08f, 0.3f, 0f, 0.55f, 4.5f, 0.6f, sound = CutSound.HARD),
        MaterialKey(150f, "DIAMOND", 85f, 0.04f, 0xDDF2FF, 0xFFFFFF, 0xFFFFFF, 0.03f, 0.2f, 0.9f, 0.45f, 5f, 0.3f, sound = CutSound.HARD),
        MaterialKey(180f, "STARSTONE", 85f, 0.05f, 0x241B4A, 0x7CF7FF, 0x4B3B8A, 0.3f, 0.2f, 0f, 0.65f, 4.5f, 1.0f, glow = 0.8f, sound = CutSound.METAL),
    )

    /** Extra hardness per unit of depth past the last key: the endless part. */
    const val ENDLESS_HARDNESS = 0.4f

    fun zoneIndex(depth: Float): Int = keys.indexOfLast { it.depth <= depth }.coerceAtLeast(0)

    fun zone(depth: Float): MaterialKey = keys[zoneIndex(depth)]

    fun hardness(depth: Float): Float {
        val last = keys.last()
        if (depth >= last.depth) return last.hardness + ENDLESS_HARDNESS * (depth - last.depth)
        val i = zoneIndex(depth)
        val a = keys[i]
        val b = keys[i + 1]
        return MathUtils.lerp(a.hardness, b.hardness, (depth - a.depth) / (b.depth - a.depth))
    }

    fun sample(depth: Float, out: MaterialSample = MaterialSample()): MaterialSample {
        val last = keys.last()
        val i = zoneIndex(depth)
        val a = keys[i]
        val b = keys.getOrNull(i + 1) ?: last
        val t = if (b === a) 0f else ((depth - a.depth) / (b.depth - a.depth)).coerceIn(0f, 1f)
        out.name = a.name
        out.hardness = hardness(depth)
        out.friction = MathUtils.lerp(a.friction, b.friction, t)
        mix(a.base, b.base, t, out.base)
        mix(a.accent, b.accent, t, out.accent)
        mix(a.cutColor, b.cutColor, t, out.cut)
        out.roughness = MathUtils.lerp(a.roughness, b.roughness, t)
        out.metal = MathUtils.lerp(a.metal, b.metal, t)
        out.translucency = MathUtils.lerp(a.translucency, b.translucency, t)
        out.grainStrength = MathUtils.lerp(a.grainStrength, b.grainStrength, t)
        out.grainFreq = MathUtils.lerp(a.grainFreq, b.grainFreq, t)
        out.mottle = MathUtils.lerp(a.mottle, b.mottle, t)
        out.glow = MathUtils.lerp(a.glow, b.glow, t)
        if (depth > last.depth) {
            // Starstone keeps shifting through hues forever.
            val shift = (depth - last.depth) * 3f
            hueShift(out.base, shift)
            hueShift(out.accent, shift)
            hueShift(out.cut, shift)
        }
        return out
    }

    private val tmpA = Color()
    private val tmpB = Color()
    private val hsv = FloatArray(3)

    private fun mix(a: Int, b: Int, t: Float, out: FloatArray) {
        tmpA.set(a shl 8 or 0xFF).lerp(tmpB.set(b shl 8 or 0xFF), t)
        out[0] = tmpA.r
        out[1] = tmpA.g
        out[2] = tmpA.b
    }

    private fun hueShift(c: FloatArray, degrees: Float) {
        tmpA.set(c[0], c[1], c[2], 1f).toHsv(hsv)
        tmpA.fromHsv((hsv[0] + degrees) % 360f, hsv[1], hsv[2])
        c[0] = tmpA.r
        c[1] = tmpA.g
        c[2] = tmpA.b
    }
}
