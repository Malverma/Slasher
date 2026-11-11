package com.slasher.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import com.slasher.SlasherGame
import com.slasher.logic.CutKind
import com.slasher.logic.GemKind
import com.slasher.logic.MaterialCurve
import com.slasher.logic.MaterialKey
import com.slasher.logic.Run
import com.slasher.logic.Weapons
import com.slasher.render.WeaponShapes
import com.slasher.render.World
import com.slasher.ui.ACCENT
import com.slasher.ui.ButtonInput
import com.slasher.ui.DANGER
import com.slasher.ui.SwipeInput
import com.slasher.ui.UiButton
import com.slasher.ui.dim
import com.slasher.ui.panel
import com.slasher.ui.text
import kotlin.math.min

/** A run: slice until the blade breaks (spec §3, §9). */
class GameScreen(private val game: SlasherGame) : ScreenAdapter() {
    private enum class State { PLAYING, PAUSED, REVIVE, GAME_OVER }

    private val a = game.assets
    private val s = game.settings
    private val run = Run(s.weapon, game.debug.startDepth, game.debug.immortal)
    private val world = World(a, s, run)
    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()
    private val autoplay = game.debug.autoplay
    private var autoplayTimer = 0.8f

    private var state = State.PLAYING
    private var w = 1f
    private var h = 1f
    private var time = 0f
    private var overTime = 0f
    private var newRecord = false
    private var coinsEarned = 0
    private var displayScore = 0f
    private var edgeFlash = 0f
    private var banner: MaterialKey? = null
    private var bannerTime = 0f
    private val glyphs = GlyphLayout()

    private val pauseBtn = UiButton(icon = a.btnPause) { state = State.PAUSED }
    private val resumeBtn = UiButton("RESUME", primary = true) { state = State.PLAYING }
    private val restartBtn = UiButton("RESTART") { game.startGame() }
    private val settingsBtn = UiButton("SETTINGS") { game.showSettings { game.setScreen(this) } }
    private val menuBtn = UiButton("MENU") { game.showMenu() }
    private val againBtn = UiButton("RETRY", primary = true) { game.startGame() }
    private val overMenuBtn = UiButton("MENU") { game.showMenu() }
    private val watchAdBtn = UiButton("WATCH AD", primary = true) { watchAd() }
    private val noThanksBtn = UiButton("NO THANKS") { finishGameOver() }

    private var zigzagTime = -1f

    private val input = InputMultiplexer(
        ButtonInput(a, ::activeButtons) { onBack() },
        SwipeInput(world, { 60f * a.u }) { if (state == State.PLAYING && !autoplay) world.swipe(it) },
    )

    init {
        world.onCut = { r ->
            if (r.kind != CutKind.CLEAN) edgeFlash = 1f
        }
        world.onZone = { key ->
            banner = key
            bannerTime = 0f
        }
        if (game.debug.gems) world.features.gemChance = 1f
        world.onGem = { _, coins -> if (coins > 0) s.coins += coins }
        world.onBroken = {
            if (!run.reviveUsed && !autoplay) state = State.REVIVE else finishGameOver()
        }
    }

    private fun activeButtons(): List<UiButton> = when (state) {
        State.PLAYING -> listOf(pauseBtn)
        State.PAUSED -> listOf(resumeBtn, restartBtn, settingsBtn, menuBtn)
        State.REVIVE -> listOf(watchAdBtn, noThanksBtn)
        State.GAME_OVER -> if (overTime > OVER_PANEL_DELAY) listOf(againBtn, overMenuBtn) else emptyList()
    }

    private fun onBack() {
        when (state) {
            State.PLAYING -> state = State.PAUSED
            State.PAUSED -> state = State.PLAYING
            State.REVIVE -> finishGameOver()
            State.GAME_OVER -> game.showMenu()
        }
    }

    /**
     * Placeholder for a rewarded video ad (spec §9.4). No ad SDK is wired up yet, so the repair is granted
     * immediately; plug the ad callback in here later.
     */
    private fun watchAd() {
        run.revive()
        world.revived()
        state = State.PLAYING
        a.play(a.sChime, 0.8f, 0.8f)
    }

