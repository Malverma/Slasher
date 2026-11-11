package com.slasher.logic

import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConvexPolyTest {
    @Test
    fun boxVolumeAndCentroid() {
        val b = ConvexPoly.box(0f, 0f, 0f, 2f, 1f, 1f)
        assertEquals(2f, b.volume, 1e-4f)
        assertTrue(b.centroid.epsilonEquals(Vector3(1f, 0.5f, 0.5f), 1e-4f))
    }

    @Test
    fun clipHalvesCube() {
        val b = ConvexPoly.box(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f)
        val (below, above) = b.split(CutPlane(Vector3(0f, 1f, 0f), 0f))
        assertEquals(0.5f, below!!.volume, 1e-4f)
        assertEquals(0.5f, above!!.volume, 1e-4f)
        assertNotNull(below.capFace(Vector3(0f, 1f, 0f)))
    }

    @Test
    fun diagonalCutKeepsVolume() {
        val b = ConvexPoly.box(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f)
        val plane = CutPlane(Vector3(0.3f, 1f, 0.2f).nor(), 0.1f)
        val (below, above) = b.split(plane)
        assertEquals(1f, below!!.volume + above!!.volume, 1e-4f)
    }

    @Test
    fun clipOutsideReturnsSameOrNull() {
        val b = ConvexPoly.box(0f, 0f, 0f, 1f, 1f, 1f)
        assertTrue(b.clip(Vector3(0f, 1f, 0f), 5f) === b)
        assertNull(b.clip(Vector3(0f, 1f, 0f), -5f))
    }

    @Test
    fun roundedColumnCrossSection() {
        val col = ConvexPoly.roundedColumn(top = 0f, bottom = -10f)
        // Lower down, the column is a rounded square; its area is a little more than the exact one (facets).
        val slab = col.clip(Vector3(0f, 1f, 0f), -4f)!!.clip(Vector3(0f, -1f, 0f), 5f)!!
        assertEquals(Tower.CROSS_AREA, slab.volume, 0.01f)
        assertEquals(0f, col.maxAlong(Vector3(0f, 1f, 0f)), 1e-4f)
    }
}

class CutPlaneTest {
    @Test
    fun planePassesThroughFrontPoint() {
        val p = CutPlane.fromLine(20f, -0.6f)
        val front = Tower.up.scl(-0.6f).mulAdd(Tower.toCamera, Tower.FRONT_DEPTH)
        assertEquals(0f, p.distance(front), 1e-5f)
        assertTrue(p.n.y > 0f)
        // The swipe line lies in the plane.
        assertEquals(0f, p.n.dot(CutPlane.lineDirection(20f)), 1e-5f)
    }

    @Test
    fun flatSwipeStillLeansTowardCamera() {
        assertEquals(Tower.VIEW_LEAN_DEG, CutPlane.fromLine(0f, 0f).tiltDeg, 0.01f)
    }
}

class MaterialTest {
    @Test
    fun hardnessRisesWithDepth() {
        var last = 0f
        for (d in 0..300) {
            val h = MaterialCurve.hardness(d.toFloat())
            assertTrue(h >= last)
            last = h
        }
        assertEquals(6f, MaterialCurve.hardness(0f), 1e-4f)
        assertEquals("WOOD", MaterialCurve.zone(30f).name)
    }

    @Test
    fun sampleBlendsBetweenKeys() {
        val a = MaterialCurve.sample(0f).base[0]
        val b = MaterialCurve.sample(12f).base[0]
        val mid = MaterialCurve.sample(6f).base[0]
        assertTrue(mid > minOf(a, b) && mid < maxOf(a, b))
    }
}

class CutRulesTest {
    private fun input(hardness: Float, grain: Float = 40f, weak: Float = 1f, speed: Float = 1800f, full: Boolean = true, thickness: Float = 0.4f) =
        CutInput(hardness, 20f, grain, weak, speed, 0.5f, thickness, full, 0)

    @Test
    fun kindsByPower() {
        assertEquals(CutKind.CLEAN, CutRules.resolve(Weapons.knife, input(6f)).kind)
        assertEquals(CutKind.STUCK, CutRules.resolve(Weapons.knife, input(14f)).kind)
        assertEquals(CutKind.BOUNCE, CutRules.resolve(Weapons.knife, input(30f)).kind)
    }

    @Test
    fun grainAndWeakSpotsGetThrough() {
        // Wood stops a plain knife cut cold, but not one with the grain through a weak spot.
        assertEquals(CutKind.BOUNCE, CutRules.resolve(Weapons.knife, input(17f)).kind)
        val r = CutRules.resolve(Weapons.knife, input(17f, grain = 2f, weak = 2f))
        assertEquals(CutKind.CLEAN, r.kind)
        assertTrue(r.perfect)
    }

    @Test
    fun shortSwipeCannotCutClean() {
        assertEquals(CutKind.STUCK, CutRules.resolve(Weapons.knife, input(6f, full = false)).kind)
    }

    @Test
    fun shavingsCostNoEdge() {
        val r = CutRules.resolve(Weapons.knife, input(6f, thickness = 0.05f))
        assertTrue(r.shaving)
        assertEquals(0f, r.edgeLost)
    }

    @Test
    fun bounceCostsMost() {
        val clean = CutRules.resolve(Weapons.knife, input(6f)).edgeLost
        val bounce = CutRules.resolve(Weapons.knife, input(30f)).edgeLost
        assertTrue(bounce > 5 * clean)
    }
}

