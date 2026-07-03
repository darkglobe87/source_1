# Bad Plots — Update Changelog

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
- `app/src/main/java/com/example/ui/screens/GameScreen.kt`
- `app/src/main/java/com/example/ui/screens/MainMenuScreen.kt` — new
- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/viewmodel/GameViewModel.kt`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/themes.xml`

## Found, not touched (pre-existing, out of scope)
`app/src/test/.../GreetingScreenshotTest.kt` and `ExampleRobolectricTest.kt` reference leftover Android-Studio-template code (a `Greeting` composable that doesn't exist anywhere in the app, and an expected app name of "My Application" instead of "Bad Plots"). These aren't wired to any real screen and were already broken before this round — flagging in case you want them cleaned up or deleted separately.

## Not verified
No network/Gradle access in this environment, so changes are careful manual edits, not compiler-verified. Worth a build/run pass before shipping.
