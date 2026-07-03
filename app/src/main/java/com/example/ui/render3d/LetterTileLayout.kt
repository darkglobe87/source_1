package com.example.ui.render3d

import com.example.ui.screens.splitIntoWrapSegments

/** A single character tile, positioned in world units with its row already centered. */
data class PlacedTile(val char: Char, val xWorld: Float, val row: Int)

data class TileLayoutResult(val tiles: List<PlacedTile>, val rowCount: Int)

/**
 * Greedy word-wrap layout that mirrors the behavior of the FlowRow-based layout
 * this replaces: within a word, every character (across segment boundaries too)
 * is spaced by [letterSpacingPx]; whole words move to a new line together
 * separated by [wordGapPx], unless a single word alone is wider than
 * [maxWidthPx], in which case its own characters wrap individually as a
 * fallback instead of overflowing.
 */
fun layoutTitleAsTiles(
    title: String,
    maxWidthPx: Float,
    tileSizePx: Float,
    letterSpacingPx: Float,
    wordGapPx: Float
): TileLayoutResult {
    data class RowChar(val char: Char, val xLeft: Float)

    val rows = mutableListOf<MutableList<RowChar>>()
    var currentRow = mutableListOf<RowChar>()
    var cursorX = 0f
    var rowHasContent = false

    fun newRow() {
        if (currentRow.isNotEmpty()) rows.add(currentRow)
        currentRow = mutableListOf()
        cursorX = 0f
        rowHasContent = false
    }

    fun placeChar(c: Char) {
        currentRow.add(RowChar(c, cursorX))
        cursorX += tileSizePx
        rowHasContent = true
    }

    for (word in title.split(" ")) {
        if (word.isEmpty()) continue
        val segments = splitIntoWrapSegments(word)
        val totalChars = segments.sumOf { it.length }
        val wordWidth = totalChars * tileSizePx + (totalChars - 1).coerceAtLeast(0) * letterSpacingPx

        val gap = if (rowHasContent) wordGapPx else 0f
        if (rowHasContent && cursorX + gap + wordWidth > maxWidthPx) {
            newRow()
        } else {
            cursorX += gap
        }

        if (wordWidth > maxWidthPx) {
            // Rare fallback: even a fresh row can't fit this whole word - wrap
            // its own characters individually instead of overflowing.
            var firstInWord = true
            for (c in word) {
                val charGap = if (!firstInWord) letterSpacingPx else 0f
                if (rowHasContent && cursorX + charGap + tileSizePx > maxWidthPx) {
                    newRow()
                } else {
                    cursorX += charGap
                }
                placeChar(c)
                firstInWord = false
            }
        } else {
            var firstChar = true
            for (c in word) {
                if (!firstChar) cursorX += letterSpacingPx
                placeChar(c)
                firstChar = false
            }
        }
    }
    if (currentRow.isNotEmpty()) rows.add(currentRow)

    val placed = mutableListOf<PlacedTile>()
    rows.forEachIndexed { rowIndex, rowChars ->
        if (rowChars.isEmpty()) return@forEachIndexed
        val rowWidth = rowChars.last().xLeft + tileSizePx
        val centerOffset = -rowWidth / 2f
        for (rc in rowChars) {
            placed.add(PlacedTile(rc.char, rc.xLeft + centerOffset + tileSizePx / 2f, rowIndex))
        }
    }
    return TileLayoutResult(placed, rows.size)
}
