package com.slasher.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import com.slasher.SlasherGame
import com.slasher.logic.Run
import com.slasher.render.World
import com.slasher.ui.ButtonInput
import com.slasher.ui.UiButton
import com.slasher.ui.text
import kotlin.math.min

/** Title, records, Play, Settings and Shop over a tower that slices itself (spec §9.1). */
class MenuScreen(private val game: SlasherGame) : ScreenAdapter() {
    private val a = game.assets
    private var world = newWorld()
    private val batch = SpriteBatch()
    private var w = 1f
    private var h = 1f
    private var time = 0f
    private val glyphs = GlyphLayout()
    private var builtFor = ""

    private val playBtn = UiButton("PLAY", primary = true) { game.startGame() }
    private val settingsBtn = UiButton("SETTINGS") { game.showSettings { game.setScreen(this) } }
    private val shopBtn = UiButton("SHOP", labelIcon = a.coin) { game.showShop { game.setScreen(this) } }
    private val buttons = listOf(playBtn, settingsBtn, shopBtn)
    private val input = ButtonInput(a, { buttons }) { Gdx.app.exit() }

    private fun newWorld(): World {
        builtFor = game.settings.weaponId + game.settings.skyId
        return World(a, game.settings, Run(game.settings.weapon, immortal = true), attract = true, topLift = 0.2f)
    }

    override fun show() {
        // Rebuild the backdrop if the shop changed the weapon or background.
        if (builtFor != game.settings.weaponId + game.settings.skyId) {
            world.dispose()
            world = newWorld()
            world.resize(w.toInt(), h.toInt())
        }
        Gdx.input.inputProcessor = input
        a.updateMusic()
    }

    override fun resize(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        world.resize(width, height)
        batch.projectionMatrix.setToOrtho2D(0f, 0f, w, h)
        val u = a.u
        val bw = 620f * u
        val bh = 150f * u
        val bottom = Gdx.graphics.safeInsetBottom + 110f * u
        playBtn.set((w - bw) / 2f, bottom + 2 * (bh + 36f * u), bw, bh)
        settingsBtn.set((w - bw) / 2f, bottom + bh + 36f * u, bw, bh)
        shopBtn.set((w - bw) / 2f, bottom, bw, bh)
    }

    override fun render(delta: Float) {
        val dt = min(delta, 1f / 20f)
        time += dt
        world.render(dt)

        val u = a.u
        val top = h - Gdx.graphics.safeInsetTop
        batch.begin()
        val bob = MathUtils.sin(time * 1.6f) * 10f * u
        text(batch, a.fontTitle, "SLASHER", w / 2f, top - 250f * u + bob)
        text(batch, a.fontSmall, "HIGH SCORE  ${game.settings.highScore}", w / 2f, top - 380f * u)
        text(batch, a.fontSmall, "BEST DEPTH  ${game.settings.bestDepth}", w / 2f, top - 440f * u)
        buttons.forEach { it.draw(batch, a) }

        // Coin balance, top-right.
        val cs = 70f * u
        val m = 40f * u
        glyphs.setText(a.fontMedium, game.settings.coins.toString())
        val cx = w - m - glyphs.width
        text(batch, a.fontMedium, game.settings.coins.toString(), cx, top - m - cs / 2f, Align.left)
        batch.draw(a.coin, cx - cs - 14f * u, top - m - cs, cs, cs)
        batch.end()
    }

    override fun dispose() {
        world.dispose()
        batch.dispose()
    }
}
