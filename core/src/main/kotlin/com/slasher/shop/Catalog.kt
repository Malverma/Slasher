package com.slasher.shop

import com.slasher.logic.Weapon
import com.slasher.logic.Weapons

/** A background color scheme: gradient keys by depth, plus when stars fade in (from 3D Stacker). */
class SkyTheme(val id: String, val name: String, val keys: List<SkyKey>, val starsFrom: Float)

/** Sky gradient at a key "height", colors as 0xRRGGBB. Slasher reads it by depth (see [Catalog.skyHeight]). */
class SkyKey(val height: Float, val top: Int, val bottom: Int)

enum class ShopTab(val title: String) { BACKGROUNDS("BACKGROUNDS"), WEAPONS("WEAPONS") }

class ShopItem(val id: String, val tab: ShopTab, val name: String, val price: Int, val weapon: Weapon? = null)

object Catalog {
    /** The two 3D Stacker themes, unchanged (spec §10). */
    val classicSky = SkyTheme(
        "bg_classic", "CLASSIC SKY",
        listOf(
            SkyKey(0f, 0x3CCBF4, 0x00FFFF),
            SkyKey(15f, 0x4AA3E8, 0xFFEFA8),
            SkyKey(30f, 0xFF7E5F, 0xFFB8C9),
            SkyKey(50f, 0x1E2A78, 0x6A3FA0),
            SkyKey(75f, 0x050818, 0x0E1A3D),
            SkyKey(100f, 0x000000, 0x06060F),
        ),
        starsFrom = 55f,
    )

    val neonSky = SkyTheme(
        "bg_neon", "NEON NIGHTS",
        listOf(
            SkyKey(0f, 0x2B0B4F, 0xFF4FA3),
            SkyKey(15f, 0x1A0F5C, 0xFF7A59),
            SkyKey(30f, 0x120A3D, 0xB13BFF),
            SkyKey(50f, 0x0A0F33, 0x00C2FF),
            SkyKey(75f, 0x05031A, 0x3B0A57),
            SkyKey(100f, 0x000000, 0x0B0420),
        ),
        starsFrom = -20f,
    )

    val skies = listOf(classicSky, neonSky)

    /** The themes were keyed by tower height 0..100; Slasher reaches "space" around Starstone (depth 180). */
    fun skyHeight(depth: Float) = depth * (100f / 180f)

    val items: List<ShopItem> =
        skies.map { ShopItem(it.id, ShopTab.BACKGROUNDS, it.name, if (it === classicSky) 0 else 100) } +
            Weapons.all.map { ShopItem(it.id, ShopTab.WEAPONS, it.name, it.price, it) }

    fun sky(id: String) = skies.firstOrNull { it.id == id } ?: classicSky
}
