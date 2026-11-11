package com.slasher.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Align
import com.slasher.SlasherGame
import com.slasher.render.Sky
import com.slasher.render.WeaponShapes
import com.slasher.shop.Catalog
import com.slasher.shop.ShopItem
import com.slasher.shop.ShopTab
import com.slasher.ui.ACCENT
import com.slasher.ui.ButtonInput
import com.slasher.ui.TextSize
import com.slasher.ui.UiButton
import com.slasher.ui.panel
import com.slasher.ui.text

/** Spend coins on backgrounds and weapons (spec §10). [back] returns to the menu. */
class ShopScreen(
    private val game: SlasherGame,
    initialTab: ShopTab = ShopTab.BACKGROUNDS,
    private val back: () -> Unit,
) : ScreenAdapter() {
    private val a = game.assets
    private val s = game.settings
    private val sky = Sky(Catalog.sky(s.skyId))
    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()
    private var w = 1f
    private var h = 1f
    private var disposed = false
    private var tab = initialTab

    private class Row(val item: ShopItem, val button: UiButton) {
        var y = 0f
    }

    private val tabButtons = ShopTab.entries.map { t -> UiButton(t.title, textSize = TextSize.TINY) { tab = t; rebuild() } }
    private val backBtn = UiButton("BACK", primary = true) { goBack() }
    private var rows = emptyList<Row>()
    private val input = ButtonInput(a, { tabButtons + rows.map { it.button } + backBtn }) { goBack() }

    init {
        rebuild()
    }

    private fun rebuild() {
        tabButtons.forEachIndexed { i, b -> b.primary = ShopTab.entries[i] == tab }
        rows = Catalog.items.filter { it.tab == tab }.map { item -> Row(item, UiButton(textSize = TextSize.SMALL) { onItem(item) }) }
        refreshButtons()
        layout()
    }

    private fun refreshButtons() {
        for (r in rows) {
            val item = r.item
            val b = r.button
            b.labelIcon = null
            b.enabled = true
            b.primary = false
            when {
                !s.owns(item) -> {
                    b.label = item.price.toString()
                    b.labelIcon = a.coin
                    b.primary = true
                    b.enabled = s.coins >= item.price
                }
                s.isEquipped(item) -> { b.label = "EQUIPPED"; b.enabled = false }
                else -> b.label = "EQUIP"
            }
        }
    }

    private fun onItem(item: ShopItem) {
        if (!s.owns(item)) {
            if (s.buy(item)) a.play(a.sCoins, 0.8f)
        } else {
            s.equip(item)
        }
        sky.theme = Catalog.sky(s.skyId)
        refreshButtons()
    }

    private fun goBack() {
        back()
        if (game.screen !== this) dispose()
    }

    override fun show() {
        Gdx.input.inputProcessor = input
        refreshButtons()
    }

    override fun resize(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        batch.projectionMatrix.setToOrtho2D(0f, 0f, w, h)
        shapes.projectionMatrix.setToOrtho2D(0f, 0f, w, h)
        layout()
    }

    private fun rowHeight() = if (tab == ShopTab.WEAPONS) 200f else 168f

    private fun layout() {
        val u = a.u
        val m = 40f * u
        val top = h - Gdx.graphics.safeInsetTop
        val gap = 16f * u
        val tw = (w - 2 * m - gap) / 2f
        tabButtons.forEachIndexed { i, b -> b.set(m + i * (tw + gap), top - 400f * u, tw, 110f * u) }
        var y = top - 440f * u
        val rh = rowHeight()
        for (r in rows) {
            y -= rh * u
            r.y = y
            r.button.set(w - m - 30f * u - 270f * u, y + (rh - ROW_GAP - 100f) / 2f * u, 270f * u, 100f * u)
        }
        val bw = 620f * u
        backBtn.set((w - bw) / 2f, Gdx.graphics.safeInsetBottom + m, bw, 140f * u)
    }

    override fun render(delta: Float) {
        sky.render(w, h, 0f, delta)
        val u = a.u
        val m = 40f * u
        val top = h - Gdx.graphics.safeInsetTop
        val rh = (rowHeight() - ROW_GAP) * u
        val swatch = rh - 40f * u
        batch.begin()
        text(batch, a.fontLarge, "SHOP", w / 2f, top - 110f * u)

        // Coin balance.
        val label = s.coins.toString()
        val cs = 76f * u
        val bw = 320f * u
        panel(batch, a, (w - bw) / 2f, top - 290f * u, bw, 110f * u, 0.55f)
        batch.draw(a.coin, (w - bw) / 2f + 28f * u, top - 290f * u + (110f * u - cs) / 2f, cs, cs)
        text(batch, a.fontMedium, label, (w - bw) / 2f + 28f * u + cs + 20f * u, top - 235f * u, Align.left)

        tabButtons.forEach { it.draw(batch, a) }

        for (r in rows) {
            val x = m
            val rw = w - 2 * m
            panel(batch, a, x, r.y, rw, rh, 0.72f)
            val tx = x + 20f * u + swatch + 28f * u
            val weapon = r.item.weapon
            if (weapon == null) {
                drawSkySwatch(r.item, x + 20f * u, r.y + 20f * u, swatch)
                text(batch, a.fontSmall, r.item.name, tx, r.y + rh / 2f, Align.left)
            } else {
                text(batch, a.fontSmall, r.item.name, tx, r.y + rh * 0.75f, Align.left)
                text(batch, a.fontTiny, "SHARP ${weapon.sharpness.toInt()}   EDGE ${weapon.maxEdge.toInt()}", tx, r.y + rh * 0.47f, Align.left)
                a.fontTiny.color = ACCENT
                val trait = weapon.trait
                text(batch, a.fontTiny, if (trait.label.isEmpty()) trait.description.uppercase() else trait.label, tx, r.y + rh * 0.2f, Align.left)
                a.fontTiny.color = Color.WHITE
            }
            r.button.draw(batch, a)
        }
        backBtn.draw(batch, a)
        batch.end()

        if (tab == ShopTab.WEAPONS) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            for (r in rows) {
                val weapon = r.item.weapon ?: continue
                val sx = m + 20f * u
                val sy = r.y + 20f * u
                shapes.setColor(0.04f, 0.07f, 0.18f, 0.35f)
                shapes.rect(sx, sy, swatch, swatch)
                WeaponShapes.drawIcon(shapes, weapon, sx + 6f * u, sy + 6f * u, swatch - 12f * u, silhouette = !s.owns(r.item))
            }
            shapes.end()
        }
    }

    /** Sky gradient preview, stepping through the theme from bottom to top. */
    private fun drawSkySwatch(item: ShopItem, x: Float, y: Float, size: Float) {
        val keys = Catalog.sky(item.id).keys
        val strips = 8
        val c = Color()
        for (i in 0 until strips) {
            val k = keys[(i * (keys.size - 1)) / (strips - 1)]
            c.set(k.bottom shl 8 or 0xFF).lerp(Color(k.top shl 8 or 0xFF), 0.5f)
            batch.color = c
            batch.draw(a.white, x, y + i * size / strips, size, size / strips + 1f)
        }
        batch.color = Color.WHITE
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        sky.dispose()
        batch.dispose()
        shapes.dispose()
    }

    companion object {
        const val ROW_GAP = 18f
    }
}
