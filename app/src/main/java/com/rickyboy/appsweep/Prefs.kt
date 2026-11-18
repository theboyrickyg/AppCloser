package com.rickyboy.appsweep

import android.content.Context

/** App-wide switches the user can change. */
object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Also wipe the Recents screen (the app cards you'd normally swipe away) after Close All. */
    fun clearRecents(ctx: Context): Boolean = p(ctx).getBoolean("clear_recents", true)
    fun setClearRecents(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("clear_recents", on).apply()
}
