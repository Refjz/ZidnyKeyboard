package com.zidny.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** How a key press sounds when typing sounds are on. */
enum class SoundStyle(val displayName: String, val englishName: String) {
    SYSTEM("Bawaan HP", "Phone default"),
    TYPEWRITER("Mesin tik", "Typewriter"),
    MECHANICAL("Keyboard mekanik", "Mechanical keyboard"),
    BUBBLE("Bubble pop", "Bubble pop"),
    SOFT("Lembut", "Soft tap"),
    WOOD("Kayu", "Wood block"),
}

/**
 * Plays the [SoundStyle] key sounds. They are synthesised once into small WAV files and played through a
 * SoundPool, so a key press only triggers an already loaded sound.
 */
object TypingSounds {
    private var pool: SoundPool? = null
    /** Loaded sound ids per style: [0] for letters, [1] for space, enter and delete. */
    private val ids = mutableMapOf<SoundStyle, IntArray>()

    fun prepare(context: Context) {
        if (pool != null) return
        val newPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        pool = newPool
        val dir = File(context.applicationContext.cacheDir, "sounds").apply { mkdirs() }
        thread(name = "typing-sounds") {
            SoundStyle.entries.filter { it != SoundStyle.SYSTEM }.forEach { style ->
                val loaded = IntArray(2) { variant ->
                    val file = File(dir, "${style.name.lowercase()}_v${SOUND_VERSION}_$variant.wav")
                    if (!file.exists()) writeWav(file, synthesize(style, variant == 1))
                    newPool.load(file.path, 1)
                }
                synchronized(ids) { ids[style] = loaded }
            }
        }
    }

    /** Plays [style]; [big] is for space, enter and delete. Returns false when it cannot play yet. */
    fun play(style: SoundStyle, big: Boolean): Boolean {
        val id = synchronized(ids) { ids[style] }?.get(if (big) 1 else 0) ?: return false
        pool?.play(id, 1f, 1f, 1, 0, 1f + Random.nextFloat() * 0.06f - 0.03f)
        return true
    }

    private fun synthesize(style: SoundStyle, big: Boolean): ShortArray {
        val lengthMs = when (style) {
            SoundStyle.BUBBLE -> 90
            SoundStyle.MECHANICAL -> 70
            else -> 60
        }
        val samples = FloatArray(RATE * lengthMs / 1000)
        val random = Random(style.ordinal * 2 + if (big) 1 else 0)
        for (i in samples.indices) {
            val t = i.toFloat() / RATE
            samples[i] = when (style) {
                SoundStyle.SYSTEM -> 0f
                SoundStyle.TYPEWRITER -> {
                    val click = (random.nextFloat() * 2 - 1) * exp(-t * 900f)
                    val ring = sin(2 * PI * (if (big) 1700.0 else 2600.0) * t).toFloat() * exp(-t * 90f) * 0.35f
                    click + ring
                }
                SoundStyle.MECHANICAL -> {
                    val click = (random.nextFloat() * 2 - 1) * exp(-t * 1400f) * 0.7f
                    val thock = sin(2 * PI * (if (big) 140.0 else 210.0) * t).toFloat() * exp(-t * 60f)
                    click + thock
                }
                SoundStyle.BUBBLE -> {
                    val frequency = (if (big) 300.0 else 450.0) + 9000.0 * t
                    sin(2 * PI * frequency * t).toFloat() * sin(PI * t / (lengthMs / 1000.0)).toFloat()
                }
                SoundStyle.SOFT -> sin(2 * PI * (if (big) 420.0 else 640.0) * t).toFloat() * exp(-t * 110f) * 0.6f
                SoundStyle.WOOD -> {
                    val body = sin(2 * PI * (if (big) 520.0 else 820.0) * t).toFloat() * exp(-t * 75f)
                    body + sin(2 * PI * 1900.0 * t).toFloat() * exp(-t * 250f) * 0.3f
                }
            }
        }
        // Short fade-in so the start does not pop.
        val fade = RATE / 2000
        for (i in 0 until minOf(fade, samples.size)) samples[i] *= i.toFloat() / fade
        val peak = samples.maxOf { kotlin.math.abs(it) }.coerceAtLeast(0.001f)
        return ShortArray(samples.size) { (samples[it] / peak * 0.8f * Short.MAX_VALUE).toInt().toShort() }
    }

    private fun writeWav(file: File, pcm: ShortArray) {
        val data = pcm.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2)
            putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data)
        }
        val body = ByteBuffer.allocate(data).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { body.putShort(it) }
        val temp = File(file.path + ".tmp")
        FileOutputStream(temp).use {
            it.write(header.array())
            it.write(body.array())
        }
        temp.renameTo(file)
    }

    private const val RATE = 22050
    /** Bump when the sounds change, so old files are not reused. */
    private const val SOUND_VERSION = 1
}
