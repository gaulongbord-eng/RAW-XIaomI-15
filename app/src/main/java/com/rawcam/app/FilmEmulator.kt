package com.rawcam.app

import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.media.Image
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One film stock. Grade = dye cross-talk matrix (linear) -> tone curve with highlight shoulder ->
 * saturation -> per-channel slope/offset/power (colour crossover) -> S-curve -> split toning -> fade.
 */
class FilmLook(
    val name: String,
    val desc: String,
    val bw: Boolean,
    val exposure: Float,
    val mix: FloatArray,
    val slope: FloatArray,
    val offset: FloatArray,
    val power: FloatArray,
    val saturation: Float,
    val contrast: Float,
    val fade: Float,
    val shadow: FloatArray,
    val highlight: FloatArray,
    val grain: Float,
    val grainSize: Float,
    val chromaGrain: Float,
    val shoulder: Float,
    val vignette: Float,
    val bwMix: FloatArray
)

/**
 * Develops a Bayer RAW16 frame into an sRGB bitmap and applies a film look:
 * (denoised) demosaic -> white balance -> camera->sRGB matrix -> film grade -> grain.
 */
object FilmEmulator {

    val looks: List<FilmLook> = listOf(
        FilmLook("Portra 160", "Soft pastel, creamy skin, muted greens",
            bw = false, exposure = 0.0f,
            mix = floatArrayOf(1f, 0.02f, -0.02f, -0.02f, 1.03f, -0.01f, 0f, 0.03f, 0.97f),
            slope = floatArrayOf(1.03f, 1f, 0.98f), offset = floatArrayOf(0.01f, 0.012f, 0.02f), power = floatArrayOf(1f, 1f, 0.97f),
            saturation = 0.86f, contrast = 0.22f, fade = 0.035f,
            shadow = floatArrayOf(-0.004f, 0.008f, 0.014f), highlight = floatArrayOf(0.018f, 0.01f, -0.004f),
            grain = 0.016f, grainSize = 1.0f, chromaGrain = 0.25f,
            shoulder = 0.65f, vignette = 0.04f, bwMix = floatArrayOf(0.3f, 0.5f, 0.2f)),
        FilmLook("Portra 400", "Warm natural skin, balanced pastel colour",
            bw = false, exposure = 0.0f,
            mix = floatArrayOf(1.02f, 0f, -0.02f, -0.03f, 1.04f, -0.01f, 0f, 0f, 1f),
            slope = floatArrayOf(1.05f, 1f, 0.95f), offset = floatArrayOf(0.008f, 0.008f, 0.014f), power = floatArrayOf(0.99f, 1f, 1.02f),
            saturation = 0.96f, contrast = 0.3f, fade = 0.028f,
            shadow = floatArrayOf(0f, 0.008f, 0.012f), highlight = floatArrayOf(0.026f, 0.012f, -0.01f),
            grain = 0.022f, grainSize = 1.1f, chromaGrain = 0.3f,
            shoulder = 0.62f, vignette = 0.05f, bwMix = floatArrayOf(0.3f, 0.5f, 0.2f)),
        FilmLook("Portra 800", "Low-light warm portrait stock, rich shadows",
            bw = false, exposure = 0.15f,
            mix = floatArrayOf(1.03f, -0.01f, -0.02f, -0.03f, 1.04f, -0.01f, 0f, 0f, 1f),
            slope = floatArrayOf(1.07f, 1f, 0.94f), offset = floatArrayOf(0.01f, 0.01f, 0.018f), power = floatArrayOf(0.98f, 1f, 1.03f),
            saturation = 1.02f, contrast = 0.34f, fade = 0.03f,
            shadow = floatArrayOf(0.004f, 0.016f, 0.02f), highlight = floatArrayOf(0.03f, 0.014f, -0.012f),
            grain = 0.034f, grainSize = 1.3f, chromaGrain = 0.35f,
            shoulder = 0.62f, vignette = 0.08f, bwMix = floatArrayOf(0.3f, 0.5f, 0.2f)),
        FilmLook("Ektar 100", "Vivid saturated colour, punchy reds and blues",
            bw = false, exposure = 0.0f,
            mix = floatArrayOf(1.06f, -0.04f, -0.02f, -0.04f, 1.08f, -0.04f, -0.02f, -0.05f, 1.07f),
            slope = floatArrayOf(1.03f, 1f, 0.99f), offset = floatArrayOf(0f, 0f, 0f), power = floatArrayOf(1.02f, 1f, 0.98f),
            saturation = 1.32f, contrast = 0.5f, fade = 0.0f,
            shadow = floatArrayOf(0f, 0f, 0.006f), highlight = floatArrayOf(0.012f, 0.004f, -0.006f),
            grain = 0.01f, grainSize = 0.9f, chromaGrain = 0.2f,
            shoulder = 0.35f, vignette = 0.03f, bwMix = floatArrayOf(0.3f, 0.5f, 0.2f)),
        FilmLook("Gold 200", "Golden warm nostalgia, yellow highlights",
            bw = false, exposure = 0.0f,
            mix = floatArrayOf(1.04f, 0.02f, -0.06f, -0.03f, 1.06f, -0.03f, 0f, 0.02f, 0.98f),
            slope = floatArrayOf(1.09f, 1.01f, 0.86f), offset = floatArrayOf(0.01f, 0.008f, 0f), power = floatArrayOf(0.98f, 1f, 1.06f),
            saturation = 1.14f, contrast = 0.38f, fade = 0.02f,
            shadow = floatArrayOf(0.008f, 0.004f, -0.006f), highlight = floatArrayOf(0.034f, 0.02f, -0.026f),
            grain = 0.026f, grainSize = 1.2f, chromaGrain = 0.35f,
            shoulder = 0.5f, vignette = 0.07f, bwMix = floatArrayOf(0.3f, 0.5f, 0.2f)),
        FilmLook("ColorPlus 200", "Consumer film, warm, green-cyan shadows",
            bw = false, exposure = 0.0f,
            mix = floatArrayOf(1.02f, 0.02f, -0.04f, -0.04f, 1.06f, -0.02f, 0f, 0.04f, 0.96f),
            slope = floatArrayOf(1.06f, 1f, 0.9f), offset = floatArrayOf(0f, 0.016f, 0.012f), power = floatArrayOf(0.99f, 0.98f, 1.04f),
            saturation = 1.05f, contrast = 0.42f, fade = 0.04f,
            shadow = floatArrayOf(-0.006f, 0.02f, 0.01f), highlight = floatArrayOf(0.024f, 0.014f, -0.014f),
            grain = 0.03f, grainSize = 1.2f, chromaGrain = 0.4f,
            shoulder = 0.5f, vignette = 0.1f, bwMix = floatArrayOf(0.3f, 0.5f, 0.2f)),
        FilmLook("Ektachrome E100", "Clean slide film, cool blue-cyan, deep contrast",
            bw = false, exposure = 0.0f,
            mix = floatArrayOf(1f, -0.02f, 0.02f, -0.03f, 1.04f, -0.01f, 0.01f, -0.03f, 1.02f),
            slope = floatArrayOf(0.96f, 1f, 1.06f), offset = floatArrayOf(0f, 0f, 0.004f), power = floatArrayOf(1.03f, 1f, 0.96f),
            saturation = 1.18f, contrast = 0.58f, fade = 0.0f,
            shadow = floatArrayOf(-0.004f, 0.004f, 0.014f), highlight = floatArrayOf(-0.004f, 0.004f, 0.014f),
            grain = 0.008f, grainSize = 0.9f, chromaGrain = 0.15f,
            shoulder = 0.22f, vignette = 0.02f, bwMix = floatArrayOf(0.3f, 0.5f, 0.2f)),
        FilmLook("Kodachrome 64", "Rich reds, warm yellows, deep blacks",
            bw = false, exposure = 0.0f,
            mix = floatArrayOf(1.1f, -0.06f, -0.04f, 0f, 1.04f, -0.04f, -0.04f, -0.02f, 1.06f),
            slope = floatArrayOf(1.07f, 0.99f, 0.93f), offset = floatArrayOf(0f, 0f, 0f), power = floatArrayOf(1.04f, 1.01f, 0.97f),
            saturation = 1.22f, contrast = 0.62f, fade = 0.004f,
            shadow = floatArrayOf(0.008f, 0f, 0f), highlight = floatArrayOf(0.026f, 0.006f, -0.018f),
            grain = 0.014f, grainSize = 1.0f, chromaGrain = 0.25f,
            shoulder = 0.28f, vignette = 0.06f, bwMix = floatArrayOf(0.3f, 0.5f, 0.2f)),
        FilmLook("Tri-X 400", "Classic black & white, gritty contrast",
            bw = true, exposure = 0.0f,
            mix = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            slope = floatArrayOf(1f, 1f, 1f), offset = floatArrayOf(0f, 0f, 0f), power = floatArrayOf(1f, 1f, 1f),
            saturation = 0.0f, contrast = 0.62f, fade = 0.025f,
            shadow = floatArrayOf(0.002f, 0.002f, 0f), highlight = floatArrayOf(0.004f, 0.003f, 0f),
            grain = 0.045f, grainSize = 1.4f, chromaGrain = 0.0f,
            shoulder = 0.45f, vignette = 0.14f, bwMix = floatArrayOf(0.36f, 0.46f, 0.18f)),
        FilmLook("Vision3 500T", "Cinema tungsten: teal shadows, warm highlights",
            bw = false, exposure = 0.0f,
            mix = floatArrayOf(1f, 0.02f, -0.02f, -0.02f, 1.02f, 0f, 0.02f, 0.04f, 0.94f),
            slope = floatArrayOf(0.95f, 0.99f, 1.1f), offset = floatArrayOf(0f, 0.01f, 0.024f), power = floatArrayOf(1.02f, 1f, 0.96f),
            saturation = 0.9f, contrast = 0.3f, fade = 0.032f,
            shadow = floatArrayOf(-0.012f, 0.014f, 0.03f), highlight = floatArrayOf(0.034f, 0.014f, -0.014f),
            grain = 0.032f, grainSize = 1.4f, chromaGrain = 0.35f,
            shoulder = 0.72f, vignette = 0.09f, bwMix = floatArrayOf(0.3f, 0.5f, 0.2f))
    )

