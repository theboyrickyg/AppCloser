package com.rickyboy.appsweep

import android.content.Context

/**
 * Screen themes. "Cyber Cyan" is free; the others come with App Closer Pro.
 * Each one recolours the background, the panel edges, the title and the main button.
 */
object Themes {
    /** Parked for now: everyone gets the standard look until the cleaning is fully tested. */
    const val ENABLED = false

    data class Look(
        val id: String,
        val name: String,
        val edge: Int,          // panel edges, title, highlights
        val button: Int,        // the main Close All button (and first result box)
        val background: Int,    // circuit background picture
        val skin: Skin? = null  // illustrated themes: pictures for every part of the screen
    )

    val all = listOf(
        Look("cyan", "Cyber Cyan", 0xFF35D7FF.toInt(), 0xFF2E8BFF.toInt(), R.drawable.bg_circuit),
        Look("inferno", "Inferno", 0xFFFF7A2E.toInt(), 0xFFE8342A.toInt(), R.drawable.bg_circuit_inferno),
        Look("toxic", "Toxic Green", 0xFF7CFF4F.toInt(), 0xFF1FBF5B.toInt(), R.drawable.bg_circuit_toxic),
        Look("royal", "Royal Purple", 0xFFD6A6FF.toInt(), 0xFF8E4DFF.toInt(), R.drawable.bg_circuit_royal),
        Look("stealth", "Stealth", 0xFFE6ECF2.toInt(), 0xFF8A97A6.toInt(), R.drawable.bg_circuit_stealth),
        Look("christmas", "🎄 Christmas", 0xFFFFD27A.toInt(), 0xFF1F9E4A.toInt(), R.drawable.xmas_bg,
            Skin(
                background = R.drawable.xmas_bg,
                header = R.drawable.xmas_header,
                dialX = 0.2946f, dialY = 0.462f, dialR = 0.124f,
                basic = R.drawable.xmas_basic,
                rapid = R.drawable.xmas_rapid,
                list = R.drawable.xmas_list,
                tagFree = R.drawable.xmas_tag_green,
                tagApps = R.drawable.xmas_tag_gold,
                close = R.drawable.xmas_close,
                tagFreeText = 0xFFFFFFFF.toInt(),
                tagAppsText = 0xFF4A2A08.toInt(),
                dialText = 0xFFFFFFFF.toInt(),
                dialLabel = 0xFFFFD27A.toInt()
            ))
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
        if (!ENABLED) { look = all[0]; return look }
        look = if (chosen == all[0] || Unlock.isUnlocked(ctx)) chosen else all[0]
        return look
    }

    fun choose(ctx: Context, l: Look) {
        prefs(ctx).edit().putString("theme", l.id).apply()
        look = l
    }

    fun isFree(l: Look) = l == all[0]
}
