# Bad Plots — Update Changelog

## Improve: stippled texture for 4 poster icons that read as bare shapes
Four of the 32 film-specific icons stood in a concept with a single flat shape (Home Alone's paint cans as one red circle, Spider-Man's spider as a dot at dead-center, Interstellar's bookshelf as one bare rectangle, Fight Club's soap as a flat tan card) - not enough visual information to read as the actual object.
- Added `poissonDiscSample` (Bridson's algorithm - the actual blue-noise point distribution real stippling/pointillism art uses) and `drawStippled` (fills any `Path` with hundreds of those points via `android.graphics.Region`/`asAndroidPath` for correct containment against curves and rects alike, plus a crisp outline stroke so the silhouette stays readable and a light-direction bias for cheap pseudo-shading).
- Rewired Home Alone (real steps + two stippled paint cans with rim arcs), Spider-Man (re-centered web + an actual stippled spider body with leg strokes), Interstellar (six individually-stippled book spines at varied heights instead of one rectangle), and Fight Club (soap gets grain texture instead of being a flat card).
- Reviewed and integrated by hand rather than dropped in wholesale - verified `asAndroidPath()`/`Region`/`drawArc` are real Compose/Android APIs and the Poisson-disc implementation is a correct, standard one before touching the repo.
- **Not pursued**: a companion proposal to batch-generate real AI illustrations (Gemini image model) for all 36 movies and bundle them as static assets. Not applied - it depends on an unverified model/API, costs real money per-run, and reintroduces the same copyright risk (recognizable character likeness) that the hand-designed/procedural approach was specifically chosen to avoid. Left as an option for you to pursue directly if you want it, not something to wire into the build.

## Fix: procedural posters now actually represent the film, not just its genre
Feedback: the generated posters were meant to represent the actual film, and generic hash-seeded shapes (or even genre-mood colors) don't do that - two sci-fi movies looking identical isn't "representing" either one specifically.
- Added `drawSpecificIcon` to `ProceduralMoviePoster`: a hand-designed icon for each of the 32 movies without a specific drawable, built directly from that film's own `plotHint`/`sceneHint`/`characterHint` in `movies.json` - e.g. crossed lightsabers + a Death Star for Star Wars, a spinning top for Inception, a lightning bolt for Back to the Future, an amber gem with a mosquito for Jurassic Park, a ship bow + iceberg for Titanic. All 32 titles checked against the exact strings in `movies.json`.
- Kept the genre/mood-based palette (dark red for horror, cool cyan/purple for sci-fi, etc.) as the background behind each specific icon, and as the fallback for any future/AI-generated movie that doesn't match one of the 32 hardcoded titles - so nothing regresses for content this doesn't cover yet.
- The fully-generic random-shape fallback (below) now only fires for a title AND mood match failure simultaneously, which shouldn't happen for any of the current 36 movies.

## Fix: procedural movie posters clustering into one corner
`ProceduralMoviePoster`'s "scattered circles" template indexed its 9-float random pool with `f[(i * 3) % f.size]` for the Y position - since `gcd(3, 9) = 3`, that expression only ever produces 3 distinct values (0, 3, 6) no matter how many circles are drawn, so every circle's Y collapsed onto the same 3 repeated values instead of spreading across the canvas. Confirmed via screenshots: circles bunched into the top-left corner on every affected movie, leaving most of the poster empty.
- Replaced the whole `f[expr % f.size]`-style indexing scheme with `FloatCursor`, which hands out each pool value exactly once per draw (sequential, never re-indexed) - fixes the same bug class across all 5 templates, not just the one that happened to be visibly broken.
- Pool size bumped from 9 to 32 (comfortably above the ~24 values the hungriest template needs), computed once per movie (`remember`), not regenerated per draw.

## New: standard mobile-game conventions pass (haptics, sfx, streaks, settings, share, rating)
- **Haptics** (`HapticsManager`, new): short vibration feedback on correct/wrong guesses, win/loss, and button taps. Toggle persisted via SharedPreferences, same pattern as `MusicManager`. Added the `VIBRATE` permission to the manifest.
- **Sound effects** (`SfxManager`, new): there were no SFX at all before this, only a music mute toggle. Since there are no audio asset files to load, each effect (correct/wrong/win/lose/tap) is a short procedurally-synthesized PCM tone played via `AudioTrack` - no new asset dependency.
- **Streak tracking**: `UserState` gains `currentStreak`, `bestStreak`, `totalMoviesGuessed` (Room DB version 2 → 3; the project already uses `fallbackToDestructiveMigration`, so this is a straightforward rebuild, not a manual migration). `AppRepository.recordWin()`/`recordLoss()` update these; shown on the main menu and in Settings.
- **Share result**: a "Share Result" button on the win banner ("I guessed *Title* with N lives to spare!") via a standard `ACTION_SEND` intent.
- **Settings screen** (new): toggles for music/SFX/haptics, a stats card (streak/best streak/movies guessed), and a "Rate Bad Plots" button (`market://details`, falling back to the Play Store web URL). Reached via a new gear icon on the main menu.
- Button taps (hints, PLAY, STORE, Settings) now play the tap sfx/haptic alongside their existing action.
- **Verification note**: this batch is logic/wiring rather than visual rendering (state flows, standard `Vibrator`/`AudioTrack`/`Intent` APIs, a Room field addition) - a fundamentally lower-risk category to write without being able to run the app than the reverted 3D work above. That said, the *feel* of the generated tones and vibration patterns, and the exact on-device behavior of the Room version bump, still haven't been checked on a real device.

## New: every movie now shows artwork
Previously only 4 movies had artwork wired up (`imageRes` in `movies.json`), and none of the 4 image files actually existed anywhere in the project - `IMAGE_SETUP.txt` described where to add them, but they were never added.
- Added hand-made abstract vector art for the 4 named movies (`img_shrek.xml`, `img_lord_of_the_rings.xml`, `img_pulp_fiction.xml`, `img_jaws.xml`, in `res/drawable/`) - symbolic/geometric motifs (layered rings, a ring + mountains, a glowing briefcase, a fin through waves), not depictions of actual characters or recreations of real poster art, to stay clear of copyright.
- Added `ProceduralMoviePoster` (new, `com.example.ui.screens`): a deterministic, title-seeded abstract "poster" (Compose `Canvas`, one of 5 composition templates, colors drawn from the app's existing neon palette) for the other 32 movies that don't have a specific drawable. Same movie always produces the same look.
- `GameScreen`'s movie-art block now always shows something - the specific drawable if one resolves, the generated poster otherwise - instead of showing nothing for movies without art.
- `IMAGE_SETUP.txt` updated to reflect this; still explains how to drop in real licensed art later to override any of the 4 named files.

## Reverted: real 3D letter tiles and 3D main menu
Both the hand-written OpenGL ES letter tiles (`com.example.ui.render3d`, `LetterTile3DGrid`) and the 3D title/PLAY/STORE menu (`Menu3DScene`) were tried, built to compile cleanly, but looked bad in practice once actually seen on a device. Reverted to the original `LetterBox.kt` (FlowRow-of-2D-boxes with the `graphicsLayer` flip) and the original `MainMenuScreen` title/buttons.
- Lesson: this whole custom-GLES detour was written and iterated on without ever being able to see it render - CI only proves it compiles, not that it looks right. Visual/UX work in this project needs an actual look before committing further effort to a given technique, not just a green build.
- Kept: the glyph-text-color and tile-contrast fixes are moot now (reverted along with the tiles), but the *reason* they were needed - low contrast against the busier cinema backdrop - is still worth keeping in mind for whatever replaces this.
- Also kept: the win confetti, the shader background (fog/rays/grain/vignette/snow), and its danger/pulse reactivity - none of that was part of the complaint.

## New: win confetti
`ConfettiBurst` (new, `com.example.ui.screens`): a full-screen burst of ~140 tumbling, colored pieces that fires once when a level is won (not on loss), then clears itself after a few seconds.

## New: radioactive snow in the shader background
`ShaderCinematicBackground` (AGSL path, API 33+) had no particle layer at all, unlike the legacy fallback which kept the original dust - this is why the background read as "less intricate" after the previous change. Added three parallax layers of tiny procedural, twinkling motes (`snowLayer` in the shader) tinted radioactive green, matching the look of the original `ParticleBackground` dust.

## New: Living Cinema background (AGSL shader)
`ParticleBackground` (simple animated dust) is replaced by `LivingBackground` (`CinematicBackground.kt`, new), a full-screen animated backdrop with real depth: drifting volumetric fog (layered FBM noise), sweeping projector-style light rays, film grain, scanlines, and a vignette.
- Runs as a GPU `RuntimeShader` (AGSL) on API 33+ (`ShaderCinematicBackground`).
- Falls back to the original particle dust plus a lightweight vignette/pulse overlay on API 24-32, where AGSL isn't available (`LegacyReactiveBackground`).
- Reactive to game state on `GameScreen`: the palette shifts toward red as lives run low (`dangerLevel`, ramps quadratically so it stays subtle until things get close), and a brief color flash plays on win (green) or loss (red) (`pulse`/`pulseColor`).
- `MainMenuScreen` uses the same background at its calm defaults (no danger/pulse).
- `ParticleBackground` itself is unchanged and still used internally by the legacy fallback path.

## New: Animated Main Menu
- `MainMenuScreen.kt` is now the app's start destination (was: straight into the game).
- Animated logo entrance (scale + fade), a slow pulsing ambient glow behind the title, staggered button entrance.
- Play / Store / How to Play all accessible from the menu, plus a coin display.
- Game screen now has a Home icon (top-left) to return to the menu; Android back button already worked via the nav stack.

## Background animation — optimized (was "intrusive")
`ParticleBackground` was rendering 200 particles every frame, up to 15dp radius, up to full opacity, in a fast warp-speed pattern behind all text/gameplay.
- Particle count: 200 → 55 (~73% fewer draw calls/frame)
- Max radius: 15dp → 6dp
- Max opacity: 1.0 → 0.4
- Drift/approach speed roughly halved
- Canvas background now uses the theme's `DarkBackground` constant instead of a slightly-mismatched hardcoded hex
- Same component is reused on the new main menu, so there's one lightweight background implementation, not two

## Loading screens
- Level-load spinner now shows a "Loading next movie..." label instead of a bare spinner.
- Checked store/coin displays — they read from local Room DB and resolve near-instantly, so a dedicated loading state there would be over-engineering.

## Bugfixes
- **Silent failure on insufficient coins**: tapping a hint you can't afford did nothing with no explanation. Now shows a "Not enough coins!" snackbar (GameViewModel + GameScreen).
- **Cold-start white flash**: on light-mode devices, the native window briefly flashed white before Compose painted the dark theme. Set `android:windowBackground` to match the app's dark background.
- Hint buttons now show "FREE" instead of "0" coins when you have Lifetime Premium.
- Removed an unused icon import left over from earlier development.

## Files touched
- `app/src/main/java/com/example/audio/HapticsManager.kt`, `SfxManager.kt` — new
- `app/src/main/java/com/example/ui/screens/SettingsScreen.kt` — new
- `app/src/main/java/com/example/data/Entities.kt`, `AppDatabase.kt`, `AppRepository.kt` — streak fields + version bump
- `app/src/main/AndroidManifest.xml` — VIBRATE permission
- `app/src/main/res/drawable/img_shrek.xml`, `img_lord_of_the_rings.xml`, `img_pulp_fiction.xml`, `img_jaws.xml` — new
- `app/src/main/java/com/example/ui/screens/ProceduralMoviePoster.kt` — new
- `IMAGE_SETUP.txt` — updated
- `app/src/main/java/com/example/ui/render3d/` — added, then removed entirely (reverted)
- `app/src/main/java/com/example/ui/components/LetterBox.kt` — restored to its original form after a brief detour through render3d
- `app/src/main/java/com/example/ui/screens/CinematicBackground.kt` — new, then extended with the snow layer
- `app/src/main/java/com/example/ui/screens/ConfettiBurst.kt` — new
- `app/src/main/java/com/example/ui/screens/GameScreen.kt`
- `app/src/main/java/com/example/ui/screens/MainMenuScreen.kt` — new, title/buttons briefly replaced by Menu3DScene then reverted
- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/viewmodel/GameViewModel.kt`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/themes.xml`

## Found, not touched (pre-existing, out of scope)
`app/src/test/.../GreetingScreenshotTest.kt` and `ExampleRobolectricTest.kt` reference leftover Android-Studio-template code (a `Greeting` composable that doesn't exist anywhere in the app, and an expected app name of "My Application" instead of "Bad Plots"). These aren't wired to any real screen and were already broken before this round — flagging in case you want them cleaned up or deleted separately.

## Not verified
No network/Gradle/Android-SDK access in this environment, so changes are careful manual edits, not compiler-verified. Worth a build/run pass before shipping.
- The AGSL shader in `CinematicBackground.kt` is hand-written and has not been compiled by the GPU shader compiler - that only happens on a real API 33+ device/emulator. If it fails to compile at runtime, `RuntimeShader` throws, so test on both an API 33+ target and an API 24-32 target (or emulator) to exercise both code paths before release.