    private fun finishGameOver() {
        state = State.GAME_OVER
        overTime = 0f
        coinsEarned = run.coins
        s.coins += coinsEarned
        newRecord = run.score > s.highScore
        if (newRecord) s.highScore = run.score
        if (run.depth.toInt() > s.bestDepth) s.bestDepth = run.depth.toInt()
        if (coinsEarned > 0) a.play(a.sCoins, 0.8f)
        if (autoplay) Gdx.app.log("slasher", "autoplay over: score ${run.score}, depth ${run.depth.toInt()}, cuts ${run.cuts}")
    }

    /** "IRON IS TOO HARD FOR THE GLADIUS. TRY THE BATTLE AXE." when the blade lost to hardness. */
    private fun hint(): List<String>? {
        val failed = run.lastFailed ?: return null
        if (failed.power >= failed.hardness * 0.85f) return null
        val next = Weapons.after(run.weapon) ?: return null
        return listOf("${MaterialCurve.zone(run.depth).name} IS TOO HARD FOR THE ${run.weapon.name}.", "TRY THE ${next.name}.")
    }

    /** Debug: one long touch sweeping across the tower and back, 0.6 s per pass, then lifting after 6 s. */
    private fun zigzag(dt: Float) {
        val sy = Gdx.graphics.height - world.screenYAt(-0.5f)
        if (zigzagTime < 0f) {
            zigzagTime = 0f
            input.touchDown((w * 0.08f).toInt(), sy.toInt(), 0, 0)
            return
        }
        zigzagTime += dt
        val phase = (zigzagTime / 0.6f) % 2f
        val k = if (phase < 1f) phase else 2f - phase
        val x = w * (0.08f + 0.84f * k)
        if (zigzagTime < 6f) input.touchDragged(x.toInt(), (sy + 40f * a.u * MathUtils.sin(zigzagTime * 7f)).toInt(), 0)
        else if (zigzagTime - dt < 6f) {
            input.touchUp(x.toInt(), sy.toInt(), 0, 0)
            Gdx.app.log("slasher", "zigzag: ${run.cuts} cuts in 6 s, depth ${run.depth}")
        }
    }

    // ---- Screen ---------------------------------------------------------------------------------

    override fun show() {
        Gdx.input.inputProcessor = input
        a.updateMusic()
    }

    override fun hide() {
        if (state == State.PLAYING) state = State.PAUSED
    }

    override fun pause() {
        if (state == State.PLAYING) state = State.PAUSED
    }

    override fun resize(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        world.resize(width, height)
        batch.projectionMatrix.setToOrtho2D(0f, 0f, w, h)
        shapes.projectionMatrix.setToOrtho2D(0f, 0f, w, h)
        val u = a.u
        val m = 40f * u
        val top = h - Gdx.graphics.safeInsetTop
        pauseBtn.set(w - m - 130f * u, top - m - 130f * u, 130f * u, 130f * u)
        val bw = 600f * u
        val bh = 140f * u
        val cx = (w - bw) / 2f
        val cy = h / 2f
        resumeBtn.set(cx, cy + 120f * u, bw, bh)
        restartBtn.set(cx, cy - 50f * u, bw, bh)
        settingsBtn.set(cx, cy - 220f * u, bw, bh)
        menuBtn.set(cx, cy - 390f * u, bw, bh)
        againBtn.set(cx, cy - 330f * u, bw, bh)
        overMenuBtn.set(cx, cy - 495f * u, bw, bh)
        watchAdBtn.set(cx, cy - 210f * u, bw, bh)
        noThanksBtn.set(cx, cy - 380f * u, bw, bh)
    }

    override fun render(delta: Float) {
        val dt = min(delta, 1f / 20f)
        time += dt
        if (state == State.GAME_OVER) overTime += dt
        bannerTime += dt
        edgeFlash = (edgeFlash - dt * 2f).coerceAtLeast(0f)
        displayScore += (run.score - displayScore) * min(1f, dt * 8f)
        if (run.score - displayScore < 0.5f) displayScore = run.score.toFloat()

        if (autoplay && state == State.PLAYING) {
            autoplayTimer -= dt
            if (autoplayTimer <= 0f && world.ready) {
                autoplayTimer = 0.35f
                world.swipe(world.botSwipe(0.1f))
            }
        }
        if (game.debug.zigzag && state == State.PLAYING) zigzag(dt)
        world.render(if (state == State.PAUSED) 0f else dt)

        batch.begin()
        drawHud()
        batch.end()
        if (state != State.GAME_OVER) drawEdgeBar()

        batch.begin()
        when (state) {
            State.PAUSED -> drawPause()
            State.REVIVE -> drawRevive()
            State.GAME_OVER -> drawGameOver()
            State.PLAYING -> Unit
        }
        batch.end()
    }

