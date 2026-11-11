# Slasher

An oddly satisfying slicing game for Android, built with libGDX and Kotlin. See `spec.md` for the game design and `Slasher.mp4` for the target feel. The UI and project layout follow [3D-Stacker](https://github.com/Malverma/3D-Stacker).

## Layout

- `core/`: all game code. `core/.../logic` has no rendering code and is unit tested: convex slicing, materials, weapons, cut rules, slice physics and the run.
- `core/.../render`: the tower, slices, weapons and effects (`World.kt`), the shader wrapper, meshes and the 3D-Stacker sky.
- `android/`: Android launcher and manifest.
- `lwjgl3/`: desktop launcher, for quick testing on a PC.
- `assets/`: shaders, font, UI images and sounds shared by both launchers.
- `design/`: `make_sfx.py` regenerates all sounds and the music loop (standard-library Python). The SVGs are the icon and UI sources.

## Build & run

The project needs JDK 17 (pinned in `.mise.toml`) and the Android SDK with platform 36 (path in `local.properties`).

```sh
./gradlew :core:test                 # logic unit tests
./gradlew :lwjgl3:run                # play on desktop (drag the mouse to slice, Esc pauses)
./gradlew :android:installDebug      # install on a connected phone/emulator
./gradlew :android:assembleRelease   # minified release APK (unsigned)
```

Desktop test hooks (environment variables): `SLASHER_AUTOPLAY=1` turns on the bot, `SLASHER_IMMORTAL=1` turns off Edge loss, `SLASHER_DEPTH=<n>` starts deeper, `SLASHER_WEAPON=<id>` (`w_knife`, `w_gladius`, `w_axe`, `w_katana`, `w_claymore`) picks a weapon, `SLASHER_COINS=<n>` sets the coin balance, `SLASHER_GEMS=1` hides a gem in every new tower part, `SLASHER_ZIGZAG=1` drags a fake finger back and forth to test continuous cutting, and `SLASHER_START=menu|game|paused|settings|shop_backgrounds|shop_weapons` picks the start screen. To save screenshots, set `SLASHER_SHOTS=/tmp/shot` and `SLASHER_SHOT_TIMES=2,5`.
