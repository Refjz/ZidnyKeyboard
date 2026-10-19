package com.zidny.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** An animation played where a key is pressed, picked in the settings. */
enum class KeyEffect(val displayName: String, val englishName: String) {
    NONE("Tanpa efek", "No effect"),
    BUBBLE("Gelembung", "Bubbles"),
    SMOKE("Asap", "Smoke"),
    SPARKLE("Kilau bintang", "Sparkles"),
    RIPPLE("Riak air", "Ripple"),
    HEARTS("Hati", "Hearts"),
    CONFETTI("Konfeti", "Confetti"),
    FIRE("Api", "Fire"),
    SNOW("Salju", "Snow"),
}

/**
 * Draws the [KeyEffect] particles over the keys. One view draws them all and only redraws itself, so a key
 * press never adds views or makes the keyboard lay itself out again. It takes no touches.
 */
class KeyEffectsView(context: Context, private val accent: Int, private val ink: Int) : View(context) {

    private class Particle(
        val shape: Int, var x: Float, var y: Float, val vx: Float, val vy: Float,
        val size: Float, val grow: Float, val color: Int, val start: Long, val life: Long,
        val text: String = "", val spin: Float = 0f,
    )

    private val particles = mutableListOf<Particle>()
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val confettiColors = intArrayOf(
        Color.parseColor("#FF5A5F"), Color.parseColor("#FFC93C"), Color.parseColor("#3EC1D3"),
        Color.parseColor("#7C5CE0"), Color.parseColor("#1FA971"),
    )

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Starts [effect] at ([x], [y]) inside this view; [keyHeight] scales how far it travels. */
    fun play(effect: KeyEffect, x: Float, y: Float, keyHeight: Int) {
        if (effect == KeyEffect.NONE) return
        val now = SystemClock.uptimeMillis()
        val h = keyHeight.toFloat()
        // Speeds are in pixels per millisecond.
        when (effect) {
            KeyEffect.NONE -> {}
            KeyEffect.BUBBLE -> repeat(3) {
                add(Particle(RING, x + rnd(h / 3), y, rnd(0.02f), -(0.08f + Random.nextFloat() * 0.06f) * h / 50,
                    dp(5f + Random.nextFloat() * 5), 0.4f, accent, now, 650))
            }
            KeyEffect.SMOKE -> repeat(4) {
                add(Particle(DOT, x + rnd(h / 4), y, rnd(0.03f), -0.06f * h / 50, dp(6f + Random.nextFloat() * 4), 1.6f,
                    Color.argb(110, 150, 150, 155), now, 700))
            }
            KeyEffect.SPARKLE -> repeat(5) { i ->
                val angle = (i * 72 + Random.nextInt(30)) * Math.PI / 180
                add(Particle(TEXT, x, y, (cos(angle) * 0.04 * density).toFloat(), (sin(angle) * 0.04 * density).toFloat(), dp(12f), 0f,
                    accent, now, 450, "✦"))
            }
            KeyEffect.RIPPLE -> {
                add(Particle(RING, x, y, 0f, 0f, dp(6f), 5f, accent, now, 420))
                add(Particle(RING, x, y, 0f, 0f, dp(3f), 6f, accent, now + 90, 420))
            }
            KeyEffect.HEARTS -> repeat(2) {
                add(Particle(TEXT, x + rnd(h / 3), y, rnd(0.02f), -0.1f * h / 50, dp(14f + Random.nextFloat() * 6), 0.3f,
                    Color.parseColor("#FF4D6D"), now, 650, "❤"))
            }
            KeyEffect.CONFETTI -> repeat(8) {
                val angle = Random.nextFloat() * 2 * Math.PI
                add(Particle(RECT, x, y, (cos(angle) * 0.05 * density).toFloat(), (sin(angle) * 0.05 * density).toFloat() - 0.015f * density, dp(5f), 0f,
                    confettiColors[it % confettiColors.size], now, 550, spin = 0.8f))
            }
            KeyEffect.FIRE -> repeat(3) {
                add(Particle(TEXT, x + rnd(h / 4), y, rnd(0.015f), -0.09f * h / 50, dp(12f + Random.nextFloat() * 6), 0.5f,
                    accent, now, 500, "🔥"))
            }
            KeyEffect.SNOW -> repeat(4) {
                add(Particle(TEXT, x + rnd(h / 2), y - h / 2, rnd(0.02f), 0.05f * h / 50, dp(10f + Random.nextFloat() * 5), 0f,
                    ink, now, 800, "❄", spin = 0.2f))
            }
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            val age = now - p.start
            if (age > p.life) {
                iterator.remove()
                continue
            }
            if (age < 0) continue
            val t = age.toFloat() / p.life
            val x = p.x + p.vx * age
            val y = p.y + p.vy * age
            val size = p.size * (1 + p.grow * t)
            val alpha = ((1 - t) * Color.alpha(p.color)).toInt()
            paint.color = p.color
            paint.alpha = alpha
            when (p.shape) {
                DOT -> {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(x, y, size, paint)
                }
                RING -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = dp(1.5f)
                    canvas.drawCircle(x, y, size, paint)
                }
                RECT -> {
                    paint.style = Paint.Style.FILL
                    canvas.save()
                    canvas.rotate(p.spin * age, x, y)
                    canvas.drawRect(x - size / 2, y - size / 4, x + size / 2, y + size / 4, paint)
                    canvas.restore()
                }
                TEXT -> {
                    paint.style = Paint.Style.FILL
                    paint.textSize = size
                    paint.textAlign = Paint.Align.CENTER
                    canvas.save()
                    if (p.spin != 0f) canvas.rotate(p.spin * age, x, y)
                    canvas.drawText(p.text, x, y + size / 3, paint)
                    canvas.restore()
                }
            }
        }
        if (particles.isNotEmpty()) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        particles.clear()
        super.onDetachedFromWindow()
    }

    private fun add(particle: Particle) {
        // Fast typing should never pile up enough particles to slow the drawing down.
        if (particles.size >= MAX_PARTICLES) particles.removeAt(0)
        particles.add(particle)
    }

    private fun rnd(range: Float) = (Random.nextFloat() * 2 - 1) * range

    private fun dp(value: Float) = value * density

    private companion object {
        const val DOT = 0
        const val RING = 1
        const val RECT = 2
        const val TEXT = 3
        const val MAX_PARTICLES = 80
    }
}
