package com.slasher.logic

/** How a weapon crosses the screen (spec §7). */
enum class Motion { THROWN, SWUNG }

enum class Trait(val label: String, val description: String) {
    NONE("", "A trusty starter blade"),
    QUICK("QUICK", "Fast swipes hit much harder"),
    CLEAVE("CLEAVE", "Strong on flat cuts and cracks"),
    PRECISION("PRECISION", "Huge grain bonus, slow-mo perfects"),
    MOMENTUM("MOMENTUM", "Each clean cut in a row adds power"),
}

class Weapon(
    val id: String,
    val name: String,
    val price: Int,
    val sharpness: Float,
    val maxEdge: Float,
    val motion: Motion,
    val trait: Trait,
    /** Swipe and weapon trail color, 0xRRGGBB. */
    val trailRgb: Int,
)

object Weapons {
    val knife = Weapon("w_knife", "KITCHEN KNIFE", 0, 10f, 60f, Motion.THROWN, Trait.NONE, 0xE6ECF5)
    val gladius = Weapon("w_gladius", "GLADIUS", 400, 18f, 80f, Motion.THROWN, Trait.QUICK, 0xE0A458)
    val axe = Weapon("w_axe", "BATTLE AXE", 1200, 24f, 120f, Motion.SWUNG, Trait.CLEAVE, 0xFF8C3A)
    val katana = Weapon("w_katana", "KATANA", 3000, 38f, 100f, Motion.SWUNG, Trait.PRECISION, 0xFFFFFF)
    val claymore = Weapon("w_claymore", "CLAYMORE", 7500, 55f, 160f, Motion.SWUNG, Trait.MOMENTUM, 0x6FB7FF)

    val all = listOf(knife, gladius, axe, katana, claymore)

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: knife

    /** The next stronger weapon, if any. */
    fun after(w: Weapon) = all.getOrNull(all.indexOf(w) + 1)
}
