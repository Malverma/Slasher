package com.slasher.render

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.slasher.Assets
import com.slasher.ui.text
import kotlin.math.max
import kotlin.random.Random

/** Dust puffs, sparks and shards, simulated in world space and drawn as soft dots on screen. */
class Particles(private val random: Random) {
    private class P(val pos: Vector3, val vel: Vector3, val color: Color, val size: Float, val life: Float, val gravity: Float, val drag: Float) {
        var age = 0f
    }

    private val list = ArrayList<P>()
    private val tmp = Vector3()

    fun dust(at: Vector3, color: Color, count: Int, speed: Float = 1.2f, size: Float = 22f) {
        repeat(count) {
            val v = Vector3(random.nextFloat() - 0.5f, random.nextFloat() * 0.6f, random.nextFloat() - 0.5f).nor().scl(speed * (0.4f + random.nextFloat()))
            list += P(Vector3(at), v, Color(color).also { it.a = 0.55f }, size * (0.6f + random.nextFloat() * 0.8f), 0.5f + random.nextFloat() * 0.4f, -0.4f, 3f)
        }
    }

    fun sparks(at: Vector3, count: Int, dir: Vector3) {
        repeat(count) {
            val v = Vector3(dir).scl(2f + random.nextFloat() * 3f).add((random.nextFloat() - 0.5f) * 4f, random.nextFloat() * 4f, (random.nextFloat() - 0.5f) * 4f)
            val c = if (random.nextBoolean()) Color(1f, 0.92f, 0.55f, 1f) else Color(1f, 0.65f, 0.25f, 1f)
            list += P(Vector3(at), v, c, 7f + random.nextFloat() * 6f, 0.25f + random.nextFloat() * 0.35f, 9f, 0.5f)
        }
    }

    fun update(dt: Float) {
        val it = list.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            if (p.age >= p.life) { it.remove(); continue }
            p.vel.y -= p.gravity * dt
            p.vel.scl(max(0f, 1f - p.drag * dt))
            p.pos.mulAdd(p.vel, dt)
        }
    }

    fun draw(batch: SpriteBatch, assets: Assets, cam: Camera) {
        for (p in list) {
            tmp.set(p.pos)
            cam.project(tmp)
            val k = 1f - p.age / p.life
            val s = p.size * assets.u * (0.6f + 0.6f * k) * 2f
            batch.setColor(p.color.r, p.color.g, p.color.b, p.color.a * k)
            batch.draw(assets.glow, tmp.x - s / 2f, tmp.y - s / 2f, s, s)
        }
        batch.color = Color.WHITE
    }

    fun clear() = list.clear()
}

/** Floating score and label popups (spec §8). Positions are in screen pixels. */
class Popups {
    private class Popup(val text: String, val x: Float, var y: Float, val color: Color, val big: Boolean, val delay: Float) {
        var age = 0f
    }

    private val list = ArrayList<Popup>()

    fun add(text: String, x: Float, y: Float, color: Color = Color.WHITE, big: Boolean = false, delay: Float = 0f) {
        list += Popup(text, x, y, Color(color), big, delay)
    }

    fun update(dt: Float, u: Float) {
        val it = list.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            if (p.age < p.delay) continue
            p.y += 110f * u * dt
            if (p.age > p.delay + 1.1f) it.remove()
        }
    }

    fun draw(batch: SpriteBatch, a: Assets) {
        for (p in list) {
            val t = p.age - p.delay
            if (t < 0f) continue
            val alpha = if (t < 0.8f) 1f else 1f - (t - 0.8f) / 0.3f
            val font: BitmapFont = if (p.big) a.fontMedium else a.fontSmall
            font.setColor(p.color.r, p.color.g, p.color.b, alpha)
            val pop = if (t < 0.12f) 0.6f + t / 0.12f * 0.4f else 1f
            font.data.setScale(pop)
            text(batch, font, p.text, p.x, p.y)
            font.data.setScale(1f)
            font.color = Color.WHITE
        }
    }

    fun clear() = list.clear()
}

/** A fading ribbon through recent points, in screen pixels. Used for the finger and for weapon trails. */
class Ribbon(private val maxAge: Float) {
    private class Pt(val a: Vector2, val b: Vector2) {
        var age = 0f
    }

    private val pts = ArrayList<Pt>()

    /** Adds a ribbon cross-section from [a] to [b] (pass the same point twice for a line). */
    fun add(a: Vector2, b: Vector2) {
        pts += Pt(Vector2(a), Vector2(b))
    }

    fun update(dt: Float) {
        pts.forEach { it.age += dt }
        pts.removeAll { it.age > maxAge }
    }

    fun clear() = pts.clear()

    /** Draws as a filled strip; [width] adds thickness to line-like ribbons. */
    fun draw(shapes: ShapeRenderer, color: Color, width: Float, alpha: Float = 1f) {
        for (i in 1 until pts.size) {
            val p0 = pts[i - 1]
            val p1 = pts[i]
            val k0 = (1f - p0.age / maxAge).coerceIn(0f, 1f)
            val k1 = (1f - p1.age / maxAge).coerceIn(0f, 1f)
            if (p0.a.epsilonEquals(p0.b, 0.5f)) {
                shapes.setColor(color.r, color.g, color.b, alpha * k1)
                shapes.rectLine(p0.a, p1.a, width * (0.3f + 0.7f * k1))
                shapes.circle(p1.a.x, p1.a.y, width * (0.3f + 0.7f * k1) / 2f, 10)
            } else {
                shapes.setColor(color.r, color.g, color.b, alpha * k0 * 0.55f)
                shapes.triangle(p0.a.x, p0.a.y, p0.b.x, p0.b.y, p1.a.x, p1.a.y)
                shapes.setColor(color.r, color.g, color.b, alpha * k1 * 0.55f)
                shapes.triangle(p1.a.x, p1.a.y, p0.b.x, p0.b.y, p1.b.x, p1.b.y)
            }
        }
    }
}
