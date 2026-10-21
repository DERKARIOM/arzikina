package com.naniger.arzikina.domain.issuedreceipt

/**
 * Traitement PUR (sans Android, testé unitairement) des pixels d'une signature importée : rend le fond
 * du papier transparent et repère la zone réellement encrée. Les pixels sont au format ARGB non
 * prémultiplié (celui de `Bitmap.getPixels` / `setPixels`).
 */
object SignaturePixels {

    /** Percentile de luminance pris comme « couleur du papier ». */
    private const val PAPER_PERCENTILE = 0.95

    /** Au-dessous de cette luminance de papier, l'image n'est pas une feuille claire : seuils par défaut. */
    private const val MIN_PAPER_LUMA = 170

    private const val DEFAULT_CUTOFF = 235
    private const val PAPER_MARGIN = 20
    private const val INK_RANGE = 85
    private const val MIN_INK_LUMA = 40
    private const val MAX_CUTOFF = 245

    /** Opacité minimale pour compter un pixel comme de l'encre. */
    private const val INK_ALPHA = 32

    /** Moins de pixels d'encre que cela : image considérée vide (poussière, bruit). */
    const val MIN_INK_PIXELS = 12

    /**
     * Rend transparent le fond clair, EN PLACE. Le papier est estimé d'après l'image elle-même (une
     * feuille un peu grise ou ombrée devient quand même transparente) ; entre « papier » et « encre
     * franche », l'opacité croît progressivement, ce qui garde des bords de trait doux.
     */
    fun makeBackgroundTransparent(pixels: IntArray) {
        if (pixels.isEmpty()) return
        val (cutoff, full) = thresholds(pixels)
        val span = (cutoff - full).coerceAtLeast(1)
        for (i in pixels.indices) {
            val p = pixels[i]
            val luma = luma(p)
            val factor = when {
                luma >= cutoff -> 0
                luma <= full -> 255
                else -> 255 * (cutoff - luma) / span
            }
            val alpha = (p ushr 24) * factor / 255
            pixels[i] = (alpha shl 24) or (p and 0x00FFFFFF)
        }
    }

    /** Luminance (0..255) en entiers, pondération ITU-R BT.601. */
    internal fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }

    /** (seuil au-dessus duquel c'est du papier, seuil en dessous duquel c'est de l'encre pleine). */
    internal fun thresholds(pixels: IntArray): Pair<Int, Int> {
        val histogram = IntArray(256)
        var counted = 0
        for (p in pixels) {
            if ((p ushr 24) < 128) continue // déjà transparent : ne dit rien sur le papier
            histogram[luma(p)]++
            counted++
        }
        if (counted == 0) return DEFAULT_CUTOFF to (DEFAULT_CUTOFF - INK_RANGE)
        val target = (counted * PAPER_PERCENTILE).toInt()
        var seen = 0
        var paper = 255
        for (level in 0..255) {
            seen += histogram[level]
            if (seen > target) { paper = level; break }
        }
        if (paper < MIN_PAPER_LUMA) return DEFAULT_CUTOFF to (DEFAULT_CUTOFF - INK_RANGE)
        val cutoff = (paper - PAPER_MARGIN).coerceIn(MIN_PAPER_LUMA - PAPER_MARGIN, MAX_CUTOFF)
        val full = (cutoff - INK_RANGE).coerceAtLeast(MIN_INK_LUMA)
        return cutoff to full
    }

    /**
     * Rectangle `[gauche, haut, droite, bas[` (bornes de droite/bas exclues) qui contient toute l'encre,
     * ou `null` si l'image est vide (moins de [MIN_INK_PIXELS] pixels d'encre).
     */
    fun inkBounds(pixels: IntArray, width: Int, height: Int): IntArray? {
        require(width > 0 && height > 0 && pixels.size >= width * height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        var count = 0
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if ((pixels[row + x] ushr 24) < INK_ALPHA) continue
                count++
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (count < MIN_INK_PIXELS) return null
        return intArrayOf(left, top, right + 1, bottom + 1)
    }

    /** Agrandit [bounds] de [padding] px sans sortir de l'image. */
    fun padded(bounds: IntArray, padding: Int, width: Int, height: Int): IntArray = intArrayOf(
        (bounds[0] - padding).coerceAtLeast(0),
        (bounds[1] - padding).coerceAtLeast(0),
        (bounds[2] + padding).coerceAtMost(width),
        (bounds[3] + padding).coerceAtMost(height)
    )
}