    private fun drawHud() {
        val u = a.u
        val m = 40f * u
        val top = h - Gdx.graphics.safeInsetTop
        if (state == State.GAME_OVER) return

        // Score and streak (top-centre).
        text(batch, a.fontLarge, displayScore.toInt().toString(), w / 2f, top - m - 55f * u)
        if (run.streak >= 2) {
            a.fontSmall.color = ACCENT
            text(batch, a.fontSmall, "×${run.streak} STREAK", w / 2f, top - m - 140f * u)
            a.fontSmall.color = Color.WHITE
        } else {
            text(batch, a.fontSmall, "BEST ${maxOf(s.highScore, run.score)}", w / 2f, top - m - 140f * u)
        }

        // Depth and material (bottom).
        val zone = MaterialCurve.zone(run.depth)
        val bottom = Gdx.graphics.safeInsetBottom + m
        panel(batch, a, (w - 640f * u) / 2f, bottom, 640f * u, 100f * u, 0.45f)
        text(batch, a.fontSmall, "DEPTH ${run.depth.toInt()} · ${zone.name}", w / 2f, bottom + 50f * u)

        // A new material: name and hardness (spec §6.5).
        banner?.let { key ->
            if (bannerTime < 2.2f) {
                val alpha = when {
                    bannerTime < 0.2f -> bannerTime / 0.2f
                    bannerTime > 1.8f -> (2.2f - bannerTime) / 0.4f
                    else -> 1f
                }
                val y = top - 330f * u
                a.fontLarge.setColor(1f, 1f, 1f, alpha)
                text(batch, a.fontLarge, key.name, w / 2f, y)
                a.fontLarge.color = Color.WHITE
                a.fontSmall.setColor(ACCENT.r, ACCENT.g, ACCENT.b, alpha)
                text(batch, a.fontSmall, "HARDNESS ${key.hardness.toInt()}", w / 2f, y - 90f * u)
                a.fontSmall.color = Color.WHITE
            }
        }

        if (state == State.PLAYING) activeButtons().forEach { it.draw(batch, a) }
    }

    /** Edge meter (top-left): the weapon's durability for this run (spec §9.2). */
    private fun drawEdgeBar() {
        val u = a.u
        val m = 40f * u
        val top = h - Gdx.graphics.safeInsetTop
        val bw = 230f * u
        val bh = 30f * u
        val icon = 100f * u
        val x = m + icon + 16f * u
        val y = top - m - icon / 2f - bh / 2f
        val f = run.edgeFraction
        val low = f < 0.25f
        val pulse = if (low) 0.55f + 0.45f * MathUtils.sin(time * 10f) else 1f

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.setColor(0.04f, 0.07f, 0.18f, 0.45f)
        shapes.rect(m - 8f * u, top - m - icon - 8f * u, icon + bw + 40f * u, icon + 16f * u)
        WeaponShapes.drawIcon(shapes, run.weapon, m, top - m - icon, icon, false)
        shapes.setColor(0f, 0f, 0f, 0.35f)
        shapes.rect(x, y, bw, bh)
        val c = if (low) DANGER else Color.WHITE
        shapes.setColor(c.r, c.g, c.b, pulse)
        shapes.rect(x, y, bw * f, bh)
        if (edgeFlash > 0f) {
            shapes.setColor(DANGER.r, DANGER.g, DANGER.b, edgeFlash * 0.6f)
            shapes.rect(x, y, bw, bh)
        }
        // Gem boosts: a shrinking bar each, under the Edge panel.
        val boosts = listOf(GemKind.BLUE to run.hasteTime, GemKind.RED to run.sharpTime).filter { it.second > 0f }
        boosts.forEachIndexed { i, (kind, left) ->
            val by = top - m - icon - 70f * u - i * 64f * u
            val c = Color(kind.rgb shl 8 or 0xFF)
            shapes.setColor(0.04f, 0.07f, 0.18f, 0.45f)
            shapes.rect(m - 8f * u, by - 8f * u, icon + bw + 40f * u, 52f * u)
            shapes.setColor(c.r, c.g, c.b, if (left < 2f) 0.55f + 0.45f * MathUtils.sin(time * 12f) else 1f)
            shapes.rect(m + 150f * u, by + 8f * u, (bw + icon - 150f * u) * left / Run.BOOST_TIME, 20f * u)
        }
        shapes.end()
        batch.begin()
        text(batch, a.fontTiny, "EDGE", x, y + bh + 26f * u, Align.left)
        boosts.forEachIndexed { i, (kind, _) ->
            val by = top - m - icon - 70f * u - i * 64f * u
            text(batch, a.fontTiny, if (kind == GemKind.BLUE) "HASTE" else "SHARP", m + 4f * u, by + 18f * u, Align.left)
        }
        batch.end()
    }

