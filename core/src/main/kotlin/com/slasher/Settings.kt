package com.slasher

import com.badlogic.gdx.Preferences
import com.slasher.logic.Weapons
import com.slasher.shop.Catalog
import com.slasher.shop.ShopItem
import com.slasher.shop.ShopTab

/** Persistent settings, records, coins and shop items (spec §8, §9, §10). */
class Settings(private val prefs: Preferences) {
    private fun bool(key: String, default: Boolean) = prefs.getBoolean(key, default)
    private fun setBool(key: String, v: Boolean) = prefs.putBoolean(key, v).flush()

    var highScore: Int
        get() = prefs.getInteger("highScore", 0)
        set(v) { prefs.putInteger("highScore", v).flush() }

    var bestDepth: Int
        get() = prefs.getInteger("bestDepth", 0)
        set(v) { prefs.putInteger("bestDepth", v).flush() }

    var sound: Boolean
        get() = bool("sound", true)
        set(v) = setBool("sound", v)

    var music: Boolean
        get() = bool("music", true)
        set(v) = setBool("music", v)

    var vibration: Boolean
        get() = bool("vibration", true)
        set(v) = setBool("vibration", v)

    var trail: Boolean
        get() = bool("trail", true)
        set(v) = setBool("trail", v)

    var slowMo: Boolean
        get() = bool("slowMo", false)
        set(v) = setBool("slowMo", v)

    // ---- coins & shop -------------------------------------------------------------------

    var coins: Int
        get() = prefs.getInteger("coins", 0)
        set(v) { prefs.putInteger("coins", v).flush() }

    private var owned: Set<String>
        get() = prefs.getString("owned", "").split(',').filter { it.isNotEmpty() }.toSet()
        set(v) { prefs.putString("owned", v.joinToString(",")).flush() }

    var skyId: String
        get() = prefs.getString("sky", Catalog.classicSky.id)
        set(v) { prefs.putString("sky", v).flush() }

    var weaponId: String
        get() = prefs.getString("weapon", Weapons.knife.id)
        set(v) { prefs.putString("weapon", v).flush() }

    val weapon get() = Weapons.byId(weaponId)

    fun owns(item: ShopItem) = item.price == 0 || item.id in owned

    fun owns(id: String) = Catalog.items.firstOrNull { it.id == id }?.let { owns(it) } ?: false

    fun isEquipped(item: ShopItem) = item.id == skyId || item.id == weaponId

    /** Spends coins on [item] and equips it right away. */
    fun buy(item: ShopItem): Boolean {
        if (owns(item) || coins < item.price) return false
        coins -= item.price
        owned = owned + item.id
        equip(item)
        return true
    }

    fun equip(item: ShopItem) {
        if (!owns(item)) return
        when (item.tab) {
            ShopTab.BACKGROUNDS -> skyId = item.id
            ShopTab.WEAPONS -> weaponId = item.id
        }
    }
}
