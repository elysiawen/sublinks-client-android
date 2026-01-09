package com.github.kr328.clash.design.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import kotlin.math.max
import kotlin.math.min

class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val satPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val satValRect = RectF()
    private val hueRect = RectF()

    private var hue = 360f
    private var sat = 1f
    private var val_ = 1f

    private var onColorChanged: ((Int) -> Unit)? = null

    init {
        selectorPaint.style = Paint.Style.STROKE
        selectorPaint.strokeWidth = 4f
        selectorPaint.color = Color.WHITE
        
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = 2f
        borderPaint.color = Color.GRAY
    }

    fun setOnColorChangedListener(listener: (Int) -> Unit) {
        onColorChanged = listener
    }

    fun setColor(@ColorInt color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0]
        sat = hsv[1]
        val_ = hsv[2]
        
        updateSatValShader()
        invalidate()
    }

    fun getColor(): Int {
        return Color.HSVToColor(floatArrayOf(hue, sat, val_))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
         val w = MeasureSpec.getSize(widthMeasureSpec)
         val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) 
             MeasureSpec.getSize(heightMeasureSpec)
             else w // Square-ish default if not specified
         setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        val width = w.toFloat()
        val height = h.toFloat()
        val padding = 16f
        val hueHeight = 60f // Thicker slider
        
        val svHeight = height - hueHeight - padding * 2
        
        satValRect.set(padding, padding, width - padding, padding + svHeight)
        hueRect.set(padding, satValRect.bottom + padding, width - padding, satValRect.bottom + padding + hueHeight)
        
        updateHueShader()
        updateSatValShader()
    }

    override fun onDraw(canvas: Canvas) {
        // Draw Sat/Val Box
        canvas.drawRect(satValRect, satPaint)
        canvas.drawRect(satValRect, valPaint)
        canvas.drawRect(satValRect, borderPaint)

        // Draw Hue Slider
        canvas.drawRect(hueRect, huePaint)
        canvas.drawRect(hueRect, borderPaint)

        // Draw Sat/Val Selector
        val sx = satValRect.left + sat * satValRect.width()
        val sy = satValRect.top + (1 - val_) * satValRect.height()
        
        selectorPaint.style = Paint.Style.STROKE
        selectorPaint.strokeWidth = 4f
        selectorPaint.color = if (val_ < 0.5) Color.WHITE else Color.BLACK
        canvas.drawCircle(sx, sy, 16f, selectorPaint)

        // Draw Hue Selector
        val hx = hueRect.left + (hue / 360f) * hueRect.width()
        val hy = hueRect.centerY()
        selectorPaint.color = Color.BLACK
        canvas.drawCircle(hx, hy, 16f, selectorPaint)
    }

    private fun updateHueShader() {
        val colors = intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
        )
        huePaint.shader = LinearGradient(
            hueRect.left, hueRect.top, hueRect.right, hueRect.top,
            colors, null, Shader.TileMode.CLAMP
        )
    }

    private fun updateSatValShader() {
        val baseColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        
        satPaint.shader = LinearGradient(
            satValRect.left, satValRect.top, satValRect.right, satValRect.top,
            Color.WHITE, baseColor, Shader.TileMode.CLAMP
        )
        
        valPaint.shader = LinearGradient(
            satValRect.left, satValRect.top, satValRect.left, satValRect.bottom,
            Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
         // .. Logic provided below
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (hueRect.contains(event.x, event.y)) {
                    hue = ((event.x - hueRect.left) / hueRect.width()) * 360f
                    hue = max(0f, min(360f, hue))
                    updateSatValShader()
                    invalidate()
                    onColorChanged?.invoke(getColor())
                    return true
                } else {
                    // Default to SV box if not explicitly on Hue, 
                    // or clamp to it if we started there. 
                    // Simple logic: if Y is in hue area, handle hue. Else handle SV.
                    if (event.y >= hueRect.top - 10) { // Tolerance
                         hue = ((event.x - hueRect.left) / hueRect.width()) * 360f
                         hue = max(0f, min(360f, hue))
                         updateSatValShader()
                         invalidate()
                         onColorChanged?.invoke(getColor())
                    } else {
                        sat = (event.x - satValRect.left) / satValRect.width()
                        val_ = 1f - ((event.y - satValRect.top) / satValRect.height())
                        
                        sat = max(0f, min(1f, sat))
                        val_ = max(0f, min(1f, val_))
                        
                        invalidate()
                        onColorChanged?.invoke(getColor())
                    }
                }
            }
        }
        return true
    }
}
