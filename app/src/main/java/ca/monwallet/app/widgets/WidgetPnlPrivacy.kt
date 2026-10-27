package ca.monwallet.app.widgets

import android.content.Context

/** Quick privacy control, independent of title prices and saved display settings. */
internal object WidgetPnlPrivacy {
    const val ACTION = "ca.monwallet.TOGGLE_WIDGET_PNL"
    private fun prefs(c: Context) = c.getSharedPreferences("widget_pnl_privacy", 0)
    fun isHidden(c: Context, id: Int) = prefs(c).getBoolean("hidden:$id", false)
    fun toggle(c: Context, id: Int) {
        prefs(c).edit().putBoolean("hidden:$id", !isHidden(c, id)).apply()
    }
    fun clear(c: Context, id: Int) { prefs(c).edit().remove("hidden:$id").apply() }
}
