package com.slasher.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix3
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.slasher.logic.Crack
import com.slasher.logic.CutPlane
import com.slasher.logic.Gem
import com.slasher.logic.MaterialSample
import com.slasher.logic.WeakSpot

/** Patterns drawn on a solid: grain direction, weak spots, a crack and a fading seam. */
class SurfaceFeatures {
    val grainN = Vector3(0f, 1f, 0f)
    val spots = FloatArray(12)
    val crackPlane = FloatArray(4)
    val crackStart = FloatArray(4)
    val crackDir = Vector3()
    val seam = FloatArray(4)
    var seamAlpha = 0f
    val gem = FloatArray(4)
    val gemColor = Color()

    fun set(grain: Vector3, spotList: List<WeakSpot>, crack: Crack?, hiddenGem: Gem? = null) = apply {
        gem[3] = 0f
        if (hiddenGem != null) {
            gem[0] = hiddenGem.pos.x
            gem[1] = hiddenGem.pos.y
            gem[2] = hiddenGem.pos.z
            gem[3] = 1f
            gemColor.set(hiddenGem.kind.rgb shl 8 or 0xFF)
        }
        grainN.set(grain)
        spots.fill(0f)
        spotList.take(3).forEachIndexed { i, s ->
            spots[i * 4] = s.pos.x
            spots[i * 4 + 1] = s.pos.y
            spots[i * 4 + 2] = s.pos.z
            spots[i * 4 + 3] = s.radius
        }
        crackStart[3] = 0f
        if (crack != null) {
            crackPlane[0] = crack.plane.n.x
            crackPlane[1] = crack.plane.n.y
            crackPlane[2] = crack.plane.n.z
            crackPlane[3] = crack.plane.d
            crackStart[0] = crack.start.x
            crackStart[1] = crack.start.y
            crackStart[2] = crack.start.z
            crackStart[3] = crack.length
            crackDir.set(crack.dir)
        }
    }

    fun seam(plane: CutPlane?, alpha: Float) = apply {
        seamAlpha = if (plane == null) 0f else alpha
        if (plane != null) {
            seam[0] = plane.n.x
            seam[1] = plane.n.y
            seam[2] = plane.n.z
            seam[3] = plane.d
        }
    }

    fun set(o: SurfaceFeatures) = apply {
        grainN.set(o.grainN)
        o.spots.copyInto(spots)
        o.crackPlane.copyInto(crackPlane)
        o.crackStart.copyInto(crackStart)
        crackDir.set(o.crackDir)
        o.seam.copyInto(seam)
        seamAlpha = o.seamAlpha
        o.gem.copyInto(gem)
        gemColor.set(o.gemColor)
    }

    companion object {
        val NONE = SurfaceFeatures()
    }
}

/** Wraps the solid shader (assets/shaders/solid.*). */
class SolidShader : Disposable {
    private val program: ShaderProgram
    private val normalMatrix = Matrix3()

    init {
        ShaderProgram.pedantic = false
        program = ShaderProgram(Gdx.files.internal("shaders/solid.vert"), Gdx.files.internal("shaders/solid.frag"))
        require(program.isCompiled) { "solid shader: ${program.log}" }
    }

    fun begin(cam: Camera, light: Vector3, skyTop: Color, skyBottom: Color, rim: Color, time: Float) {
        program.bind()
        program.setUniformMatrix("u_projView", cam.combined)
        program.setUniformf("u_camPos", cam.position)
        program.setUniformf("u_lightDir", light)
        program.setUniformf("u_lightColor", 1f, 0.97f, 0.93f)
        program.setUniformf("u_skyTop", skyTop.r, skyTop.g, skyTop.b)
        program.setUniformf("u_skyBottom", skyBottom.r, skyBottom.g, skyBottom.b)
        program.setUniformf("u_rimColor", rim.r, rim.g, rim.b)
        program.setUniformf("u_time", time)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glDepthMask(true)
    }

    fun material(m: MaterialSample) {
        program.setUniformf("u_base", m.base[0], m.base[1], m.base[2])
        program.setUniformf("u_accent", m.accent[0], m.accent[1], m.accent[2])
        program.setUniformf("u_cutColor", m.cut[0], m.cut[1], m.cut[2])
        program.setUniformf("u_rough", m.roughness)
        program.setUniformf("u_metal", m.metal)
        program.setUniformf("u_transl", m.translucency)
        program.setUniformf("u_grainStrength", m.grainStrength)
        program.setUniformf("u_grainFreq", m.grainFreq)
        program.setUniformf("u_mottle", m.mottle)
        program.setUniformf("u_glow", m.glow)
        program.setUniformf("u_emissive", 0f, 0f, 0f)
    }

    /** A plain colored surface (weapon parts). */
    fun plain(rgb: Int, metal: Float, rough: Float, emissive: Float = 0f) {
        val c = Color(rgb shl 8 or 0xFF)
        program.setUniformf("u_base", c.r, c.g, c.b)
        program.setUniformf("u_accent", c.r, c.g, c.b)
        program.setUniformf("u_cutColor", c.r, c.g, c.b)
        program.setUniformf("u_rough", rough)
        program.setUniformf("u_metal", metal)
        program.setUniformf("u_transl", 0f)
        program.setUniformf("u_grainStrength", 0f)
        program.setUniformf("u_grainFreq", 1f)
        program.setUniformf("u_mottle", 0f)
        program.setUniformf("u_glow", 0f)
        program.setUniformf("u_emissive", c.r * emissive, c.g * emissive, c.b * emissive)
        features(SurfaceFeatures.NONE)
    }

    fun features(f: SurfaceFeatures) {
        program.setUniformf("u_grainN", f.grainN)
        program.setUniform4fv("u_spots", f.spots, 0, 12)
        program.setUniformf("u_crackPlane", f.crackPlane[0], f.crackPlane[1], f.crackPlane[2], f.crackPlane[3])
        program.setUniformf("u_crackStart", f.crackStart[0], f.crackStart[1], f.crackStart[2], f.crackStart[3])
        program.setUniformf("u_crackDir", f.crackDir)
        program.setUniformf("u_seam", f.seam[0], f.seam[1], f.seam[2], f.seam[3])
        program.setUniformf("u_seamAlpha", f.seamAlpha)
        program.setUniformf("u_gem", f.gem[0], f.gem[1], f.gem[2], f.gem[3])
        program.setUniformf("u_gemColor", f.gemColor.r, f.gemColor.g, f.gemColor.b)
    }

    fun draw(mesh: Mesh, model: Matrix4, alpha: Float = 1f, heightShade: Float = 1f, cull: Boolean = true) {
        if (cull) {
            Gdx.gl.glEnable(GL20.GL_CULL_FACE)
            Gdx.gl.glCullFace(GL20.GL_BACK)
        } else {
            Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        }
        if (alpha < 1f) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        } else {
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }
        program.setUniformMatrix("u_model", model)
        program.setUniformMatrix("u_normalMatrix", normalMatrix.set(model).inv().transpose())
        program.setUniformf("u_alpha", alpha)
        program.setUniformf("u_heightShade", heightShade)
        mesh.render(program, GL20.GL_TRIANGLES)
    }

    fun end() {
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun dispose() = program.dispose()
}
