package com.slasher

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.slasher.screens.GameScreen
import com.slasher.screens.MenuScreen
import com.slasher.screens.SettingsScreen
import com.slasher.screens.ShopScreen
import com.slasher.shop.ShopTab

/** Desktop-only testing hooks; Android always uses the defaults. */
data class DebugOptions(
    val autoplay: Boolean = false,
    val immortal: Boolean = false,
    val startDepth: Float = 0f,
    val weaponId: String? = null,
    val coins: Int? = null,
    /** A gem in every new tower part. */
    val gems: Boolean = false,
    /** Drags a fake finger back and forth across the tower without lifting, to test continuous cutting. */
    val zigzag: Boolean = false,
    val screenshotPrefix: String? = null,
    val screenshotTimes: List<Float> = emptyList(),
    val startScreen: String = "menu",
)

class SlasherGame(val debug: DebugOptions = DebugOptions()) : Game() {
    lateinit var settings: Settings; private set
    lateinit var assets: Assets; private set
    private var elapsed = 0f
    private var shotIndex = 0

    override fun create() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        settings = Settings(Gdx.app.getPreferences("slasher"))
        debug.coins?.let { settings.coins = it }
        debug.weaponId?.let { settings.weaponId = it }
        assets = Assets(settings)
        assets.resize(Gdx.graphics.width, Gdx.graphics.height)
        assets.updateMusic()
        when {
            debug.autoplay || debug.startScreen == "game" -> startGame()
            debug.startScreen == "settings" -> setScreen(SettingsScreen(this) { showMenu() })
            debug.startScreen == "paused" -> { startGame(); screen.pause() }
            debug.startScreen.startsWith("shop") -> {
                val tab = ShopTab.entries.firstOrNull { debug.startScreen.endsWith(it.name.lowercase()) } ?: ShopTab.BACKGROUNDS
                setScreen(ShopScreen(this, tab) { showMenu() })
            }
            else -> showMenu()
        }
    }

    override fun resize(width: Int, height: Int) {
        assets.resize(width, height)
        super.resize(width, height)
    }

    fun showMenu() = replaceScreen(MenuScreen(this))

    fun startGame() = replaceScreen(GameScreen(this))

    /** Opens settings; [back] decides where to go afterwards (menu, or resume a paused game). */
    fun showSettings(back: () -> Unit) = setScreen(SettingsScreen(this, back))

    fun showShop(back: () -> Unit) = setScreen(ShopScreen(this, back = back))

    private fun replaceScreen(next: Screen) {
        val old = screen
        setScreen(next)
        old?.dispose()
    }

    override fun render() {
        super.render()
        captureDebugScreenshots()
    }

    private fun captureDebugScreenshots() {
        val prefix = debug.screenshotPrefix ?: return
        elapsed += Gdx.graphics.deltaTime
        if (shotIndex < debug.screenshotTimes.size && elapsed >= debug.screenshotTimes[shotIndex]) {
            val w = Gdx.graphics.backBufferWidth
            val h = Gdx.graphics.backBufferHeight
            val pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h)
            val flipped = Pixmap(w, h, Pixmap.Format.RGBA8888)
            for (y in 0 until h) flipped.drawPixmap(pixmap, 0, y, 0, h - 1 - y, w, 1)
            PixmapIO.writePNG(Gdx.files.absolute("${prefix}_$shotIndex.png"), flipped)
            pixmap.dispose()
            flipped.dispose()
            shotIndex++
            if (shotIndex >= debug.screenshotTimes.size) Gdx.app.exit()
        }
    }

    override fun dispose() {
        screen?.dispose()
        assets.dispose()
    }
}
