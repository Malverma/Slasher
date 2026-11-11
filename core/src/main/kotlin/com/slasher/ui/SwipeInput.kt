package com.slasher.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.TimeUtils
import com.slasher.logic.Tower
import com.slasher.render.Swipe
import com.slasher.render.World
import kotlin.math.abs

/**
 * Turns finger drags into cuts (spec §5). Swiping is continuous: every time the finger crosses the tower
 * a cut fires and the next stroke starts right there, so right–left–right without lifting makes three cuts.
 * Turning back before crossing also starts a new stroke at the turning point. Lifting the finger cuts
 * along whatever stroke has not fired yet.
 */
class SwipeInput(private val world: World, private val minLength: () -> Float, private val onSwipe: (Swipe) -> Unit) : InputAdapter() {
    private var active = false
    /** Where the current stroke began. */
    private val start = Vector2()
    private var startTime = 0L
    private val last = Vector2()
    /** Furthest point along the current horizontal direction, to notice when the finger turns back. */
    private val extreme = Vector2()
    private var extremeTime = 0L
    private var direction = 0f
    private val span = Vector2()
    private val p = Vector2()

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pointer != 0) return false
        active = true
        p.set(screenX.toFloat(), Gdx.graphics.height - screenY.toFloat())
        beginStroke(p, TimeUtils.millis())
        world.trailOnGrain = false
        world.fingerTrail.add(p, p)
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (!active || pointer != 0) return false
        p.set(screenX.toFloat(), Gdx.graphics.height - screenY.toFloat())
        world.fingerTrail.add(p, p)
        last.set(p)
        val now = TimeUtils.millis()

        // Track the horizontal direction; a clear turn-back starts a new stroke at the turning point.
        val dx = last.x - extreme.x
        if (direction == 0f && abs(dx) > JITTER * u()) direction = Math.signum(dx)
        if (direction != 0f) {
            if (dx * direction > 0f) {
                extreme.set(last)
                extremeTime = now
            } else if (abs(dx) > TURN * u()) {
                beginStroke(Vector2(extreme), extremeTime)
                last.set(p)
                direction = Math.signum(dx)
                extreme.set(p)
                extremeTime = now
            }
        }

        world.hintSwipe(start, last)
        if (crossedTower()) {
            fire(true, now)
            beginStroke(last, now)
        }
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (!active || pointer != 0) return false
        active = false
        last.set(screenX.toFloat(), Gdx.graphics.height - screenY.toFloat())
        if (last.dst(start) >= minLength()) fire(false, TimeUtils.millis())
        return true
    }

    private fun beginStroke(at: Vector2, time: Long) {
        start.set(at)
        last.set(at)
        extreme.set(at)
        startTime = time
        extremeTime = time
        direction = 0f
    }

    /** The stroke started off one side of the tower and is now past the other, somewhere within its height. */
    private fun crossedTower(): Boolean {
        val midY = (start.y + last.y) / 2f
        val top = world.screenYAt(Tower.TOP + 0.3f)
        val bottom = world.screenYAt(Tower.TOP - Tower.MAX_CUT_DEPTH - 0.6f)
        if (midY > top || midY < bottom) return false
        world.towerScreenSpan(Tower.TOP - 0.6f, span)
        return (start.x < span.x && last.x > span.y) || (start.x > span.y && last.x < span.x)
    }

    private fun fire(crossed: Boolean, now: Long) {
        world.hintSwipe(start, last)
        val seconds = ((now - startTime) / 1000f).coerceAtLeast(0.016f)
        onSwipe(Swipe(Vector2(start), Vector2(last), last.dst(start) / seconds, crossed))
    }

    private fun u() = Gdx.graphics.width / 1080f

    companion object {
        /** Movement (reference px) that sets the stroke's direction. */
        const val JITTER = 12f
        /** How far back (reference px) the finger must move to count as turning around. */
        const val TURN = 45f
    }
}
