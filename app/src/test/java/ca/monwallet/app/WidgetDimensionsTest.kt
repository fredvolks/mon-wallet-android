package ca.monwallet.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.SizeF
import androidx.test.core.app.ApplicationProvider
import ca.monwallet.app.widgets.WidgetDimensions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestMonWallet::class)
class WidgetDimensionsTest {
    @Test @Config(sdk = [35]) fun oneUiExactSizesSelectPortraitAndLandscapeIndependently() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val options = Bundle().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(SizeF(334f, 180f), SizeF(620f, 100f)))
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 100)
        }
        val original = context.resources.configuration
        try {
            val portrait = Configuration(original).apply { orientation = Configuration.ORIENTATION_PORTRAIT }
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(portrait, context.resources.displayMetrics)
            assertEquals(334 to 180, WidgetDimensions.current(context, options, 250 to 150))
            val landscape = Configuration(original).apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(landscape, context.resources.displayMetrics)
            assertEquals(620 to 100, WidgetDimensions.current(context, options, 250 to 150))
        } finally {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(original, context.resources.displayMetrics)
        }
    }

    @Test @Config(sdk = [28]) fun olderLauncherUsesPortraitHeightRatherThanLandscapeMinimum() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 334)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 620)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 100)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
        }
        val original = context.resources.configuration
        try {
            val portrait = Configuration(original).apply { orientation = Configuration.ORIENTATION_PORTRAIT }
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(portrait, context.resources.displayMetrics)
            assertEquals(334 to 180, WidgetDimensions.current(context, options, 250 to 150))
            assertEquals(616.0 / 333.0, 334.0 / 180.0, 0.01)
        } finally {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(original, context.resources.displayMetrics)
        }
    }
}