    private fun drawPause() {
        val u = a.u
        dim(batch, a, w, h, 0.5f)
        val pw = 760f * u
        val ph = 980f * u
        panel(batch, a, (w - pw) / 2f, h / 2f - 470f * u, pw, ph)
        text(batch, a.fontLarge, "PAUSED", w / 2f, h / 2f + 370f * u)
        activeButtons().forEach { it.draw(batch, a) }
    }

    private fun drawRevive() {
        val u = a.u
        dim(batch, a, w, h, 0.55f)
        val pw = 800f * u
        val ph = 900f * u
        panel(batch, a, (w - pw) / 2f, h / 2f - 470f * u, pw, ph)
        text(batch, a.fontLarge, "BLADE BROKEN", w / 2f, h / 2f + 330f * u)
        text(batch, a.fontMedium, "REPAIR BLADE?", w / 2f, h / 2f + 160f * u)
        text(batch, a.fontSmall, "WATCH AN AD TO RESTORE", w / 2f, h / 2f + 30f * u)
        text(batch, a.fontSmall, "40% OF YOUR EDGE", w / 2f, h / 2f - 30f * u)
        activeButtons().forEach { it.draw(batch, a) }
    }

    private fun drawGameOver() {
        if (overTime < OVER_PANEL_DELAY) return
        val u = a.u
        val t = MathUtils.clamp((overTime - OVER_PANEL_DELAY) / 0.35f, 0f, 1f)
        dim(batch, a, w, h, 0.45f * t)
        val pw = 860f * u
        val ph = 1180f * u
        val slide = (1f - Interpolation.pow3Out.apply(t)) * 200f * u
        panel(batch, a, (w - pw) / 2f, h / 2f - 590f * u - slide, pw, ph, 0.85f * t)
        val cy = h / 2f - slide
        text(batch, a.fontLarge, "BLADE BROKEN", w / 2f, cy + 470f * u)
        a.fontSmall.color = DANGER
        text(batch, a.fontSmall, "ON ${MaterialCurve.zone(run.depth).name}", w / 2f, cy + 385f * u)
        a.fontSmall.color = Color.WHITE
        text(batch, a.fontTitle, run.score.toString(), w / 2f, cy + 250f * u)
        if (newRecord) {
            a.fontMedium.color = ACCENT
            text(batch, a.fontMedium, "NEW HIGH SCORE!", w / 2f, cy + 120f * u)
            a.fontMedium.color = Color.WHITE
        } else {
            text(batch, a.fontSmall, "BEST ${s.highScore}", w / 2f, cy + 120f * u)
        }
        text(batch, a.fontSmall, "DEPTH ${run.depth.toInt()}   ·   BEST STREAK ${run.bestStreak}", w / 2f, cy + 40f * u)
        val cs = 64f * u
        val label = "+${coinsEarned + run.gemCoins} COINS"
        a.fontMedium.color = ACCENT
        val lw = glyphs.apply { setText(a.fontMedium, label) }.width
        batch.draw(a.coin, w / 2f - (lw + cs + 16f * u) / 2f, cy - 80f * u - cs / 2f, cs, cs)
        text(batch, a.fontMedium, label, w / 2f - (lw + cs + 16f * u) / 2f + cs + 16f * u, cy - 80f * u, Align.left)
        a.fontMedium.color = Color.WHITE
        hint()?.forEachIndexed { i, line -> text(batch, a.fontTiny, line, w / 2f, cy - 175f * u - i * 46f * u) }
        activeButtons().forEach { it.draw(batch, a) }
    }

    override fun dispose() {
        world.dispose()
        batch.dispose()
        shapes.dispose()
    }

    companion object {
        const val OVER_PANEL_DELAY = 0.8f
    }
}