class RunTest {
    @Test
    fun depthScoreAndZoneBonus() {
        val run = Run(Weapons.knife, startDepth = 11.8f)
        val r = CutRules.resolve(Weapons.knife, CutInput(6f, 20f, 40f, 1f, 1800f, 0.5f, 0.4f, true, 0))
        val bonus = run.apply(r, 0.5f)
        assertEquals(Run.ZONE_BONUS, bonus)
        assertEquals(12.3f, run.depth, 1e-4f)
        assertEquals(r.points + Run.ZONE_BONUS, run.score)
        assertEquals(1, run.streak)
    }

    @Test
    fun edgeRunsOut() {
        val run = Run(Weapons.knife)
        val bounce = CutRules.resolve(Weapons.knife, CutInput(80f, 20f, 40f, 1f, 1800f, 0.5f, 0.4f, true, 0))
        repeat(10) { run.apply(bounce, 0f) }
        assertTrue(run.broken)
        run.revive()
        assertTrue(!run.broken && run.reviveUsed)
    }
}

class SliceBodyTest {
    @Test
    fun sliceSlidesTipsAndLeaves() {
        val plane = CutPlane.fromLine(25f, -0.5f)
        val (below, above) = ConvexPoly.freshColumn.split(plane)
        val body = SliceBody(above!!, plane, below!!.capFace(plane.n), 0.15f, Random(1))
        val phases = mutableListOf(body.phase)
        var t = 0f
        while (body.phase != SliceBody.Phase.DONE && t < 8f) {
            if (body.update(1f / 60f)) phases += body.phase
            t += 1f / 60f
        }
        assertEquals(listOf(SliceBody.Phase.SLIDE, SliceBody.Phase.TIP, SliceBody.Phase.TUMBLE, SliceBody.Phase.DONE), phases)
        assertTrue(t < 5f, "slice took $t s")
    }

    @Test
    fun slidingKeepsContactWithPlane() {
        val plane = CutPlane.fromLine(-30f, -0.4f)
        val (below, above) = ConvexPoly.freshColumn.split(plane)
        val body = SliceBody(above!!, plane, below!!.capFace(plane.n), 0.15f, Random(2))
        val start = plane.distance(body.position)
        repeat(10) { body.update(1f / 60f) }
        assertEquals(SliceBody.Phase.SLIDE, body.phase)
        assertEquals(start, plane.distance(body.position), 1e-4f)
    }
}

class FeaturesTest {
    @Test
    fun weakSpotHelpsOnlyNearby() {
        val f = TowerFeatures(Random(3))
        f.spots += WeakSpot(Vector3(0.5f, -0.6f, 0f), 0.08f)
        val through = CutPlane(CutPlane.normalFor(10f), CutPlane.normalFor(10f).dot(Vector3(0.5f, -0.6f, 0f)))
        assertEquals(2f, f.weakFactor(through, 2f), 1e-4f)
        val far = CutPlane(through.n, through.d + 0.5f)
        assertEquals(1f, f.weakFactor(far, 2f), 1e-4f)
    }

    @Test
    fun grainStaysReachable() {
        val f = TowerFeatures(Random(4))
        repeat(200) {
            f.regenerate(it.toFloat())
            assertTrue(abs(f.grainAngle) in 5f..50f)
        }
    }
}

class GemTest {
    @Test
    fun cutThroughGemFindsIt() {
        val f = TowerFeatures(Random(5))
        f.gem = Gem(GemKind.RED, Vector3(0f, -0.6f, 0f))
        val n = CutPlane.normalFor(15f)
        assertTrue(f.gemHit(CutPlane(n, n.dot(Vector3(0f, -0.6f, 0f)))))
        assertTrue(!f.gemHit(CutPlane(n, n.dot(Vector3(0f, -0.2f, 0f)))))
    }

    @Test
    fun missedGemLeavesWithTheSliceOnlyIfAbove() {
        val f = TowerFeatures(Random(6))
        f.gem = Gem(GemKind.GOLD, Vector3(0f, -0.9f, 0f))
        f.removeAbove(CutPlane.fromLine(0f, -0.3f))
        assertNotNull(f.gem)
        f.removeAbove(CutPlane.fromLine(0f, -1.3f))
        assertNull(f.gem)
    }

    @Test
    fun gemEffects() {
        val run = Run(Weapons.knife)
        assertEquals(Run.GOLD_COINS, run.collect(GemKind.GOLD))
        run.collect(GemKind.RED)
        run.collect(GemKind.BLUE)
        assertEquals(Run.SHARP_BOOST, run.sharpBoost)
        assertTrue(run.hasted)
        run.tick(Run.BOOST_TIME + 0.1f)
        assertEquals(1f, run.sharpBoost)
        assertTrue(!run.hasted)
    }

    @Test
    fun sharpBoostCutsHarderMaterial() {
        val base = CutInput(17f, 20f, 40f, 1f, 1800f, 0.5f, 0.4f, true, 0)
        assertEquals(CutKind.BOUNCE, CutRules.resolve(Weapons.knife, base).kind)
        val boosted = CutInput(17f, 20f, 40f, 1f, 1800f, 0.5f, 0.4f, true, 0, sharpBoost = 1.6f)
        assertEquals(CutKind.STUCK, CutRules.resolve(Weapons.knife, boosted).kind)
    }
}
