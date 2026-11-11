package com.slasher.logic

import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/** A glowing weak spot on the tower's surface (spec §6.4). */
class WeakSpot(val pos: Vector3, val radius: Float)

/** The three gems that can hide inside the tower. */
enum class GemKind(val rgb: Int, val label: String) {
    /** +15 coins. */
    GOLD(0xFFC93C, "+15 COINS"),
    /** Everything moves faster and every swipe counts as full speed, for a short time. */
    BLUE(0x3FA9FF, "HASTE!"),
    /** Extra sharpness for a short time. */
    RED(0xFF3B4F, "SHARP!"),
}

/** A gem hidden in the middle of the tower. A cut through it pops it out. */
class Gem(val kind: GemKind, val pos: Vector3)

/** A crack left by a stuck cut: a weak line along [plane], from [start] along [dir] for [length]. */
class Crack(val plane: CutPlane, val start: Vector3, val dir: Vector3, val length: Float)

/** Grain and weak spots of the current tower part (spec §6.3, §6.4). */
class TowerFeatures(private val random: Random) {
    /** Screen angle of the grain lines, degrees; cutting at this angle is "with the grain". */
    var grainAngle = 20f
        private set
    val spots = ArrayList<WeakSpot>()
    var crack: Crack? = null
    var gem: Gem? = null
    /** Chance that a new part hides a gem; the desktop debug hook raises it. */
    var gemChance = GEM_CHANCE

    fun grainPlane(): CutPlane = CutPlane.fromLine(grainAngle, 0f)

    /** Angle between [plane] and the grain, degrees. */
    fun grainOffset(plane: CutPlane) = plane.angleTo(grainPlane())

    /** 1.0 (no help) up to [crackMax] (along a crack) or 2.0 (through a weak spot's centre). */
    fun weakFactor(plane: CutPlane, crackMax: Float): Float {
        var best = 1f
        for (s in spots) {
            val dist = abs(plane.distance(s.pos))
            best = max(best, 1f + (1f - dist / (2f * s.radius)).coerceIn(0f, 1f))
        }
        crack?.let { c ->
            val angle = plane.angleTo(c.plane)
            val offset = abs(plane.distance(c.start))
            if (angle < 8f && offset < 0.1f) {
                val closeness = 1f - max(angle / 8f, offset / 0.1f)
                best = max(best, 1f + (crackMax - 1f) * closeness)
            }
        }
        return best
    }

    /** Does a cut along [plane] go through the gem? */
    fun gemHit(plane: CutPlane): Boolean = gem?.let { abs(plane.distance(it.pos)) < GEM_HIT } ?: false

    /** Spots above a clean cut leave with the slice, and so does a gem the cut missed. */
    fun removeAbove(plane: CutPlane) {
        spots.removeAll { plane.distance(it.pos) > 0f }
        if (gem?.let { plane.distance(it.pos) > 0f } == true) gem = null
        crack = null
    }

    /** A new tower part has reconnected: turn the grain and place new weak spots. */
    fun regenerate(depth: Float) {
        val step = 10f + random.nextFloat() * 25f
        var g = grainAngle + if (random.nextBoolean()) step else -step
        if (abs(g) > 50f) g = Math.signum(g) * (100f - abs(g))
        if (abs(g) < 5f) g = if (g < 0f) -5f else 5f
        grainAngle = g
        crack = null
        spots.clear()
        val count = when {
            depth < 25f -> 2
            depth < 60f -> 1 + random.nextInt(2)
            depth < 120f -> if (random.nextFloat() < 0.5f) 1 else 0
            else -> if (random.nextFloat() < 0.3f) 1 else 0
        }
        repeat(count) { spots += randomSpot() }
        if (gem == null && random.nextFloat() < gemChance) gem = randomGem()
    }

    private fun randomGem(): Gem {
        val roll = random.nextFloat()
        val kind = when {
            roll < 0.5f -> GemKind.GOLD
            roll < 0.75f -> GemKind.BLUE
            else -> GemKind.RED
        }
        val pos = Vector3(-0.15f + random.nextFloat() * 0.3f, -1.1f + random.nextFloat() * 0.75f, -0.15f + random.nextFloat() * 0.3f)
        return Gem(kind, pos)
    }

    companion object {
        /** Chance that a new tower part hides a gem. */
        const val GEM_CHANCE = 0.12f
        /** A cut this close to a gem's centre finds it. */
        const val GEM_HIT = 0.14f
    }

    private fun randomSpot(): WeakSpot {
        val y = -1.25f + random.nextFloat() * 1.05f
        val t = -0.3f + random.nextFloat() * 0.6f
        val h = Tower.HALF
        // Only the two faces turned toward the camera (+x and +z) get spots.
        val pos = if (random.nextBoolean()) Vector3(h, y, t) else Vector3(t, y, h)
        return WeakSpot(pos, 0.08f + random.nextFloat() * 0.04f)
    }
}
