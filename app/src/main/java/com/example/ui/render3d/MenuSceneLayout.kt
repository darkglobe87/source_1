package com.example.ui.render3d

/**
 * Single source of truth for the menu's 3D scene layout (title + PLAY + STORE),
 * shared between the composable (which sizes the AndroidView and positions the
 * invisible touch targets) and the renderer (which places the 3D objects) - kept
 * in one place so those two can't drift out of sync with each other.
 *
 * World units are DP-relative: one world unit equals [referenceDp] screen dp,
 * same convention as the letter tile grid (there, one unit = one tile).
 */
object MenuSceneLayout {
    val referenceDp = 56f

    val titleHalfW = 320f / referenceDp / 2f
    val titleHalfH = 96f / referenceDp / 2f
    val playHalfW = 220f / referenceDp / 2f
    val playHalfH = 56f / referenceDp / 2f
    val storeHalfW = 220f / referenceDp / 2f
    val storeHalfH = 48f / referenceDp / 2f

    private val gapAfterTitle = 56f / referenceDp
    private val gapBetweenButtons = 16f / referenceDp
    private val margin = 0.3f

    val titleCenterY: Float
    val playCenterY: Float
    val storeCenterY: Float
    val halfWidthUnits: Float
    val halfHeightUnits: Float

    init {
        var cursor = 0f
        cursor -= titleHalfH
        val rawTitleY = cursor
        cursor -= titleHalfH + gapAfterTitle
        cursor -= playHalfH
        val rawPlayY = cursor
        cursor -= playHalfH + gapBetweenButtons
        cursor -= storeHalfH
        val rawStoreY = cursor
        cursor -= storeHalfH

        val totalHeight = -cursor
        val shift = totalHeight / 2f

        titleCenterY = rawTitleY + shift
        playCenterY = rawPlayY + shift
        storeCenterY = rawStoreY + shift
        halfHeightUnits = totalHeight / 2f + margin
        halfWidthUnits = maxOf(titleHalfW, playHalfW, storeHalfW) + margin
    }

    val totalWidthDp: Float get() = 2f * halfWidthUnits * referenceDp
    val totalHeightDp: Float get() = 2f * halfHeightUnits * referenceDp
}
