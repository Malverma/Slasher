package com.slasher.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.slasher.SlasherGame
import com.slasher.render.Sky
import com.slasher.shop.Catalog
import com.slasher.ui.ButtonInput
import com.slasher.ui.UiButton
import com.slasher.ui.panel
import com.slasher.ui.text

/** Sound, music, vibration, trail and slow-motion toggles (spec §9.5). [back] returns to the menu or the paused game. */
class SettingsScreen(private val game: SlasherGame, private val back: () -> Unit) : ScreenAdapter() {
    private val a = game.assets
    private val s = game.settings
    private val sky = Sky(Catalog.sky(s.skyId))
    private val batch = SpriteBatch()
    private var w = 1f
    private var h = 1f
    private var disposed = false

    private val soundBtn = UiButton { s.sound = !s.sound; refresh() }
    private val musicBtn = UiButton { s.music = !s.music; a.updateMusic(); refresh() }
    private val vibrateBtn = UiButton { s.vibration = !s.vibration; refresh(); a.vibrate(40) }
    private val trailBtn = UiButton { s.trail = !s.trail; refresh() }
    private val slowMoBtn = UiButton { s.slowMo = !s.slowMo; refresh() }
    private val backBtn = UiButton("BACK", primary = true) { goBack() }
    private val toggles = listOf(soundBtn, musicBtn, vibrateBtn, trailBtn, slowMoBtn)
    private val buttons = toggles + backBtn
    private val input = ButtonInput(a, { buttons }) { goBack() }

    init {
        refresh()
    }

    private fun refresh() {
        soundBtn.label = "SOUND: ${onOff(s.sound)}"
        musicBtn.label = "MUSIC: ${onOff(s.music)}"
        vibrateBtn.label = "VIBRATION: ${onOff(s.vibration)}"
        trailBtn.label = "SWIPE TRAIL: ${onOff(s.trail)}"
        slowMoBtn.label = "SLOW-MO: ${onOff(s.slowMo)}"
    }

    private fun onOff(v: Boolean) = if (v) "ON" else "OFF"

    private fun goBack() {
        back()
        if (game.screen !== this) dispose()
    }

    override fun show() {
        Gdx.input.inputProcessor = input
    }

    override fun resize(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        batch.projectionMatrix.setToOrtho2D(0f, 0f, w, h)
        val u = a.u
        val bw = 680f * u
        val bh = 130f * u
        val x = (w - bw) / 2f
        val cy = h / 2f
        toggles.forEachIndexed { i, b -> b.set(x, cy + 330f * u - i * 160f * u, bw, bh) }
        backBtn.set(x, cy - 520f * u, bw, bh)
    }

    override fun render(delta: Float) {
        sky.render(w, h, 0f, delta)
        val u = a.u
        batch.begin()
        val pw = 820f * u
        val ph = 1300f * u
        panel(batch, a, (w - pw) / 2f, h / 2f - 590f * u, pw, ph)
        text(batch, a.fontLarge, "SETTINGS", w / 2f, h / 2f + 580f * u)
        buttons.forEach { it.draw(batch, a) }
        batch.end()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        sky.dispose()
        batch.dispose()
    }
}
