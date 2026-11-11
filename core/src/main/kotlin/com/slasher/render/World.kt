package com.slasher.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.slasher.Assets
import com.slasher.Settings
import com.slasher.logic.ConvexPoly
import com.slasher.logic.CutInput
import com.slasher.logic.CutKind
import com.slasher.logic.CutPlane
import com.slasher.logic.CutResult
import com.slasher.logic.CutRules
import com.slasher.logic.Crack
import com.slasher.logic.Gem
import com.slasher.logic.GemKind
import com.slasher.logic.MaterialCurve
import com.slasher.logic.MaterialKey
import com.slasher.logic.MaterialSample
import com.slasher.logic.Motion
import com.slasher.logic.Run
import com.slasher.logic.SliceBody
import com.slasher.logic.Tower
import com.slasher.logic.TowerFeatures
import com.slasher.logic.Trait
import com.slasher.logic.WeakSpot
import com.slasher.shop.Catalog
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan
import kotlin.random.Random

/** A finished swipe, in screen pixels (origin bottom-left), with its speed in pixels per second. */
class Swipe(val a: Vector2, val b: Vector2, val speed: Float, val crossed: Boolean = false)

/**
 * The tower, its slices, the weapon and every effect around them (spec §4, §7, §11, §12).
 * Used by the game and, in [attract] mode, as the self-slicing backdrop of the main menu.
 */
