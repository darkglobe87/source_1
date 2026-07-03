package com.example.ui.render3d

/** Thread-safe bridge for the menu's 3D scene, same pattern as TileSceneState. */
class MenuSceneState {
    @Volatile var elapsedSec: Float = 0f
    @Volatile var playPressProgress: Float = 0f
    @Volatile var storePressProgress: Float = 0f
}