    fun byName(n: String?): FilmLook? = looks.firstOrNull { it.name == n }

    private const val LUT_N = 16384
    private const val LUT_MAX = 4f

    /** Linear -> display. Unity slope up to a knee, soft highlight shoulder above it. */
    private fun buildLut(shoulder: Float): FloatArray {
        val knee = 0.80f - 0.45f * shoulder
        return FloatArray(LUT_N) { i ->
            val x = i * LUT_MAX / (LUT_N - 1)
            val v = if (x < knee) x else knee + (1f - knee) * (1f - exp(-(x - knee) / (1f - knee)))
            val c = v.coerceIn(0f, 1f)
            if (c <= 0.0031308f) 12.92f * c else 1.055f * c.pow(1f / 2.4f) - 0.055f
        }
    }

    private const val NS = 1024

    private fun noiseTile(size: Float): FloatArray {
        val rnd = Random(12345)
        var a = FloatArray(NS * NS) { rnd.nextGaussian().toFloat() }
        val k = max(1, min(5, size.roundToInt()))
        if (k > 1) {
            repeat(2) {
                val b = FloatArray(NS * NS)
                for (y in 0 until NS) for (x in 0 until NS) {
                    var s = 0f
                    for (d in 0 until k) s += a[y * NS + ((x + d) and (NS - 1))]
                    b[y * NS + x] = s / k
                }
                val c = FloatArray(NS * NS)
                for (y in 0 until NS) for (x in 0 until NS) {
                    var s = 0f
                    for (d in 0 until k) s += b[((y + d) and (NS - 1)) * NS + x]
                    c[y * NS + x] = s / k
                }
                a = c
            }
        }
        var m = 0.0
        for (v in a) m += v * v
        val sd = sqrt(m / a.size).toFloat().coerceAtLeast(1e-6f)
        for (i in a.indices) a[i] /= sd
        return a
    }

