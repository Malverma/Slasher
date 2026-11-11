package com.slasher.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.slasher.DebugOptions
import com.slasher.SlasherGame

/**
 * Desktop build for quick testing. Environment variables:
 *  SLASHER_AUTOPLAY=1 (bot cuts with the grain), SLASHER_IMMORTAL=1 (no Edge loss), SLASHER_DEPTH=<n>,
 *  SLASHER_WEAPON=<id, e.g. w_katana>, SLASHER_COINS=<n>, SLASHER_GEMS=1 (a gem in every new part),
 *  SLASHER_ZIGZAG=1 (a fake finger swipes back and forth without lifting),
 *  SLASHER_START=menu|game|paused|settings|shop_backgrounds|shop_weapons,
 *  SLASHER_SHOTS=<file prefix>, SLASHER_SHOT_TIMES=2,5,9
 */
fun main() {
    val env = System.getenv()
    val debug = DebugOptions(
        autoplay = env["SLASHER_AUTOPLAY"] == "1",
        immortal = env["SLASHER_IMMORTAL"] == "1",
        startDepth = env["SLASHER_DEPTH"]?.toFloatOrNull() ?: 0f,
        weaponId = env["SLASHER_WEAPON"],
        coins = env["SLASHER_COINS"]?.toIntOrNull(),
        gems = env["SLASHER_GEMS"] == "1",
        zigzag = env["SLASHER_ZIGZAG"] == "1",
        screenshotPrefix = env["SLASHER_SHOTS"],
        screenshotTimes = env["SLASHER_SHOT_TIMES"]?.split(',')?.mapNotNull { it.trim().toFloatOrNull() } ?: emptyList(),
        startScreen = env["SLASHER_START"] ?: "menu",
    )
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Slasher")
        setWindowedMode(450, 900)
        setResizable(false)
        useVsync(true)
        setForegroundFPS(60)
        setBackBufferConfig(8, 8, 8, 8, 16, 0, 4)
    }
    Lwjgl3Application(SlasherGame(debug), config)
}
