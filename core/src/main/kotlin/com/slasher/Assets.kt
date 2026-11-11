package com.slasher

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.utils.Disposable
import com.slasher.logic.CutSound
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Textures, fonts and sounds. UI is laid out for a 1080×1920 reference screen and scaled by [u],
 * and fonts are rasterized at the real pixel size so text stays sharp on every device (as in 3D Stacker).
 */
class Assets(private val settings: Settings) : Disposable {
    private val textures = ArrayList<Texture>()
    private fun tex(path: String) = Texture(Gdx.files.internal(path), true).also {
        it.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
        textures += it
    }

    val btnPause = tex("ui/btn_pause.png")
    val coin = tex("ui/coin.png")
    private val panelTex = Texture(Gdx.files.internal("ui/panel.png")).also {
        it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        textures += it
    }
    val panel = NinePatch(panelTex, 40, 40, 40, 40)
    val white: Texture = run {
        val pm = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pm.setColor(Color.WHITE)
        pm.fill()
        Texture(pm).also { pm.dispose(); textures += it }
    }

    /** Soft white radial falloff: the glow behind the tower and round particles. */
    val glow: Texture = run {
        val size = 256
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        for (y in 0 until size) for (x in 0 until size) {
            val dx = (x + 0.5f) / size * 2f - 1f
            val dy = (y + 0.5f) / size * 2f - 1f
            val r = min(1f, sqrt(dx * dx + dy * dy))
            val a = (1f - r) * (1f - r) * (1f - r * 0.5f)
            pm.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, a))
        }
        Texture(pm).also {
            it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            pm.dispose()
            textures += it
        }
    }

    private val sounds = ArrayList<Sound>()
    private fun sfx(name: String) = Gdx.audio.newSound(Gdx.files.internal("sfx/$name.wav")).also { sounds += it }

    val sClick = sfx("click")
    val sWhoosh = sfx("whoosh")
    val sWhirr = sfx("whirr")
    private val sCutSoft = sfx("cut_soft")
    private val sCutWood = sfx("cut_wood")
    private val sCutHard = sfx("cut_hard")
    private val sCutMetal = sfx("cut_metal")
    val sSlide = sfx("slide")
    val sWhump = sfx("whump")
    val sThunk = sfx("thunk")
    val sThock = sfx("thock")
    val sClank = sfx("clank")
    val sChime = sfx("chime")
    val sSnap = sfx("snap")
    val sZone = sfx("zone")
    val sCoins = sfx("coins")

    fun cutSound(kind: CutSound) = when (kind) {
        CutSound.SOFT -> sCutSoft
        CutSound.WOOD -> sCutWood
        CutSound.HARD -> sCutHard
        CutSound.METAL -> sCutMetal
    }

    val music: Music = Gdx.audio.newMusic(Gdx.files.internal("sfx/music.wav")).apply {
        isLooping = true
        volume = 0.35f
    }

    private val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/NotoSans-Black.ttf"))
    lateinit var fontTitle: BitmapFont; private set
    lateinit var fontLarge: BitmapFont; private set
    lateinit var fontMedium: BitmapFont; private set
    lateinit var fontSmall: BitmapFont; private set
    lateinit var fontTiny: BitmapFont; private set
    private var fontsBuilt = false

    /** UI scale: pixels per reference unit. */
    var u = 1f; private set

    fun resize(width: Int, height: Int) {
        val nu = min(width / 1080f, height / 1920f)
        if (fontsBuilt && abs(nu - u) < 0.001f) return
        if (fontsBuilt) fonts().forEach { it.dispose() }
        u = nu
        fontTitle = font(150f, 8f)
        fontLarge = font(96f, 6f)
        fontMedium = font(60f, 4f)
        fontSmall = font(42f, 3f)
        fontTiny = font(32f, 2f)
        fontsBuilt = true
    }

    private fun fonts() = listOf(fontTitle, fontLarge, fontMedium, fontSmall, fontTiny)

    private fun font(size: Float, shadow: Float): BitmapFont {
        val p = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            this.size = max(8, (size * u).roundToInt())
            color = Color.WHITE
            shadowOffsetX = 0
            shadowOffsetY = max(1, (shadow * u).roundToInt())
            shadowColor = Color(0.04f, 0.07f, 0.18f, 0.45f)
            minFilter = Texture.TextureFilter.Linear
            magFilter = Texture.TextureFilter.Linear
        }
        return generator.generateFont(p)
    }

    /** Draws the rounded panel with its corner radius scaled to the UI, so small screens keep round corners. */
    fun drawPanel(batch: SpriteBatch, x: Float, y: Float, w: Float, h: Float) {
        val s = min(u, min(w, h) / 2f / panel.leftWidth)
        panel.draw(batch, x, y, 0f, 0f, w / s, h / s, s, s, 0f)
    }

    /** Plays [sound] if sound is on; returns its id (or -1) so long sounds can be stopped. */
    fun play(sound: Sound, volume: Float = 1f, pitch: Float = 1f): Long =
        if (settings.sound) sound.play(volume, pitch, 0f) else -1L

    fun vibrate(ms: Int) {
        if (settings.vibration) Gdx.input.vibrate(ms)
    }

    fun updateMusic() {
        if (settings.music && !music.isPlaying) music.play()
        if (!settings.music && music.isPlaying) music.pause()
    }

    override fun dispose() {
        textures.forEach { it.dispose() }
        sounds.forEach { it.dispose() }
        music.dispose()
        if (fontsBuilt) fonts().forEach { it.dispose() }
        generator.dispose()
    }
}