class World(
    private val assets: Assets,
    private val settings: Settings,
    val run: Run,
    private val attract: Boolean = false,
    seed: Long = System.nanoTime(),
    /** How far above the screen's centre the tower top sits, as a fraction of the screen height. */
    private val topLift: Float = 0.26f,
) : Disposable {
    val cam = PerspectiveCamera(FOV, 1f, 1f)
    private val shader = SolidShader()
    val sky = Sky(Catalog.sky(settings.skyId))
    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()
    private val random = Random(seed)
    val features = TowerFeatures(random)
    private val particles = Particles(random)
    val popups = Popups()
    val fingerTrail = Ribbon(0.2f)
    private val weaponTrail = Ribbon(0.12f)
    private val weaponModel = WeaponModel(run.weapon)
    private val trailColor = Color(run.weapon.trailRgb shl 8 or 0xFF)

    /** Fired when a cut resolves. */
    var onCut: ((CutResult) -> Unit)? = null
    /** Fired when the tower reaches a new material. */
    var onZone: ((MaterialKey) -> Unit)? = null
    /** Fired once the blade has broken and its last swing is over. */
    var onBroken: (() -> Unit)? = null
    /** Fired when a cut finds a gem; gold passes the coins to add to the balance. */
    var onGem: ((GemKind, Int) -> Unit)? = null

    // ---- tower ------------------------------------------------------------------------------

    private val freshMesh = Meshes.fromPoly(ConvexPoly.freshColumn)
    private var stubMesh: Mesh = freshMesh
    private val stubFeatures = SurfaceFeatures()
    private val towerMaterial = MaterialSample()
    private val dropMaterial = MaterialSample()
    private var displayDepth = run.depth
    private var displayGrain = features.grainAngle
    private var spotAppear = 1f
    private var seamPlane: CutPlane? = null
    private var seamAlpha = 0f

    private class SliceVis(val body: SliceBody, val mesh: Mesh, val material: MaterialSample, val features: SurfaceFeatures) {
        var slideSound = -1L
    }

    private val slices = ArrayList<SliceVis>()

    /** The next part of the tower dropping in to reconnect with the cut face (spec §4.3). */
    private class Drop(val mesh: Mesh, val plane: CutPlane) {
        var offset = 3.5f
        var velocity = -18f
        var landed = false
        var squash = 0f
    }

    private var drop: Drop? = null

    /** A found gem popping out of the cut and spinning up toward the camera. */
    private class GemFx(val kind: GemKind, val pos: Vector3, val vel: Vector3) {
        var age = 0f
        var spin = 0f
    }

    private val gems = ArrayList<GemFx>()
    private val gemMesh = Meshes.gem(0.11f)
    private val dropFeatures = SurfaceFeatures()

    // ---- cutting ------------------------------------------------------------------------------

    /** Everything decided at swipe time; applied when the blade reaches the tower. */
    private class Plan(
        val plane: CutPlane,
        val dir: Vector3,
        val center: Vector3,
        val below: ConvexPoly?,
        val above: ConvexPoly?,
        val result: CutResult?,
        val removedDepth: Float,
        val miss: String?,
        val gemHit: Boolean = false,
    )

    private var fx: WeaponFx? = null
    /** Swipes waiting for the tower to be ready, oldest first (spec §5). */
    private val queue = ArrayDeque<Swipe>()
    /** True while the finger's swipe lines up with the grain, so the trail glows gold. */
    var trailOnGrain = false
    var brokenPending = false
        private set
    private var brokenNotified = false

    // ---- camera & time -------------------------------------------------------------------------

    private var screenW = 1f
    private var screenH = 1f
    private var dist = 9f
    private var targetY = -0.6f
    private var shake = 0f
    private var bump = 0f
    private var slowMo = 0f
    var time = 0f
        private set
    private var attractTimer = 1.2f
    private val identity = Matrix4()
    private val model = Matrix4()
    private val tmp = Vector3()
    private val tmp2 = Vector3()
    private val v2a = Vector2()
    private val v2b = Vector2()
    private val light = Tower.right.scl(0.55f).add(0f, 1f, 0f).mulAdd(Tower.toCamera, 0.35f).nor()
    private val glowColor = Color()
    private val rimColor = Color()

    /**
     * Ready for the next cut: as soon as the last slice has started to tip (spec §4.3). The next blade may
     * set off while the new part is still dropping in; if it arrives first, the reconnect finishes at once.
     */
    val ready: Boolean
        get() = (fx?.released ?: true) && !brokenPending &&
            slices.none { it.body.phase == SliceBody.Phase.SLIDE }

    val busy: Boolean get() = fx != null || drop != null || slices.isNotEmpty() || queue.isNotEmpty()

    init {
        features.regenerate(run.depth)
        displayGrain = features.grainAngle
    }

    fun resize(width: Int, height: Int) {
        screenW = width.toFloat()
        screenH = height.toFloat()
        cam.viewportWidth = screenW
        cam.viewportHeight = screenH
        cam.near = 0.3f
        cam.far = 80f
        val aspect = screenW / screenH
        val tanV = tan(FOV / 2f * MathUtils.degreesToRadians)
        // The tower takes about 42% of the screen width, and at least 5.5 units of height are visible.
        dist = max(TOWER_SCREEN_WIDTH / (TOWER_WIDTH_FRACTION * 2f * tanV * aspect), 5.5f / (2f * tanV))
        // Put the top high on the screen (about 24% down in the game), so plenty of tower shows below it.
        targetY = Tower.TOP - topLift * 2f * dist * tanV / MathUtils.cosDeg(PITCH)
        batch.projectionMatrix.setToOrtho2D(0f, 0f, screenW, screenH)
        shapes.projectionMatrix.setToOrtho2D(0f, 0f, screenW, screenH)
        updateCamera()
    }

    private fun updateCamera() {
        val sx = (random.nextFloat() - 0.5f) * shake * 0.12f
        val sy = (random.nextFloat() - 0.5f) * shake * 0.12f
        tmp.set(0f, targetY - bump + sy, 0f).mulAdd(Tower.right, sx)
        cam.position.set(tmp).mulAdd(Tower.toCamera, dist * MathUtils.cosDeg(PITCH)).add(0f, dist * MathUtils.sinDeg(PITCH), 0f)
        cam.up.set(0f, 1f, 0f)
        cam.lookAt(tmp)
        cam.update()
    }

    // ---- input ---------------------------------------------------------------------------------

    /** Cuts along [swipe] now if ready, otherwise queues it (up to [MAX_QUEUE] wait, spec §5). */
    fun swipe(swipe: Swipe) {
        if (ready && queue.isEmpty()) startCut(swipe) else if (queue.size < MAX_QUEUE) queue.addLast(swipe)
    }

    /** Lights the finger trail gold while a swipe from [a] to [b] would cut with the grain. */
    fun hintSwipe(a: Vector2, b: Vector2) {
        val line = swipeLine(a, b)
        trailOnGrain = line != null && abs(line.angle - features.grainAngle) <= CutRules.WITH_GRAIN_DEG
    }

    private class Line(val angle: Float, val sign: Float, val height: Float)

    /** The screen swipe as a line on the tower's front: clamped angle, direction and height at the centre. */
    private fun swipeLine(a: Vector2, b: Vector2): Line? {
        val a3 = unprojectFront(a.x, a.y, Vector3())
        val b3 = unprojectFront(b.x, b.y, Vector3())
        val dir = Vector3(b3).sub(a3)
        if (dir.len() < 0.05f) return null
        val right = Tower.right
        val dr = dir.dot(right)
        val dy = dir.y
        if (atan2(abs(dy), abs(dr)) * MathUtils.radiansToDegrees > MAX_SWIPE_ANGLE) return null
        val sign = if (dr >= 0f) 1f else -1f
        var angle = atan2(dy * sign, abs(dr)) * MathUtils.radiansToDegrees
        if (abs(angle) < MIN_TILT) angle = -MIN_TILT * sign
        angle = angle.coerceIn(-MAX_TILT, MAX_TILT)
        return Line(angle, sign, a3.y + (-a3.dot(right) / dr) * dy)
    }

    /** Where the tower's front sits on screen at height [y]: left and right x, in pixels. */
    fun towerScreenSpan(y: Float, out: Vector2): Vector2 {
        val c = tmp2.set(0f, y, 0f).mulAdd(Tower.toCamera, Tower.FRONT_DEPTH)
        tmp.set(c).mulAdd(Tower.right, -0.72f)
        cam.project(tmp)
        val l = tmp.x
        tmp.set(c).mulAdd(Tower.right, 0.72f)
        cam.project(tmp)
        return out.set(l, tmp.x)
    }

    /** Screen y of the tower's top and of the deepest allowed cut, for input hit tests. */
    fun screenYAt(y: Float): Float {
        tmp.set(0f, y, 0f).mulAdd(Tower.toCamera, Tower.FRONT_DEPTH)
        cam.project(tmp)
        return tmp.y
    }

    private fun unprojectFront(sx: Float, sy: Float, out: Vector3): Vector3 {
        val ray = cam.getPickRay(sx, screenH - sy)
        val n = Tower.toCamera
        val p0 = Vector3(n).scl(Tower.FRONT_DEPTH)
        val t = p0.sub(ray.origin).dot(n) / ray.direction.dot(n)
        return out.set(ray.origin).mulAdd(ray.direction, t)
    }

    private fun startCut(sw: Swipe) {
        val plan = plan(sw) ?: return
        fx = WeaponFx(plan)
        if (run.weapon.motion == Motion.THROWN) play(assets.sWhirr, 0.7f, 0.9f + random.nextFloat() * 0.2f)
        else play(assets.sWhoosh, 0.9f, if (run.weapon.trait == Trait.PRECISION) 1.3f else 0.85f)
    }

    private fun plan(sw: Swipe): Plan? {
        val line = swipeLine(sw.a, sw.b) ?: return null
        val angle = line.angle
        val sign = line.sign
        val height = line.height
        val plane = CutPlane.fromLine(angle, height)
        val lineDir = CutPlane.lineDirection(angle).scl(sign)
        val center = Tower.up.scl(height).mulAdd(Tower.toCamera, Tower.FRONT_DEPTH)

        if (height < Tower.TOP - Tower.MAX_CUT_DEPTH) return Plan(plane, lineDir, center, null, null, null, 0f, "TOO DEEP")
        val (below, above) = ConvexPoly.freshColumn.split(plane)
        if (above == null || below == null || above.volume < 1e-4f) return Plan(plane, lineDir, center, null, null, null, 0f, if (height > 0f) "TOO HIGH" else null)

        // A swipe has to cross (most of) the tower to cut clean.
        val span = towerScreenSpan(height, v2a)
        val lo = min(sw.a.x, sw.b.x)
        val hi = max(sw.a.x, sw.b.x)
        val coverage = (min(hi, span.y) - max(lo, span.x)) / max(1f, span.y - span.x)
        val full = sw.crossed || coverage >= 0.6f

        val removed = above.volume / Tower.CROSS_AREA
        val weapon = run.weapon
        val input = CutInput(
            hardness = MaterialCurve.hardness(run.depth + removed * 0.5f),
            screenTiltDeg = abs(angle),
            grainAngleDeg = features.grainOffset(plane),
            weakFactor = features.weakFactor(plane, CutRules.crackFactor(weapon)),
            swipeSpeed = if (run.hasted) 99999f else sw.speed / assets.u,
            volume = above.volume,
            thickness = above.maxAlong(plane.n) - plane.d,
            fullSwipe = full,
            streak = run.streak,
            sharpBoost = run.sharpBoost,
        )
        return Plan(plane, lineDir, center, below, above, CutRules.resolve(weapon, input), removed, null, features.gemHit(plane))
    }

    /** The blade has reached the tower. */
    private fun applyHit(plan: Plan) {
        drop?.let { finishReconnect(it.plane) }
        val r = plan.result
        if (r == null) {
            plan.miss?.let { popupAt(plan.center, it, Color(1f, 1f, 1f, 0.8f)) }
            return
        }
        val bonus = if (attract) 0 else run.apply(r, if (r.kind == CutKind.CLEAN) plan.removedDepth else 0f)
        when (r.kind) {
            CutKind.CLEAN -> slice(plan, r)
            CutKind.STUCK -> {
                val entry = Vector3(plan.center).mulAdd(plan.dir, -0.72f)
                features.crack = Crack(plan.plane, entry, Vector3(plan.dir), 1.5f * r.penetration)
                particles.dust(entry, colorOf(towerMaterial.cut), 6, 0.8f)
                play(assets.sThock, 0.9f)
                vibrate(30)
                popupAt(plan.center, "STUCK", Color(1f, 0.85f, 0.5f, 1f))
            }
            CutKind.BOUNCE -> {
                val entry = Vector3(plan.center).mulAdd(plan.dir, -0.72f)
                particles.sparks(entry, 22, Vector3(plan.dir).scl(-1f))
                play(assets.sClank, 1f, 0.9f + random.nextFloat() * 0.2f)
                shake = 1f
                vibrate(60)
                popupAt(plan.center, "CLANK!", Color(1f, 0.45f, 0.4f, 1f), big = true)
            }
        }
        if (bonus > 0) {
            play(assets.sZone, 0.8f)
            onZone?.invoke(MaterialCurve.zone(run.depth))
        }
        if (!attract) onCut?.invoke(r)
        if (run.broken) brokenPending = true
    }

    private fun slice(plan: Plan, r: CutResult) {
        val below = plan.below!!
        val above = plan.above!!
        val material = MaterialSample().set(towerMaterial)
        val feats = SurfaceFeatures().set(stubFeatures)
        replaceStub(Meshes.fromPoly(below))
        if (plan.gemHit) features.gem?.let { collectGem(it) }
        val body = SliceBody(above, plan.plane, below.capFace(plan.plane.n), material.friction, random)
        val vis = SliceVis(body, Meshes.fromPoly(above), material, feats)
        vis.slideSound = play(assets.sSlide, 0.25f + 0.5f * MathUtils.sinDeg(plan.plane.tiltDeg), 0.9f + random.nextFloat() * 0.2f)
        slices += vis
        features.removeAbove(plan.plane)

        val zone = MaterialCurve.zone(run.depth)
        play(assets.cutSound(zone.sound), 1f, 0.92f + random.nextFloat() * 0.16f)
        vibrate(12)
        for (k in -1..1 step 2) particles.dust(Vector3(plan.center).mulAdd(plan.dir, 0.7f * k), colorOf(material.cut), 5, 0.7f, 16f)

        if (attract) return
        popupAt(plan.center, "+${r.points}", Color.WHITE, big = true)
        when {
            r.perfect -> {
                popupAt(plan.center, "PERFECT", Color(1f, 0.82f, 0.25f, 1f), big = true, delay = 0.15f, dy = 70f)
                play(assets.sChime, 0.8f)
                vibrate(10)
            }
            r.withGrain -> popupAt(plan.center, "WITH THE GRAIN", Color(0.75f, 1f, 0.8f, 1f), delay = 0.15f, dy = 70f)
            r.weakHit -> popupAt(plan.center, "WEAK SPOT", Color(1f, 0.85f, 0.45f, 1f), delay = 0.15f, dy = 70f)
        }
        if (r.shaving) popupAt(plan.center, "SHAVING!", Color(0.85f, 0.9f, 1f, 1f), delay = 0.3f, dy = 130f)
        if (r.perfect && (run.weapon.trait == Trait.PRECISION || settings.slowMo)) slowMo = SLOW_MO_TIME
    }

    private fun collectGem(gem: Gem) {
        features.gem = null
        val vel = Tower.toCamera.scl(1.2f).add(0f, 3.6f, 0f)
        gems += GemFx(gem.kind, Vector3(gem.pos), vel)
        val color = Color(gem.kind.rgb shl 8 or 0xFF)
        particles.sparks(gem.pos, 18, Tower.up)
        particles.dust(gem.pos, color, 10, 1.6f, 26f)
        play(assets.sChime, 1f, 1.35f)
        vibrate(25)
        if (attract) return
        val coins = run.collect(gem.kind)
        if (coins > 0) play(assets.sCoins, 0.9f)
        popupAt(gem.pos, gem.kind.label, color, big = true, delay = 0.1f, dy = 150f)
        onGem?.invoke(gem.kind, coins)
    }

    private fun replaceStub(mesh: Mesh) {
        if (stubMesh !== freshMesh) stubMesh.dispose()
        stubMesh = mesh
    }

    private fun startDrop(plane: CutPlane) {
        // The new part's grain and weak spots are known now, so a cut planned during the drop uses them.
        features.regenerate(run.depth)
        spotAppear = 0f
        val part = ConvexPoly.freshColumn.clip(Vector3(plane.n).scl(-1f), -plane.d)
        if (part == null) {
            finishReconnect(plane)
            return
        }
        drop = Drop(Meshes.fromPoly(part), plane)
    }

    private fun finishReconnect(plane: CutPlane) {
        drop?.mesh?.dispose()
        drop = null
        replaceStub(freshMesh)
        seamPlane = plane
        seamAlpha = 1f
    }

    // ---- bot -----------------------------------------------------------------------------------

    /** A good cut: with the grain and through a weak spot when there is one. Used by the menu and autoplay. */
    fun botSwipe(sloppiness: Float = 0f): Swipe {
        val angle = (features.grainAngle + (random.nextFloat() - 0.5f) * 2f * sloppiness * 20f).coerceIn(-MAX_TILT, MAX_TILT)
        val n = CutPlane.normalFor(angle)
        val target = if (!attract && features.gem != null) features.gem!!.pos else features.spots.randomOrNull(random)?.pos
        val h = if (target != null && sloppiness < 0.5f) {
            (n.dot(target) - Tower.FRONT_DEPTH * n.dot(Tower.toCamera)) / n.y
        } else {
            -0.25f - random.nextFloat() * 0.6f
        }.coerceIn(-1.3f, -0.12f)
        val c = Tower.up.scl(h).mulAdd(Tower.toCamera, Tower.FRONT_DEPTH)
        val d = CutPlane.lineDirection(angle)
        val p1 = Vector3(c).mulAdd(d, -1.7f)
        val p2 = Vector3(c).mulAdd(d, 1.7f)
        cam.project(p1)
        cam.project(p2)
        val a = Vector2(p1.x, p1.y)
        val b = Vector2(p2.x, p2.y)
        return if (random.nextBoolean()) Swipe(a, b, 2600f * assets.u, true) else Swipe(b, a, 2600f * assets.u, true)
    }

    // ---- update ----------------------------------------------------------------------------------

    fun update(rawDt: Float) {
        if (slowMo > 0f) slowMo -= rawDt
        if (!attract) run.tick(rawDt)
        val dt = (if (slowMo > 0f) rawDt * 0.3f else rawDt) * (if (run.hasted) HASTE_SPEED else 1f)
        time += dt

        fx?.let { f ->
            f.update(dt)
            if (f.done) fx = null
        }
        val sit = slices.iterator()
        while (sit.hasNext()) {
            val s = sit.next()
            if (s.body.update(dt)) {
                when (s.body.phase) {
                    SliceBody.Phase.TIP -> {
                        // The next part starts dropping as soon as the slice tips, to keep play moving.
                        if (s.slideSound >= 0) assets.sSlide.stop(s.slideSound)
                        startDrop(s.body.plane)
                    }
                    SliceBody.Phase.TUMBLE -> play(assets.sWhump, 0.5f, 0.9f + random.nextFloat() * 0.2f)
                    else -> Unit
                }
            }
            if (s.body.phase == SliceBody.Phase.DONE) {
                s.mesh.dispose()
                sit.remove()
            }
        }
        drop?.let { d ->
            if (!d.landed) {
                d.velocity -= 80f * dt
                d.offset += d.velocity * dt
                if (d.offset <= 0f) {
                    d.offset = 0f
                    d.landed = true
                    play(assets.sThunk, 0.9f, 0.95f + random.nextFloat() * 0.1f)
                    vibrate(15)
                    bump = 0.05f
                    val h = d.plane.d / d.plane.n.y
                    repeat(6) { i ->
                        val a = i / 6f * 360f
                        tmp.set(MathUtils.cosDeg(a) * 0.55f, h, MathUtils.sinDeg(a) * 0.55f)
                        particles.dust(tmp, colorOf(dropMaterial.base), 2, 0.9f, 18f)
                    }
                }
            } else {
                d.squash += dt
                if (d.squash >= SQUASH_TIME) finishReconnect(d.plane)
            }
        }

        if (brokenPending && fx == null && !brokenNotified) {
            brokenNotified = true
            play(assets.sSnap, 1f)
            vibrate(200)
            particles.sparks(Vector3(0f, -0.4f, 0f).mulAdd(Tower.toCamera, 1.2f), 30, Tower.up)
            onBroken?.invoke()
        }
        if (ready) queue.removeFirstOrNull()?.let { startCut(it) }
        val git = gems.iterator()
        while (git.hasNext()) {
            val g = git.next()
            g.age += rawDt
            g.vel.y -= 4f * rawDt
            g.pos.mulAdd(g.vel, rawDt)
            g.spin += 540f * rawDt
            if (g.age > GEM_LIFE) git.remove()
        }

        if (attract) {
            attractTimer -= rawDt
            if (attractTimer <= 0f && ready) {
                attractTimer = 2.6f
                swipe(botSwipe(0.2f))
            }
        }

        displayDepth += (run.depth - displayDepth) * min(1f, rawDt * 3f)
        displayGrain += (features.grainAngle - displayGrain) * min(1f, rawDt * 8f)
        spotAppear = min(1f, spotAppear + rawDt * 3f)
        seamAlpha = max(0f, seamAlpha - rawDt / 0.35f)
        shake = max(0f, shake - rawDt * 4f)
        bump *= max(0f, 1f - rawDt * 10f)
        particles.update(dt)
        popups.update(rawDt, assets.u)
        fingerTrail.update(rawDt)
        weaponTrail.update(dt)
        updateCamera()
    }

    /** Lets the blade break again after a revive. */
    fun revived() {
        brokenPending = false
        brokenNotified = false
    }

    // ---- render ----------------------------------------------------------------------------------

    fun render(rawDt: Float) {
        update(rawDt)
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        sky.render(screenW, screenH, Catalog.skyHeight(displayDepth), rawDt)

        // The radial glow behind the tower, as in the video.
        glowColor.set(sky.bottom).lerp(Color.WHITE, 0.45f)
        tmp.set(0f, -1.4f, 0f)
        cam.project(tmp)
        val gs = screenW * 1.9f
        batch.begin()
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        batch.setColor(glowColor.r, glowColor.g, glowColor.b, 0.55f)
        batch.draw(assets.glow, tmp.x - gs / 2f, tmp.y - gs * 0.6f, gs, gs * 1.2f)
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.color = Color.WHITE
        batch.end()

        rimColor.set(glowColor).lerp(Color.WHITE, 0.55f)
        MaterialCurve.sample(displayDepth, towerMaterial)
        MaterialCurve.sample(run.depth, dropMaterial)
        val spots = features.spots.map { WeakSpot(it.pos, it.radius * spotAppear) }
        stubFeatures.set(CutPlane.normalFor(displayGrain), spots, features.crack, features.gem).seam(seamPlane, seamAlpha)

        shader.begin(cam, light, sky.top, sky.bottom, rimColor, time)
        shader.material(towerMaterial)
        shader.features(stubFeatures)
        shader.draw(stubMesh, identity)
        drop?.let { d ->
            val k = if (d.landed) MathUtils.sin(MathUtils.PI * (d.squash / SQUASH_TIME)) else 0f
            val pivot = d.plane.d / d.plane.n.y
            model.idt().translate(0f, d.offset + pivot, 0f).scale(1f + 0.03f * k, 1f - 0.06f * k, 1f + 0.03f * k).translate(0f, -pivot, 0f)
            shader.material(dropMaterial)
            shader.features(dropFeatures.set(CutPlane.normalFor(displayGrain), emptyList(), null))
            shader.draw(d.mesh, model)
        }
        for (s in slices) {
            shader.material(s.material)
            shader.features(s.features)
            shader.draw(s.mesh, s.body.transform(model), heightShade = 0.5f)
        }
        for (g in gems) {
            val k = g.age / GEM_LIFE
            val scale = if (k < 0.7f) 1f + k * 0.9f else (1f + 0.63f) * (1f - (k - 0.7f) / 0.3f)
            model.idt().translate(g.pos).rotate(Vector3.Y, g.spin).rotate(Vector3.X, 18f).scale(scale, scale, scale)
            shader.plain(g.kind.rgb, 0.35f, 0.05f, emissive = 0.35f)
            shader.draw(gemMesh, model, heightShade = 0f)
        }
        fx?.draw()
        shader.end()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        weaponTrail.draw(shapes, trailColor, 14f * assets.u, 0.8f)
        if (settings.trail) fingerTrail.draw(shapes, if (trailOnGrain) GRAIN_GOLD else trailColor, 16f * assets.u, 0.7f)
        shapes.end()
        batch.begin()
        particles.draw(batch, assets, cam)
        popups.draw(batch, assets)
        batch.end()
    }

    private fun popupAt(world: Vector3, text: String, color: Color, big: Boolean = false, delay: Float = 0f, dy: Float = 0f) {
        tmp.set(world).mulAdd(Tower.toCamera, 0.3f)
        cam.project(tmp)
        popups.add(text, tmp.x, tmp.y + (90f + dy) * assets.u, color, big, delay)
    }

    private fun colorOf(rgb: FloatArray) = Color(rgb[0], rgb[1], rgb[2], 1f)

    private fun play(sound: com.badlogic.gdx.audio.Sound, volume: Float, pitch: Float = 1f): Long =
        assets.play(sound, if (attract) volume * 0.35f else volume, pitch)

    private fun vibrate(ms: Int) {
        if (!attract) assets.vibrate(ms)
    }

    override fun dispose() {
        slices.forEach { it.mesh.dispose() }
        drop?.mesh?.dispose()
        if (stubMesh !== freshMesh) stubMesh.dispose()
        freshMesh.dispose()
        gemMesh.dispose()
        weaponModel.dispose()
        shader.dispose()
        sky.dispose()
        batch.dispose()
        shapes.dispose()
    }

    // ---- weapon animation -----------------------------------------------------------------------------

    /** The weapon crossing the screen along the swipe, in the cut plane (spec §7). */
    private inner class WeaponFx(private val plan: Plan) {
        private val shape = weaponModel.shape
        private val n = plan.plane.n
        private val e1 = plan.dir
        private val e2 = Vector3(n).crs(e1).nor()
        private val kind = plan.result?.kind
        private val penetration = plan.result?.penetration ?: 1f
        private val thrown = run.weapon.motion == Motion.THROWN

        var done = false
            private set
        /** True once the next cut may start. */
        var released = false
            private set

        private var t = 0f
        private var hit = false
        private var holdTime = -1f
        private var leaving = false
        private var leaveTime = 0f
        private var alpha = 1f

        // Thrown.
        private var along = THROW_START
        private val throwSpeed = if (run.weapon.trait == Trait.QUICK) 26f else 22f
        private var spin = random.nextFloat() * 360f
        private val ballistic = Vector3()
        private val velocity = Vector3()
        private var spinRate = shape.spinRate

        // Swung.
        private val hand = Vector3(n).crs(e1).nor().let { q -> if (q.dot(Tower.toCamera) < 0f) q.scl(-1f) else q }
        private val reach = shape.reach * shape.scale
        private val pivot = Vector3(plan.center).mulAdd(hand, reach)
        private val hitAngle = -atan(0.62f / reach) * MathUtils.radiansToDegrees
        private val windup = run.weapon.trait == Trait.CLEAVE || run.weapon.trait == Trait.MOMENTUM
        private val startAngle = if (windup) -100f else -85f
        private val swingTime = when (run.weapon.trait) {
            Trait.PRECISION -> 0.11f
            Trait.MOMENTUM -> 0.19f
            else -> 0.16f
        }
        private var angle = if (windup) -80f else startAngle
        private var retractFrom = 0f

        private val xAxis = Vector3()
        private val yAxis = Vector3()
        private val zAxis = Vector3()
        private val pos = Vector3()
        private val m = Matrix4()

        fun update(dt: Float) {
            t += dt
            if (thrown) updateThrown(dt) else updateSwung(dt)
            trail()
        }

        private fun reachHit() {
            if (hit) return
            hit = true
            applyHit(plan)
            if (kind == null || kind == CutKind.CLEAN) released = true
        }

        private fun updateThrown(dt: Float) {
            if (leaving) {
                leaveTime += dt
                velocity.y -= 9f * dt
                ballistic.mulAdd(velocity, dt)
                spin += spinRate * dt
                alpha = max(0f, 1f - leaveTime / 0.6f)
                if (leaveTime > 0.6f) done = true
                return
            }
            if (holdTime >= 0f) {
                holdTime += dt
                if (holdTime > HOLD_TIME) leave(Vector3(e1).scl(-1.5f).add(0f, 2.5f, 0f), 200f)
                return
            }
            along += throwSpeed * dt
            spin += spinRate * dt
            if (!hit && along >= THROW_HIT) {
                reachHit()
                if (kind == CutKind.BOUNCE) {
                    along = THROW_HIT
                    leave(Vector3(e1).scl(-5f).add(0f, 3.5f, 0f), -spinRate)
                    return
                }
            }
            if (hit && kind == CutKind.STUCK && along >= THROW_HIT + 1.2f * penetration) {
                along = THROW_HIT + 1.2f * penetration
                holdTime = 0f
            }
            if (along > -THROW_START) done = true
        }

        private fun leave(v: Vector3, newSpin: Float) {
            leaving = true
            released = true
            ballistic.set(plan.center).mulAdd(e1, along)
            velocity.set(v)
            spinRate = newSpin
        }

        private fun updateSwung(dt: Float) {
            if (leaving) {
                leaveTime += dt
                val k = min(1f, leaveTime / 0.22f)
                angle = MathUtils.lerp(retractFrom, startAngle - 10f, k * k)
                alpha = 1f - k
                if (k >= 1f) done = true
                return
            }
            if (holdTime >= 0f) {
                holdTime += dt
                if (holdTime > HOLD_TIME) retract()
                return
            }
            val wind = if (windup) WINDUP_TIME else 0f
            if (t < wind) {
                angle = MathUtils.lerp(-80f, startAngle, t / wind)
                return
            }
            val k = min(1f, (t - wind) / swingTime)
            val e = k * k * (3f - 2f * k)
            angle = MathUtils.lerp(startAngle, 85f, e)
            if (!hit && angle >= hitAngle) {
                reachHit()
                if (kind == CutKind.BOUNCE) {
                    angle = hitAngle
                    retract()
                    return
                }
            }
            val stop = hitAngle + 2f * abs(hitAngle) * penetration
            if (hit && kind == CutKind.STUCK && angle >= stop) {
                angle = stop
                holdTime = 0f
                return
            }
            if (k >= 1f) {
                alpha = max(0f, alpha - dt / 0.12f)
                if (alpha <= 0f) done = true
            }
        }

        private fun retract() {
            leaving = true
            released = true
            retractFrom = angle
        }

        private fun wobble(): Float = if (holdTime >= 0f && !leaving) 7f * MathUtils.sin(holdTime * 45f) * (1f - holdTime / HOLD_TIME) else 0f

        private fun transform(): Matrix4 {
            if (thrown) {
                if (leaving) pos.set(ballistic) else pos.set(plan.center).mulAdd(e1, along)
                val a = spin + wobble()
                xAxis.set(e1).scl(MathUtils.cosDeg(a)).mulAdd(e2, MathUtils.sinDeg(a))
                yAxis.set(n).crs(xAxis)
                zAxis.set(n)
                val s = shape.scale
                return m.set(xAxis, yAxis, zAxis, pos).scale(s, s, s).translate(-shape.spinCenter, 0f, 0f)
            }
            val a = angle + wobble()
            xAxis.set(hand).scl(-MathUtils.cosDeg(a)).mulAdd(e1, MathUtils.sinDeg(a))
            yAxis.set(hand).scl(MathUtils.sinDeg(a)).mulAdd(e1, MathUtils.cosDeg(a))
            zAxis.set(xAxis).crs(yAxis)
            val s = shape.scale
            return m.set(xAxis, yAxis, zAxis, pivot).scale(s, s, s)
        }

        private fun trail() {
            if (alpha < 0.3f) return
            transform()
            if (thrown) {
                tmp.set(pos)
                cam.project(tmp)
                weaponTrail.add(v2a.set(tmp.x, tmp.y), v2a)
            } else {
                tmp.set(shape.maxX * 0.98f, 0f, 0f).mul(m)
                cam.project(tmp)
                tmp2.set(shape.maxX * 0.6f, 0f, 0f).mul(m)
                cam.project(tmp2)
                weaponTrail.add(v2a.set(tmp.x, tmp.y), v2b.set(tmp2.x, tmp2.y))
            }
        }

        fun draw() {
            if (alpha <= 0f) return
            val mat = transform()
            shape.parts.forEachIndexed { i, part ->
                shader.plain(part.rgb, part.metal, part.rough)
                shader.draw(weaponModel.meshes[i], mat, alpha, heightShade = 0f, cull = false)
            }
        }
    }

    companion object {
        const val FOV = 38f
        const val PITCH = 16f
        const val TOWER_SCREEN_WIDTH = 1.45f
        const val TOWER_WIDTH_FRACTION = 0.42f
        const val MIN_TILT = 5f
        const val MAX_TILT = 60f
        const val MAX_SWIPE_ANGLE = 75f
        const val SLOW_MO_TIME = 0.4f
        const val SQUASH_TIME = 0.12f
        const val THROW_START = -3.2f
        const val THROW_HIT = -0.6f
        const val HOLD_TIME = 0.28f
        const val WINDUP_TIME = 0.06f
        const val MAX_QUEUE = 3
        const val HASTE_SPEED = 1.6f
        const val GEM_LIFE = 1f
        val GRAIN_GOLD = Color(1f, 0.82f, 0.25f, 1f)
    }
}
