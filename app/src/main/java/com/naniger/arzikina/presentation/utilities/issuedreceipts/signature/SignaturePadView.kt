package com.naniger.arzikina.presentation.utilities.issuedreceipts.signature

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.io.ByteArrayOutputStream

/**
 * Zone de dessin de la signature (doigt ou stylet). Les traits sont mémorisés comme listes de points
 * (annulation trait par trait) et lissés à l'affichage par des courbes de Bézier : aucun bitmap
 * plein écran n'est gardé en mémoire pendant le tracé. [exportPng] produit un PNG transparent que
 * `IssuerSignatureProcessor` recadre et réduit.
 *
 * Vue volontairement sans dépendance métier : elle ne connaît ni Room, ni le reçu.
 */
class SignaturePadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Appelé quand le contenu change ([hasInk] a pu basculer) : sert à activer/désactiver les boutons. */
    var onInkChanged: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val strokeWidth = STROKE_DP * density
    private val strokes = ArrayList<MutableList<Point>>()
    private var current: MutableList<Point>? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK_COLOR
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = this@SignaturePadView.strokeWidth
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK_COLOR; style = Paint.Style.FILL }
    private val path = Path()

    private class Point(val x: Float, val y: Float)

    val hasInk: Boolean get() = strokes.isNotEmpty()

    /** Annule le dernier trait. */
    fun undo() {
        if (strokes.isEmpty()) return
        strokes.removeAt(strokes.lastIndex)
        changed()
    }

    fun clear() {
        if (strokes.isEmpty()) return
        strokes.clear()
        current = null
        changed()
    }

    /**
     * Exporte le tracé en PNG transparent (`null` s'il est vide). Largeur plafonnée à [EXPORT_MAX_WIDTH]
     * px pour borner la mémoire, quelle que soit la taille de l'écran.
     */
    fun exportPng(): ByteArray? {
        if (strokes.isEmpty() || width <= 0 || height <= 0) return null
        val scale = minOf(2f, EXPORT_MAX_WIDTH.toFloat() / width)
        val bitmap = Bitmap.createBitmap((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            drawStrokes(canvas)
            val out = ByteArrayOutputStream()
            if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) out.toByteArray() else null
        } finally {
            bitmap.recycle()
        }
    }

    override fun onDraw(canvas: Canvas) {
        drawStrokes(canvas)
    }

    private fun drawStrokes(canvas: Canvas) {
        for (stroke in strokes) drawStroke(canvas, stroke)
    }

    private fun drawStroke(canvas: Canvas, points: List<Point>) {
        if (points.size == 1) {
            canvas.drawCircle(points[0].x, points[0].y, strokeWidth / 2f, dotPaint)
            return
        }
        path.rewind()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            path.quadTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
        }
        val last = points.last()
        path.lineTo(last.x, last.y)
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.coerceIn(0f, width.toFloat())
        val y = event.y.coerceIn(0f, height.toFloat())
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Le pad est dans une boîte de dialogue / un défilement : le geste lui appartient.
                parent?.requestDisallowInterceptTouchEvent(true)
                if (strokes.size >= MAX_STROKES) return true
                current = mutableListOf(Point(x, y)).also { strokes.add(it) }
            }
            MotionEvent.ACTION_MOVE -> current?.let { stroke ->
                for (i in 0 until event.historySize) {
                    stroke.add(Point(event.getHistoricalX(i).coerceIn(0f, width.toFloat()), event.getHistoricalY(i).coerceIn(0f, height.toFloat())))
                }
                stroke.add(Point(x, y))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                current = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            else -> return super.onTouchEvent(event)
        }
        changed()
        return true
    }

    private fun changed() {
        invalidate()
        onInkChanged?.invoke()
    }

    private companion object {
        const val STROKE_DP = 3f
        const val EXPORT_MAX_WIDTH = 1200
        const val MAX_STROKES = 200

        /** Bleu nuit, proche d'un stylo à bille : lisible sur papier et sur écran. */
        val INK_COLOR: Int = Color.parseColor("#0B1F4D")
    }
}
