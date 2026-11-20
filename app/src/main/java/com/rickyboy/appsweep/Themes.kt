package com.rickyboy.appsweep

import android.content.Context

/**
 * Screen themes. "Cyber Cyan" is free; the others come with App Closer Pro.
 * Each one recolours the background, the panel edges, the title and the main button.
 */
object Themes {

    data class Look(
        val id: String,
        val name: String,
        val edge: Int,          // panel edges, title, highlights
        val button: Int,        // the main Close All button (and first result box)
        val background: Int     // circuit background picture
    )

    val all = listOf(
        Look("cyan", "Cyber Cyan", 0xFF35D7FF.toInt(), 0xFF2E8BFF.toInt(), R.drawable.bg_circuit),
        Look("inferno", "Inferno", 0xFFFF7A2E.toInt(), 0xFFE8342A.toInt(), R.drawable.bg_circuit_inferno),
        Look("toxic", "Toxic Green", 0xFF7CFF4F.toInt(), 0xFF1FBF5B.toInt(), R.drawable.bg_circuit_toxic),
        Look("royal", "Royal Purple", 0xFFD6A6FF.toInt(), 0xFF8E4DFF.toInt(), R.drawable.bg_circuit_royal),
        Look("stealth", "Stealth", 0xFFE6ECF2.toInt(), 0xFF8A97A6.toInt(), R.drawable.bg_circuit_stealth)
    )

    /** The look in use right now (set when the main screen opens). */
    @Volatile
    var look: Look = all[0]
        private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Loads the chosen theme; Pro themes fall back to the free one without Pro. */
    fun load(ctx: Context): Look {
        val id = prefs(ctx).getString("theme", "cyan")
        val chosen = all.firstOrNull { it.id == id } ?: all[0]
        look = if (chosen == all[0] || Unlock.isUnlocked(ctx)) chosen else all[0]
        return look
    }

    fun choose(ctx: Context, l: Look) {
        prefs(ctx).edit().putString("theme", l.id).apply()
        look = l
    }

    fun isFree(l: Look) = l == all[0]
}
