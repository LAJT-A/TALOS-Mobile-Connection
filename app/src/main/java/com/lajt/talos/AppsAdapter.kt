package com.lajt.talos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Filas del menu "Apps de la PC": logo (o la inicial mientras llega) y nombre. */
class AppsAdapter(private val context: Context, private var items: List<String>) : BaseAdapter() {

    private val icons = HashMap<String, Bitmap>()

    fun setItems(list: List<String>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): String = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val row = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
            addView(ImageView(context), LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(TextView(context).apply {
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(dp(14), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val name = items[position]
        val image = row.getChildAt(0) as ImageView
        (row.getChildAt(1) as TextView).text = name
        val bitmap = icons[name] ?: loadIcon(name)
        if (bitmap != null) image.setImageBitmap(bitmap) else image.setImageDrawable(Letter(name))
        return row
    }

    private fun loadIcon(name: String): Bitmap? {
        val file = Link.iconFile(context, name)
        if (!file.exists()) return null
        val bitmap = try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
        if (bitmap != null) icons[name] = bitmap
        return bitmap
    }

    /** Circulo con la inicial, para las apps cuyo logo todavia no llego. */
    private class Letter(name: String) : Drawable() {
        private val letter = name.trim().take(1).uppercase()
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val palette = intArrayOf(0xFF1F6F8B.toInt(), 0xFF2E8B57.toInt(), 0xFF8B5A2B.toInt(), 0xFF6A5ACD.toInt(), 0xFFB5525C.toInt())
            color = palette[Math.floorMod(name.hashCode(), palette.size)]
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val radius = Math.min(b.width(), b.height()) / 2f
            canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), radius, fill)
            text.textSize = radius
            canvas.drawText(letter, b.exactCenterX(), b.exactCenterY() - (text.descent() + text.ascent()) / 2f, text)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
