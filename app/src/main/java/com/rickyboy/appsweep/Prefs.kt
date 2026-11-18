package com.rickyboy.appsweep

import android.content.Context

/** App-wide switches the user can change. */
object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Also wipe the Recents screen (the app cards you'd normally swipe away) after Close All. */
    // always on: when someone cleans they want everything shut down
    @Suppress("UNUSED_PARAMETER")
    fun clearRecents(ctx: Context): Boolean = true
    fun setClearRecents(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("clear_recents", on).apply()
}
