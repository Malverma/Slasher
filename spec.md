# Slasher — Game Specification

## 1. Overview

**Slasher** is an oddly satisfying, endless slicing game for Android. A tall, soft-edged tower stands in the middle of the screen. You swipe across it to throw or swing your weapon. The blade cuts clean through, and the cut-off slice slides down the angled cut face, tips over the edge and tumbles away. Then the next part of the tower drops down and **reconnects** with a soft *thunk*, ready for the next cut.

The deeper you cut, the more the tower changes, slowly and smoothly: soft soap becomes wood, then stone, then steel, and so on. Each material is **harder** than the last, so a cheap weapon starts to bounce off. Every material has **grains and weak spots** that cut more easily if you aim for them. Cutting earns points, and points become **coins** to spend in the **Shop** on better weapons and backgrounds.

The UI, menus, shop and code layout follow [3D-Stacker](https://github.com/Malverma/3D-Stacker): a Play button, Settings and Shop on the main menu, the same pill buttons, coin balance, fonts and panels.

### Reference video (`Slasher.mp4`)

The 5-second clip sets the look and feel:
- A single **square prism with rounded edges** (like a thick eraser or bar of soap), seen from slightly below at a three-quarter angle so two side faces and the top show.
- **Soft, matte, glossy-edged** material in purple, with a thin bright **rim light** along the vertical edges.
- A plain **radial glow background**: bright magenta behind the tower, fading to deep purple at the edges.
- A **razor blade spins in** from off-screen along a straight path, passes through the tower at an angle and flies out the other side. There is no impact pause. The blade goes through as if the tower were butter.
- The cut leaves a **perfectly flat, slanted cut face**. The piece above it **slides down the slope** under gravity, slowly at first and then faster, **overhangs the edge, tips and falls** out of frame, turning as it goes.
- The remaining top is clean again, and the loop repeats with a cut at a new angle.

That slide → overhang → tip → tumble motion is the core of the game. Everything else is built around it.

---

## 2. Core Concepts

| Term | Definition |
|---|---|
| **Tower** | The vertical rounded square prism being cut. Its cross-section is a 1×1 unit square with rounded corners. It reaches below the bottom of the screen. |
| **Segment** | One unit of tower height, the smallest "chunk" of material. Each segment has a material, hardness, grain direction and weak spots. Segments fit together without visible seams. |
| **Depth** | How much tower has been cut away this run, in units (segments). It drives material, hardness and background. |
| **Cut / Slice** | One swipe. The swipe defines a **cut plane**. Everything above the plane is the **slice**. |
| **Weapon** | What you cut with. It has **Sharpness** (cutting power), **Edge** (durability) and a special trait. |
| **Hardness** | How much cutting power a segment needs. It rises smoothly with depth. |
| **Grain** | Visible lines running through a material. Cutting **along** the grain is easier. |
| **Weak spot** | A glowing crack or vein on the tower's surface. A cut plane that passes through one is much easier. |
| **Edge** | Your weapon's durability for the current run. When it hits 0, the blade breaks and the run ends. |

---

## 3. Gameplay Loop

1. The run starts with the tower at depth 0 (softest material), with the equipped weapon at full Edge.
2. The player **swipes** across the tower (§5). The swipe sets the cut plane's height and angle.
3. The weapon flies or swings along the swipe path (§7).
4. The game compares the weapon's **cutting power** with the **hardness** where the plane crosses the tower (§6):
   - **Clean cut:** the blade passes through and the slice separates.
   - **Stuck:** the blade bites partway in, sticks for a moment, then pops out and leaves a **crack** (a new weak spot).
   - **Bounce:** the blade bounces off with sparks and a *clank*, and loses a large chunk of Edge.
5. A separated slice **slides, tips and tumbles away** (§4). Points are scored when it leaves the tower (§8).
6. The next part of the tower **drops down and reconnects** with the cut face (§4.3). Depth increases by the height that was removed.
7. Material, hardness, appearance and background move a little further along their curves.
8. Repeat until Edge reaches 0: **"BLADE BROKEN"**, then Game Over (§9).

There is no timer. The game is meant to be relaxing. The only pressure is your weapon's Edge, which pushes you toward accurate, grain-aware cuts and better weapons.

---

## 4. Slicing & Physics

The physics is the main thing that has to feel right. Exact stacking is not needed, but the slide, tip and tumble must be smooth, heavy and predictable.

### 4.1 The cut plane

- The swipe gives a 2D line on screen. The **cut plane** contains that line, placed on the tower's visible front faces, so the cut lands where the player drew it. The plane is then tipped **22° toward the camera** around that line. So even a flat swipe makes a cut that slopes toward the viewer, and the slice slides out toward the camera and to the side, as in the video. It also keeps the weapon's flat side visible.
- **Angle limits:** the swipe's angle on screen is clamped to **5°–60°**. Steeper swipes are flattened to 60°. A swipe that is close to vertical (more than 75°) is ignored and draws a faded trail. This stops the player from cutting the tower lengthwise.
- **Height limits:** the plane must cross the tower somewhere **between the top and 1.5 units below it**. Swipes outside that band just whoosh past and cost nothing.
- **The swipe must cross the whole tower silhouette** (enter on one side, leave on the other). A short swipe that stops inside the tower counts as a *Stuck* result at best.

### 4.2 The slice in motion

When a cut is clean, the part above the plane becomes a separate rigid body. Its motion has three phases:

1. **Slide.** The slice moves downhill along the cut plane, accelerating at `g · (sin θ − μ · cos θ)` (at least 1.2 units/s²), where θ is the plane's tilt and μ is the material's friction (≈ 0.15 soft, ≈ 0.05 polished). It starts with a push of 0.9 units/s from the blade, so even a gentle cut slides off briskly. Steep cuts shoot off.
2. **Tip.** Once the slice's centre of mass passes the edge of the cut face, it pivots over that edge. It rotates around the edge line, speeding up as it goes.
3. **Tumble.** After about 25° of tipping, it comes free: it falls under full gravity, keeps its spin and gets a small random extra spin. It leaves the frame within about 1.2 s, then is removed.

Guidelines:
- `g` is tuned for weight and pace, not realism: **12 units/s²**, with the tower 1 unit wide. A typical slide lasts about 0.25 s from cut to tipping, so play keeps moving.
- During the slide, the slice and the cut face stay **in perfect contact**, without jitter or gaps. This sliding contact is the most satisfying moment.
- A **very thin slice** (under 0.08 units at its thickest) flutters off like a shaving, with lighter gravity and more spin. It gets the *Shaving* bonus (§8).
- Physics is custom and deterministic (no Bullet). The tower never moves. Slices only interact with the cut face they came off, never with each other or with the tower after tipping.

### 4.3 Reconnect

As soon as the slice starts tipping (start of phase 2):
- The tower's remaining top is the slanted cut face. The **next part of the tower drops down from above the screen**. Its bottom matches the slanted cut exactly, so it fits flush. Its top is a fresh flat rounded top like the start.
- It falls fast (about 0.2 s), lands with a quick **squash and stretch** (6% squash over 0.12 s), a *thunk*, a small puff of dust in the material's colour, and a very light camera bump.
- The seam disappears within about 0.3 s and the tower looks like one piece again.
- The new top is **one segment** high above the lowest point of the cut. So the tower always comes back to the same visible height, and the camera never has to move.
- The next blade may set off as soon as the slice starts tipping, while the new part is still dropping. If the blade arrives first, the reconnect finishes instantly. Swipes made before that are queued (§5), so cuts come about every half second.

### 4.4 Data needed for a cut

- The tower part above the reconnect line is a **convex polyhedron** (box with bevelled vertical edges). Slicing is a convex clip against a plane: clip each face polygon and close the hole with the cap polygon. That gives the slice mesh, the remaining stub and the **volume removed** (used for scoring).
- Rounded edges are a bevel in the mesh plus smoothed normals. The video's look does not need true curved geometry.

---

## 5. Controls (Touch)

| Gesture | Action |
|---|---|
| **Swipe across the tower** | Cut along that line (§4.1) |
| **Swipe speed** | Faster swipes hit a little harder (§6) |
| **Pause button** (top corner) | Pause the game |

Guidelines:
- A fading **swipe trail** follows the finger, tinted with the weapon's colour.
- Swiping is **continuous**: every time the finger crosses the tower, a cut fires and the next stroke starts right there. Swiping right, left, right without lifting makes three cuts. Turning back before crossing also starts a new stroke at the turning point. Lifting the finger cuts along any stroke that has not fired yet.
- Cuts run one at a time, but swipes made during a cut are **queued** (up to 3) and run back to back, so fast players never wait.
- **With the grain**, the finger trail turns gold while the swipe lines up with the grain. This hint teaches the system without text.
- Desktop: drag the mouse to swipe. Esc pauses.

---

## 6. Hardness, Grain & Weak Spots

### 6.1 The cut check

```
power = weapon.sharpness × speedFactor × grainFactor × weakFactor × weapon.traitFactor
```

| Factor | Range | Notes |
|---|---|---|
| `speedFactor` | 0.85 – 1.15 | Linear in swipe speed from slow (~600 px/s) to fast (~3000 px/s) |
| `grainFactor` | 1.0 – 1.8 | 1.8 when the cut is within 5° of the grain angle, dropping to 1.0 at 25° or more |
| `weakFactor` | 1.0 – 2.0 | 2.0 if the plane passes through a weak spot's centre, falling to 1.0 at its glow radius. Several weak spots do not stack. The highest counts. |
| `traitFactor` | varies | Per weapon (§7) |

Compare `power` with the **hardness** of the hardest segment that the plane crosses:

| Result | Condition | What happens |
|---|---|---|
| **Clean cut** | `power ≥ hardness` | Slice separates (§4). Normal Edge wear. |
| **Stuck** | `0.6 × hardness ≤ power < hardness` | Blade bites in to about `power / hardness` of the width, sticks for 0.25 s with a wobble, then pops out. It leaves a **crack**, which is a new weak spot along that line (§6.3). Medium Edge wear. |
| **Bounce** | `power < 0.6 × hardness` | Blade glances off with sparks, a *clank* and a small screen shake. Heavy Edge wear. |

### 6.2 Edge wear

| Result | Edge lost |
|---|---|
| Clean cut | `max(0.3, 1.5 × hardness / sharpness)`, **halved** with the grain or through a weak spot |
| Stuck | `3 × hardness / sharpness` |
| Bounce | `8 + 3 × hardness / sharpness` |
| Shaving (§4.2) | No wear |

Edge never comes back during a run, except through the revive (§9.4). Good cuts make it last longer.

### 6.3 Grain

- Every material has a **grain angle**. It is drawn as crisp **parallel grain lines** that lie exactly in the "with the grain" cut planes, with a little wobble that never changes their direction. A swipe that follows the lines is a with-the-grain cut. Finer lines between them add detail.
- Separately, each material has **mottling** (pores, speckles, clouds, plus a slight surface bump). This texture does not follow the grain, so it never misleads.
- Each new reconnected part **rotates the grain angle** by a random step of ±10°–35°, so the player has to keep reading the tower.
- Grain lines stay readable at every depth. The contrast is strongest on wood and a little softer on metals and crystal.

### 6.4 Weak spots

- Each reconnected part has **0–2 weak spots**: small glowing cracks, knots, bubbles or veins on the visible faces. They are drawn as an emissive decal with a slow pulse (1.5 s).
- Weak spots are more common early (2 per part) and rarer later (often 0 past depth 60).
- A **Stuck** result adds a crack along the attempted line. Cutting along it again gives `weakFactor` 2.0. So a weak weapon can still get through a hard material with two accurate cuts, at the cost of Edge.

### 6.5 Material progression

Hardness and appearance change **smoothly with depth**. They blend between the key depths below, so every cut looks a little different. There is never a sudden switch.

| Depth | Material | Hardness | Look | Grain |
|---|---|---|---|---|
| 0 | **Soap** | 6 | Matte lilac, soft subsurface glow, as in the video | Faint swirls |
| 12 | **Clay** | 11 | Warm terracotta, slightly rough | Smooth layers |
| 25 | **Wood** | 17 | Pale birch to dark walnut, clear rings | Strong, straight |
| 40 | **Ice** | 24 | Translucent blue, frosted edges, trapped bubbles as weak spots | Fracture lines |
| 55 | **Stone** | 32 | Grey marble with veins | Veins |
| 75 | **Iron** | 42 | Dark, slightly rusty, matte | Forge lines |
| 95 | **Steel** | 54 | Polished chrome with strong reflections | Brushed streaks |
| 120 | **Obsidian** | 68 | Glossy black glass with purple sheen | Conchoidal ripples |
| 150 | **Diamond** | 85 | Clear and glittering, rainbow speckles | Faint facet lines |
| 180+ | **Starstone** | 85 + 0.4 per depth | Dark, shifting colours with glowing veins. Endless. | Shimmer |

- Friction μ goes down as materials get smoother (soap and clay ~0.15, metal and glass ~0.05). So deeper slices slide faster, which feels heavier and slicker.
- The rim light and the colour of the slice's cut face follow the material: cut wood shows light end-grain, stone shows a fresh pale face, metal shows bright raw metal.
- A short banner appears when a new material is reached, for example "WOOD — hardness 17".

### 6.6 Gems

Rarely (a 12% chance per new tower part), a **gem** hides in the middle of the tower, inside the cut band. It gives the nearby surface a faint, twinkling sparkle in its color, so a sharp-eyed player can aim for it. A **clean cut** passing within 0.14 units of its centre finds it. The gem pops out of the cut, spins up toward the camera and bursts with sparkles. A gem stays hidden through later cuts below it, but is lost if a slice carries it away.

| Gem | Chance | Effect |
|---|---|---|
| **Gold** | 50% | **+15 coins**, added to the balance right away |
| **Blue** | 25% | **Haste** for 8 s: every swipe counts as full speed, and blades, slices and reconnects all move 1.6× faster |
| **Red** | 25% | **Sharp** for 8 s: 1.6× weapon Sharpness (more power, less Edge wear) |

Active boosts show as shrinking colored bars under the Edge meter.

---

## 7. Weapons

Five weapons, bought in the Shop. Each has a different motion as well as different stats.

| # | Weapon | Price | Sharpness | Edge | Motion | Trait |
|---|---|---|---|---|---|---|
| 1 | **Kitchen Knife** | Free (default) | 10 | 60 | **Thrown**, spinning end over end along the swipe, like the razor in the video | — |
| 2 | **Gladius** (short sword) | 400 | 18 | 80 | **Thrown**, flat spin like a disc | **Quick:** swipe speed counts twice as much (`speedFactor` 0.7–1.3) |
| 3 | **Battle Axe** | 1,200 | 24 | 120 | **Swung** in a heavy arc from the side of the screen, with a short wind-up | **Cleave:** `traitFactor` 1.3 when the cut is below 25° tilt. A Stuck result splits the crack further, so the next cut there gets `weakFactor` 2.3. Grain bonus capped at 1.4. |
| 4 | **Katana** | 3,000 | 38 | 100 | **Swung** in one fast draw-slash. A white arc lingers for a moment. | **Precision:** grain bonus up to 2.2. A perfect cut (with the grain *and* through a weak spot) adds a 0.4 s slow-motion moment. |
| 5 | **Claymore** (greatsword) | 7,500 | 55 | 160 | **Swung** two-handed in a huge sweeping arc that fills the screen | **Momentum:** each clean cut in a row adds +5% power, up to +40%. Resets on Stuck or Bounce. |

Guidelines:
- With the Kitchen Knife, skilled players should get to about depth 30–40 (wood), using grain and weak spots. Each later weapon should push a good run about 1.5–2 materials deeper.
- Thrown weapons fly in from off-screen on the start side of the swipe and out the other side. Swung weapons sweep in from the start side, follow through and leave the same way.
- Low-poly models with flat shading and a bright specular edge on the blade. Each weapon has its own trail colour: knife silver, gladius bronze, axe orange, katana white, claymore blue.
- Each weapon has its own cut sound (§11). Heavier weapons sound deeper.
- The equipped weapon is shown slowly turning on the main menu, next to the tower.

---

## 8. Scoring & Coins

### Score

| Event | Points |
|---|---|
| Clean cut | `round(volume × hardness × 10)`. A full 1-unit slice of Soap is about 60. |
| **With the grain** | +50% of the cut's points |
| **Weak spot** | +50% of the cut's points |
| **Perfect** (both of the above) | ×2 instead of adding, with a "PERFECT" popup |
| **Shaving** (thinner than 0.08) | Flat +40, "SHAVING!" popup |
| **Streak** | Each clean cut in a row adds +10% to the next cut's points, up to +100%. Resets on Stuck or Bounce. |
| New material reached | +250 |
| Stuck / Bounce | 0 |

- Points pop up near the slice as it tumbles away, with a short count-up in the HUD.
- **High Score** and **Best Depth** are saved on the device and shown on the main menu and Game Over screen.

### Coins
- When a run ends, you earn **1 coin per 100 points** (rounded down), as in 3D-Stacker.
- The Game Over screen shows "+N COINS". The balance shows on the main menu and in the Shop.

---

## 9. Screens & UI

The look matches 3D-Stacker: white rounded pill buttons with navy text and a soft drop shadow, a yellow (`#FFD23F`) primary button, navy translucent panels and NotoSans Black.

1. **Main Menu:** "SLASHER" title (with a slow bob), coin balance top-right, High Score and Best Depth, and the **PLAY** (primary), **SETTINGS** and **SHOP** buttons. In the background, the tower stands in the equipped background, and every ~3 s a ghost knife slices it automatically (the video loop, as an attract mode).
2. **In-Game HUD:**
   - Top-left: **Edge meter**, a horizontal bar with the weapon's icon. It flashes red under 25%.
   - Top-centre: score, with a streak counter under it (e.g. "×6").
   - Top-right: pause button.
   - Side: depth meter with the current material name, e.g. "DEPTH 27 · WOOD".
3. **Pause Menu:** Resume, Restart, Settings, Quit to Menu.
4. **Game Over:** "BLADE BROKEN". The weapon snaps in half with a short animation. Shows final score, depth reached, the material it broke on, "New High Score!" if earned, "+N COINS", and Retry and Menu buttons. If the weapon was beaten by hardness, a hint suggests the next weapon: "Iron is too hard for the Gladius. Try the Battle Axe."
   - **Revive:** the first time the blade breaks in a run, a **"REPAIR BLADE?"** offer appears with **WATCH AD** (restore 40% Edge) and **NO THANKS**. Once per run. As in 3D-Stacker, no ad SDK yet. WATCH AD grants the repair right away, and the SDK call goes in `GameScreen.watchAd()`.
5. **Settings:** music volume, SFX volume, vibration on/off, swipe trail on/off, slow-motion on perfect cuts on/off.

---

## 10. Shop

The SHOP button is on the main menu, under Play and Settings. It has two tabs. Each row has a swatch or preview, a name, and a price button (or OWNED / EQUIPPED).

| Tab | Items | Price |
|---|---|---|
| **Backgrounds** | **Classic Sky** (default) and **Neon Nights**, the same two themes and colour keys as 3D-Stacker (`Catalog.classicSky` and `Catalog.neonSky`) | Free / 100 coins |
| **Weapons** | Kitchen Knife, Gladius, Battle Axe, Katana, Claymore (§7) | Free / 400 / 1,200 / 3,000 / 7,500 |

- Buying an item equips it right away. Owned items can be re-equipped at any time. One background and one weapon are active.
- Weapon rows show Sharpness and Edge as small bars, plus a one-line trait description. A locked weapon's preview is shown as a dark silhouette.
- **Background behaviour:** the 3D-Stacker themes are keyed by tower height. In Slasher, the same keys are read using **depth** instead, so the sky moves from day to sunset to night to space as you cut deeper. The **radial glow** from the video is drawn on top: a soft bright spot behind the tower in a lighter tint of the current bottom colour.

---

## 11. Audio & Feedback

The sounds are a big part of "oddly satisfying". They should be crisp, close and quiet.

| Moment | Sound | Haptic |
|---|---|---|
| Weapon flying or swinging | Soft whoosh. Thrown weapons add a spinning *whirr*. | — |
| Clean cut | A short, clean *shhk*. Pitch and texture follow the material: wet squish for soap, crunch for wood, crisp crack for ice, ring for metal. | Short light tick |
| Slide | Quiet friction hiss that rises with speed and stops when it tips | Very faint buzz |
| Tip & tumble | Soft whump as it drops out of frame | — |
| Reconnect | Deep, soft *thunk* | Light tap |
| Stuck | Dull *thock* and a wobble | Medium |
| Bounce | *Clank* with sparks | Strong, short |
| Perfect | Bright chime on top of the cut sound | Double tick |
| Blade breaks | Snap and ringing tail | Long |

- Calm ambient music, which shifts with the background theme as in 3D-Stacker.
- Temporary sounds are generated by `design/make_sfx.py`, as in 3D-Stacker.

---

## 12. Camera & Rendering

- **Camera:** fixed, at a 3/4 angle (40° yaw) and slightly above the top, looking down 16° so the top face shows, as in the video. The tower is about 42% of the screen width. Its top sits about 24% from the top of the screen (30% on the menu, below the title), so a tall column shows below it, which leaves room above for the reconnecting part to drop in.
- The tower reaches past the bottom of the screen. Only the top ~3 units are ever drawn.
- **Lighting:** one soft key light from upper left, a strong **rim light** from behind that gives the bright edge lines in the video, and soft ambient light. There are no hard shadows on the tower. The slice casts a soft shadow onto the cut face while sliding.
- **Material shader:** one shader with parameters (base colour, roughness, metalness, subsurface tint, translucency, grain texture and angle, weak-spot mask). Materials are blended by depth through these parameters. There is no texture swap per material.
- **Slow motion:** perfect cuts with the Katana always run at 30% speed for 0.4 s at the moment of the cut. Other weapons do this only if slow-mo is turned on in Settings (off by default, to keep the pace up).
- Target **60 fps**. The scene is very small: one tower, at most ~4 slices in flight and one weapon.

---

## 13. Technical Requirements

| Item | Requirement |
|---|---|
| Platform | Android 8.0 (API 26) and up |
| Orientation | Portrait, locked |
| Engine | **libGDX with Kotlin** (OpenGL ES 2.0 shaders), the same stack and project layout as 3D-Stacker (`core/`, `android/`, `lwjgl3/`, `assets/`, `design/`) |
| Physics | Custom, deterministic slide, tip and tumble (§4). No physics engine. |
| Persistence | High score, best depth, coins, owned items, equipped items and settings in libGDX `Preferences`, as in 3D-Stacker's `Settings` |
| Offline | Fully playable offline. No account. |
| APK size | Target < 20 MB |

### Code to reuse from 3D-Stacker
- `ui/Ui.kt` (`UiButton`, `ButtonInput`, `panel`, `dim`, `text`, colours), `Assets.kt` (fonts, panel, coin), `screens/MenuScreen.kt`, `screens/SettingsScreen.kt` and `screens/ShopScreen.kt` (tab layout and row layout).
- `render/Sky.kt` and the `SkyTheme` / `SkyKey` data in `shop/Catalog.kt`, driven by depth instead of height.
- `ShopTab` becomes `BACKGROUNDS, WEAPONS`. `BlockSkin` and the Heart Block are removed.

### Data model (suggested)
- `Material { name, depth, hardness, friction, baseColor, roughness, metalness, translucency, grainStrength }`. `MaterialCurve.at(depth)` returns the blended material.
- `TowerPart { mesh: ConvexPoly, depthStart, grainAngle, weakSpots: List<WeakSpot> }`. `WeakSpot { position on surface, radius, kind }`.
- `Weapon { id, name, sharpness, maxEdge, motion: THROWN | SWUNG, trait }`.
- `CutResult { kind: CLEAN | STUCK | BOUNCE, power, hardness, grainFactor, weakFactor, volume, edgeLost, points }`, produced by a pure function `resolveCut(plane, part, weapon, swipeSpeed, streak)`.
- `Slice { mesh, phase: SLIDE | TIP | TUMBLE, position, velocity, rotation, angularVelocity }`.
- `GameState { score, depth, edge, streak, weapon, parts, slices, reviveUsed }`.
- `logic/` has no rendering code and is unit tested: cut resolution, edge wear, scoring, material curve, convex clipping and volume.

### Desktop test hooks (as in 3D-Stacker)
`SLASHER_AUTOPLAY=1` (bot cuts with the grain), `SLASHER_IMMORTAL=1` (no Edge loss), `SLASHER_START=menu|game|paused|settings|shop_backgrounds|shop_weapons`, `SLASHER_DEPTH=<n>` (start at a depth), `SLASHER_WEAPON=<id>` (e.g. `w_katana`), `SLASHER_COINS=<n>`, and `SLASHER_SHOTS` / `SLASHER_SHOT_TIMES` for screenshots.

---

## 14. Out of Scope for MVP / Future Ideas

- Weapon upgrades (sharpening stones that raise Sharpness or Edge)
- More backgrounds made for Slasher (for example the purple glow from the video as its own theme)
- Weapon skins and trail effects
- Special segments: gold segments worth extra coins, and explosive or hollow segments
- Daily challenge with a fixed tower seed
- Online leaderboards
- "Zen mode" with no Edge limit and no score

---

## 15. Decisions Log

- The run ends when the weapon's **Edge** is used up, not on a timer or hearts. That keeps the game relaxing and makes hardness matter (§6.2).
- **Reconnect** means the next part of the tower drops in from above and fits the slanted cut (§4.3). It always restores the same visible height, so the camera stays fixed.
- Gems: gold, blue (haste) and red (sharp) hide in 12% of new parts (§6.6).
- Faster pace: quicker slides, the reconnect drops as soon as the slice tips, and swipes queue (§4, §5).
- The cut plane holds the swipe line on the tower's front faces and leans 22° toward the camera. The swipe angle is clamped to 5°–60° (§4.1).
- Swung weapons sweep in the cut plane from a hand in front of the tower, like a first-person slash. Thrown weapons spin in the cut plane, like the razor in the video (§7).
- Edge wear was halved from the first draft, so a skilled Kitchen Knife run reaches wood or ice (§6.2).
- **Stuck** cuts leave cracks that act as weak spots, so a weak weapon can still get through with two accurate cuts (§6.4).
- The background is the two 3D-Stacker themes, keyed by depth, with a radial glow on top (§10).
- Engine: libGDX with Kotlin, the same as 3D-Stacker (§13).

## 16. Open Questions

- **Reconnect direction:** this spec has the new tower part drop from above. Another option is a tower that reaches off the top of the screen and slides down as a whole after each cut. That would need a two-cut "wedge" mechanic so slices can be pulled out of the middle. Confirm which one you want.
- Should the run end on Edge, or would a no-fail version (only cuts and coins) suit the "oddly satisfying" goal better? That could be the default, with Edge as a "Challenge" mode.
- Weapon prices and the hardness curve are first guesses. Tune them after playtesting so the Kitchen Knife stalls at around wood or ice.
