package ca.monwallet.app.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.SizeF

/** Sizes supplied by the launcher, in dp. A cell count is never a pixel size. */
internal object WidgetDimensions {
    fun exactSizes(options: Bundle): List<SizeF> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<SizeF>(
                AppWidgetManager.OPTION_APPWIDGET_SIZES)
            sizes.orEmpty().filter { it.width > 0f && it.height > 0f }.distinct()
        } else emptyList()

    fun current(context: Context, options: Bundle, fallback: Pair<Int, Int>): Pair<Int, Int> {
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val exact = exactSizes(options).let { sizes ->
            if (landscape) sizes.maxByOrNull { it.width }
            else sizes.minByOrNull { it.width }
        }
        if (exact != null) return exact.width.toInt() to exact.height.toInt()

        // Older launchers report a range: the narrow width and tall height are
        // the portrait pair; the wide width and short height are landscape.
        val width = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
            else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
        val height = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
        return options.getInt(width, fallback.first).takeIf { it > 0 }?.let { w ->
            w to (options.getInt(height, fallback.second).takeIf { it > 0 } ?: fallback.second)
        } ?: fallback
    }
}
