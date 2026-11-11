package com.slasher.logic

import com.badlogic.gdx.math.MathUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class CutKind { CLEAN, STUCK, BOUNCE }

/** Everything about one cut that the game needs to show and score (spec §6, §8). */
class CutResult(
    val kind: CutKind,
    val power: Float,
    val hardness: Float,
    val speedFactor: Float,
    val grainFactor: Float,
    val weakFactor: Float,
    val traitFactor: Float,
    /** How far the blade got, 0..1 of the way through. */
    val penetration: Float,
    val edgeLost: Float,
    val points: Int,
    val withGrain: Boolean,
    val weakHit: Boolean,
    val shaving: Boolean,
) {
    val perfect get() = kind == CutKind.CLEAN && withGrain && weakHit
}

/** What the player did, measured by the game. */
class CutInput(
    val hardness: Float,
    /** Swipe angle on screen, degrees from horizontal (unsigned). */
    val screenTiltDeg: Float,
    /** Angle between the cut and the grain, degrees. */
    val grainAngleDeg: Float,
    val weakFactor: Float,
    /** Swipe speed in reference pixels per second (1080-wide screen). */
    val swipeSpeed: Float,
    /** Volume of the slice. */
    val volume: Float,
    /** Slice thickness at its thickest, measured along the plane normal. */
    val thickness: Float,
    /** Did the swipe cross (most of) the tower? */
    val fullSwipe: Boolean,
    /** Clean cuts in a row before this one. */
    val streak: Int,
    /** Sharpness multiplier from a red gem. */
    val sharpBoost: Float = 1f,
)

object CutRules {
    const val STUCK_RATIO = 0.6f
    const val SHAVING_THICKNESS = 0.08f
    const val SHAVING_POINTS = 40
    const val WITH_GRAIN_DEG = 10f
    const val WEAK_HIT = 1.5f

    fun speedFactor(w: Weapon, speed: Float): Float {
        val t = ((speed - 600f) / 2400f).coerceIn(0f, 1f)
        return if (w.trait == Trait.QUICK) MathUtils.lerp(0.7f, 1.3f, t) else MathUtils.lerp(0.85f, 1.15f, t)
    }

    fun grainFactor(w: Weapon, angleDeg: Float): Float {
        val best = when (w.trait) {
            Trait.PRECISION -> 2.2f
            Trait.CLEAVE -> 1.4f
            else -> 1.8f
        }
        val t = ((angleDeg - 5f) / 20f).coerceIn(0f, 1f)
        return MathUtils.lerp(best, 1f, t)
    }

    fun traitFactor(w: Weapon, screenTiltDeg: Float, streak: Int): Float = when (w.trait) {
        Trait.CLEAVE -> if (screenTiltDeg < 25f) 1.3f else 1f
        Trait.MOMENTUM -> 1f + min(0.4f, 0.05f * streak)
        else -> 1f
    }

    /** Best crack bonus for this weapon (spec §7, Battle Axe). */
    fun crackFactor(w: Weapon) = if (w.trait == Trait.CLEAVE) 2.3f else 2f

    fun resolve(w: Weapon, input: CutInput): CutResult {
        val sf = speedFactor(w, input.swipeSpeed)
        val gf = grainFactor(w, input.grainAngleDeg)
        val tf = traitFactor(w, input.screenTiltDeg, input.streak)
        val power = w.sharpness * input.sharpBoost * sf * gf * input.weakFactor * tf
        val h = input.hardness
        var kind = when {
            power >= h -> CutKind.CLEAN
            power >= STUCK_RATIO * h -> CutKind.STUCK
            else -> CutKind.BOUNCE
        }
        if (kind == CutKind.CLEAN && !input.fullSwipe) kind = CutKind.STUCK
        val penetration = when (kind) {
            CutKind.CLEAN -> 1f
            CutKind.STUCK -> min(0.85f, power / h)
            CutKind.BOUNCE -> 0f
        }
        val withGrain = input.grainAngleDeg <= WITH_GRAIN_DEG
        val weakHit = input.weakFactor >= WEAK_HIT
        val shaving = kind == CutKind.CLEAN && input.thickness < SHAVING_THICKNESS
        val ratio = h / (w.sharpness * input.sharpBoost)
        val edgeLost = when (kind) {
            CutKind.CLEAN -> when {
                shaving -> 0f
                withGrain || weakHit -> max(0.3f, 1.5f * ratio) / 2f
                else -> max(0.3f, 1.5f * ratio)
            }
            CutKind.STUCK -> 3f * ratio
            CutKind.BOUNCE -> 8f + 3f * ratio
        }
        val points = if (kind == CutKind.CLEAN) {
            val base = max(1, (input.volume * h * 10f).roundToInt())
            val bonus = when {
                withGrain && weakHit -> 2f
                else -> 1f + (if (withGrain) 0.5f else 0f) + (if (weakHit) 0.5f else 0f)
            }
            val streakMul = 1f + min(1f, 0.1f * input.streak)
            (base * bonus * streakMul).roundToInt() + if (shaving) SHAVING_POINTS else 0
        } else 0
        return CutResult(kind, power, h, sf, gf, input.weakFactor, tf, penetration, edgeLost, points, withGrain, weakHit, shaving)
    }
}
