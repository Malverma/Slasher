package com.slasher.logic

import kotlin.math.max

/** One run's score, depth and blade (spec §3, §8). */
class Run(val weapon: Weapon, startDepth: Float = 0f, private val immortal: Boolean = false) {
    var score = 0
        private set
    var depth = startDepth
        private set
    var edge = weapon.maxEdge
        private set
    /** Clean cuts in a row. */
    var streak = 0
        private set
    var bestStreak = 0
        private set
    var reviveUsed = false
        private set
    var cuts = 0
        private set
    /** The last cut that did not go through, for the game-over hint. */
    var lastFailed: CutResult? = null
        private set

    /** Seconds left on the blue gem's haste and the red gem's sharpness. */
    var hasteTime = 0f
        private set
    var sharpTime = 0f
        private set
    /** Coins from gold gems this run (already added to the balance when found). */
    var gemCoins = 0
        private set

    val hasted get() = hasteTime > 0f
    /** Sharpness multiplier from the red gem. */
    val sharpBoost get() = if (sharpTime > 0f) SHARP_BOOST else 1f

    val zoneIndex get() = MaterialCurve.zoneIndex(depth)
    val edgeFraction get() = (edge / weapon.maxEdge).coerceIn(0f, 1f)
    val broken get() = edge <= 0f
    /** 1 coin per 100 points (spec §8). */
    val coins get() = score / 100

    /**
     * Applies a resolved cut that removed [removedDepth] of tower. Returns the bonus for reaching a new
     * material, or 0.
     */
    fun apply(r: CutResult, removedDepth: Float): Int {
        cuts++
        if (!immortal) edge = max(0f, edge - r.edgeLost)
        if (r.kind != CutKind.CLEAN) {
            streak = 0
            lastFailed = r
            return 0
        }
        score += r.points
        streak++
        bestStreak = max(bestStreak, streak)
        val zoneBefore = zoneIndex
        depth += removedDepth
        if (zoneIndex > zoneBefore) {
            score += ZONE_BONUS
            return ZONE_BONUS
        }
        return 0
    }

    /** Applies a gem's effect. Gold returns the coins to add to the balance. */
    fun collect(kind: GemKind): Int = when (kind) {
        GemKind.GOLD -> { gemCoins += GOLD_COINS; GOLD_COINS }
        GemKind.BLUE -> { hasteTime = BOOST_TIME; 0 }
        GemKind.RED -> { sharpTime = BOOST_TIME; 0 }
    }

    fun tick(dt: Float) {
        hasteTime = max(0f, hasteTime - dt)
        sharpTime = max(0f, sharpTime - dt)
    }

    fun revive() {
        reviveUsed = true
        edge = weapon.maxEdge * 0.4f
    }

    companion object {
        const val ZONE_BONUS = 250
        const val GOLD_COINS = 15
        const val BOOST_TIME = 8f
        const val SHARP_BOOST = 1.6f
    }
}