    /**
     * @param strength  0..1.5  how much of the film grade is applied (0 = neutral render)
     * @param grainMul  0..2    grain amount relative to the stock's own grain
     */
    fun develop(
        raw: Image, meta: CaptureResult, chars: CameraCharacteristics, look: FilmLook, deg: Int,
        strength: Float, grainMul: Float
    ): Bitmap {
        val w = raw.width
        val h = raw.height
        val plane = raw.planes[0]
        val rs = plane.rowStride
        val buf = plane.buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)

        val cfa = when (chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT) ?: 0) {
            1 -> intArrayOf(1, 0, 2, 1)   // GRBG
            2 -> intArrayOf(1, 2, 0, 1)   // GBRG
            3 -> intArrayOf(2, 1, 1, 0)   // BGGR
            else -> intArrayOf(0, 1, 1, 2) // RGGB  (0=R 1=G 2=B), index = (y&1)*2 + (x&1)
        }
        val white = (chars.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL) ?: 1023).toFloat()
        val pat = chars.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
        val bl = FloatArray(4) { i -> pat?.getOffsetForIndex(i and 1, i shr 1)?.toFloat() ?: 64f }
        val inv = FloatArray(4) { 1f / max(1f, white - bl[it]) }

        // ---- white balance
        var gr = 1f
        var gb = 1f
        val neutral = meta.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT)
        if (neutral != null && neutral.size >= 3 && neutral[0].toFloat() > 0f && neutral[2].toFloat() > 0f) {
            gr = neutral[1].toFloat() / neutral[0].toFloat()
            gb = neutral[1].toFloat() / neutral[2].toFloat()
        } else {
            meta.get(CaptureResult.COLOR_CORRECTION_GAINS)?.let {
                if (it.greenEven > 0f) {
                    gr = it.red / it.greenEven
                    gb = it.blue / it.greenEven
                }
            }
        }

        // ---- camera (white balanced) -> linear sRGB
        val x2s = floatArrayOf(
            3.1338561f, -1.6168667f, -0.4906146f,
            -0.9787684f, 1.9161415f, 0.0334540f,
            0.0719453f, -0.2289914f, 1.4052427f
        )
        val m = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        val fm = chars.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2)
            ?: chars.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1)
        if (fm != null) {
            val f = FloatArray(9) { fm.getElement(it % 3, it / 3).toFloat() }
            for (r in 0..2) for (c in 0..2) {
                var s = 0f
                for (k in 0..2) s += x2s[r * 3 + k] * f[k * 3 + c]
                m[r * 3 + c] = s
            }
        }

        fun rd(x: Int, y: Int): Float {
            val xx = if (x < 0) -x else if (x >= w) 2 * w - 2 - x else x
            val yy = if (y < 0) -y else if (y >= h) 2 * h - 2 - y else y
            val i = ((yy and 1) shl 1) or (xx and 1)
            val v = ((buf.getShort(yy * rs + xx * 2).toInt() and 0xFFFF) - bl[i]) * inv[i]
            return if (v < 0f) 0f else v
        }

        // ---- scene key: log-average of green sites -> partial auto exposure
        var sumLog = 0.0
        var cnt = 0
        var sy = 1
        while (sy < h - 1) {
            var sx = 1
            while (sx < w - 1) {
                if (cfa[((sy and 1) shl 1) or (sx and 1)] == 1) {
                    sumLog += ln(max(rd(sx, sy), 1e-4f).toDouble())
                    cnt++
                }
                sx += 13
            }
            sy += 13
        }
        val avg = if (cnt > 0) exp(sumLog / cnt).toFloat() else 0.1f
        val autoScale = (0.20f / max(avg, 1e-4f)).pow(0.75f).coerceIn(0.4f, 10f) * 2f.pow(look.exposure)

        // ---- sensor-noise reduction inside the demosaic, stronger with higher ISO
        val iso = (meta.get(CaptureResult.SENSOR_SENSITIVITY) ?: 100).coerceAtLeast(50)
        val nrAmt = (ln(iso / 100.0) / ln(2.0) / 3.0).toFloat().coerceIn(0f, 0.85f).let { if (it < 0.12f) 0f else it }
        val sigma = if (iso > 800) 1.25f else 0.95f
        val gw = FloatArray(25) { i ->
            val dx = i % 5 - 2
            val dy = i / 5 - 2
            exp(-(dx * dx + dy * dy) / (2f * sigma * sigma))
        }

        val lutN = buildLut(0.5f)
        val lutF = buildLut(look.shoulder)
        val lutScale = (LUT_N - 1) / LUT_MAX
        val noise = noiseTile(look.grainSize * (w / 2200f))
        val gAmt = look.grain * grainMul
        val st = strength

        val rot = when (deg) { 90 -> 1; 180 -> 2; 270 -> 3; else -> 0 }
        val outW = if (rot == 1 || rot == 3) h else w
        val outH = if (rot == 1 || rot == 3) w else h
        val out = IntArray(outW * outH)
        val cx = w / 2f
        val cy = h / 2f

        val mx = look.mix
        val sl = look.slope
        val of = look.offset
        val pw = look.power
        val sh = look.shadow
        val hi = look.highlight
        val cg = look.chromaGrain

        fun row(y: Int) {
            for (x in 0 until w) {
                val c = cfa[((y and 1) shl 1) or (x and 1)]
                val ctr = rd(x, y)
                var r: Float
                var g: Float
                var b: Float
                if (c == 1) {
                    val hAvg = (rd(x - 1, y) + rd(x + 1, y)) * 0.5f
                    val vAvg = (rd(x, y - 1) + rd(x, y + 1)) * 0.5f
                    val hIsRed = cfa[((y and 1) shl 1) or ((x + 1) and 1)] == 0
                    g = ctr
                    if (hIsRed) { r = hAvg; b = vAvg } else { b = hAvg; r = vAvg }
                } else {
                    val cross = (rd(x - 1, y) + rd(x + 1, y) + rd(x, y - 1) + rd(x, y + 1)) * 0.25f
                    val diag = (rd(x - 1, y - 1) + rd(x + 1, y - 1) + rd(x - 1, y + 1) + rd(x + 1, y + 1)) * 0.25f
                    g = cross
                    if (c == 0) { r = ctr; b = diag } else { b = ctr; r = diag }
                }
                if (nrAmt > 0f) {
                    var sr = 0f; var sg = 0f; var sb = 0f
                    var wr = 0f; var wg = 0f; var wb = 0f
                    for (i in 0 until 25) {
                        val xx = x + i % 5 - 2
                        val yy = y + i / 5 - 2
                        val wgt = gw[i]
                        val v = rd(xx, yy)
                        when (cfa[((yy and 1) shl 1) or (xx and 1)]) {
                            0 -> { sr += v * wgt; wr += wgt }
                            1 -> { sg += v * wgt; wg += wgt }
                            else -> { sb += v * wgt; wb += wgt }
                        }
                    }
                    if (wr > 0f) r += (sr / wr - r) * nrAmt
                    if (wg > 0f) g += (sg / wg - g) * nrAmt
                    if (wb > 0f) b += (sb / wb - b) * nrAmt
                }
                r *= gr
                b *= gb
                var lr = (m[0] * r + m[1] * g + m[2] * b) * autoScale
                var lg = (m[3] * r + m[4] * g + m[5] * b) * autoScale
                var lb = (m[6] * r + m[7] * g + m[8] * b) * autoScale
                if (lr < 0f) lr = 0f
                if (lg < 0f) lg = 0f
                if (lb < 0f) lb = 0f

                // neutral reference render
                val nr0 = lutN[min(LUT_N - 1, (lr * lutScale).toInt())]
                val ng0 = lutN[min(LUT_N - 1, (lg * lutScale).toInt())]
                val nb0 = lutN[min(LUT_N - 1, (lb * lutScale).toInt())]

                // ---- film grade
                var xr = max(0f, mx[0] * lr + mx[1] * lg + mx[2] * lb)
                var xg = max(0f, mx[3] * lr + mx[4] * lg + mx[5] * lb)
                var xb = max(0f, mx[6] * lr + mx[7] * lg + mx[8] * lb)
                if (look.bw) {
                    val yy = look.bwMix[0] * xr + look.bwMix[1] * xg + look.bwMix[2] * xb
                    xr = yy; xg = yy; xb = yy
                }
                var dr = lutF[min(LUT_N - 1, (xr * lutScale).toInt())]
                var dg = lutF[min(LUT_N - 1, (xg * lutScale).toInt())]
                var db = lutF[min(LUT_N - 1, (xb * lutScale).toInt())]
                var l = 0.2126f * dr + 0.7152f * dg + 0.0722f * db
                if (!look.bw) {
                    dr = l + (dr - l) * look.saturation
                    dg = l + (dg - l) * look.saturation
                    db = l + (db - l) * look.saturation
                }
                dr = (dr.coerceIn(0f, 1f) * sl[0] + of[0]).coerceIn(0f, 1f).pow(pw[0])
                dg = (dg.coerceIn(0f, 1f) * sl[1] + of[1]).coerceIn(0f, 1f).pow(pw[1])
                db = (db.coerceIn(0f, 1f) * sl[2] + of[2]).coerceIn(0f, 1f).pow(pw[2])
                dr += look.contrast * (dr * dr * (3f - 2f * dr) - dr)
                dg += look.contrast * (dg * dg * (3f - 2f * dg) - dg)
                db += look.contrast * (db * db * (3f - 2f * db) - db)
                l = 0.2126f * dr + 0.7152f * dg + 0.0722f * db
                val ws = (1f - l) * (1f - l)
                val wh = l * l
                dr += sh[0] * ws + hi[0] * wh
                dg += sh[1] * ws + hi[1] * wh
                db += sh[2] * ws + hi[2] * wh
                val f = look.fade
                dr = (f + dr * (1f - f)).coerceIn(0f, 1f)
                dg = (f + dg * (1f - f)).coerceIn(0f, 1f)
                db = (f + db * (1f - f)).coerceIn(0f, 1f)

                // strength: blend neutral render -> graded render
                dr = nr0 + (dr - nr0) * st
                dg = ng0 + (dg - ng0) * st
                db = nb0 + (db - nb0) * st

                if (look.vignette > 0f) {
                    val dx = (x - cx) / cx
                    val dy = (y - cy) / cy
                    val v = 1f - look.vignette * st * (dx * dx + dy * dy) * 0.5f
                    dr *= v; dg *= v; db *= v
                }
                if (gAmt > 0f) {
                    val lum = (0.2126f * dr + 0.7152f * dg + 0.0722f * db).coerceIn(0f, 1f)
                    val amp = gAmt * (0.35f + 5.2f * lum * (1f - lum))
                    val n0 = noise[((y and (NS - 1)) shl 10) or (x and (NS - 1))]
                    val n1 = noise[(((y + 311) and (NS - 1)) shl 10) or ((x + 517) and (NS - 1))]
                    val n2 = noise[(((y + 673) and (NS - 1)) shl 10) or ((x + 149) and (NS - 1))]
                    val n3 = noise[(((y + 97) and (NS - 1)) shl 10) or ((x + 829) and (NS - 1))]
                    dr += ((1f - cg) * n0 + cg * n1) * amp
                    dg += ((1f - cg) * n0 + cg * n2) * amp
                    db += ((1f - cg) * n0 + cg * n3) * amp
                }
                val ir = (dr.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                val ig = (dg.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                val ib = (db.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

                val ox: Int
                val oy: Int
                when (rot) {
                    1 -> { ox = h - 1 - y; oy = x }
                    2 -> { ox = w - 1 - x; oy = h - 1 - y }
                    3 -> { ox = y; oy = w - 1 - x }
                    else -> { ox = x; oy = y }
                }
                out[oy * outW + ox] = (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }

        val nThreads = max(2, min(4, Runtime.getRuntime().availableProcessors()))
        val workers = (0 until nThreads).map { t ->
            Thread { var y = t; while (y < h) { row(y); y += nThreads } }.also { it.start() }
        }
        workers.forEach { it.join() }

        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, outW, 0, 0, outW, outH)
        return bmp
    }
}
