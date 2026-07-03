package com.example.ui.render3d

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Renders every letter tile as a real, lit 3D box: perspective-free (orthographic)
 * camera so overall scale stays consistent regardless of how many rows wrap, but
 * each tile itself is a genuine rotating 3D object with Blinn-Phong lighting, a
 * specular sweep across the face as it turns, and a soft cast shadow - this is
 * what makes it "real" 3D rather than the graphicsLayer rotationY/cameraDistance
 * trick used elsewhere in the app.
 *
 * World units are tile-relative: one tile spans 1x1 world unit, so all layout
 * math from [layoutTitleAsTiles] (which works in tile-relative world coordinates)
 * lines up directly with the geometry here.
 */
class LetterTileRenderer(
    private val state: TileSceneState,
    private val hiddenBaseColor: FloatArray,
    private val accentColor: FloatArray
) : GLSurfaceView.Renderer {

    private var program = 0
    private val mesh = TileMesh()

    private var aPosition = 0
    private var aNormal = 0
    private var aUV = 0
    private var uModel = 0
    private var uView = 0
    private var uProjection = 0
    private var uNormalMatrix = 0
    private var uTexture = 0
    private var uLightDir = 0
    private var uViewPos = 0
    private var uBaseColor = 0
    private var uRimColor = 0
    private var uIsShadow = 0
    private var uUseTexture = 0

    private var blankTextureId = -1
    private val glyphTextureCache = HashMap<Char, Int>()

    private var viewportWidth = 1
    private var viewportHeight = 1

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val normalMatrix3 = FloatArray(9)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE) // winding per-face isn't guaranteed; correctness over the minor perf cost
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        program = buildTileShaderProgram()
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        aUV = GLES20.glGetAttribLocation(program, "aUV")
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uView = GLES20.glGetUniformLocation(program, "uView")
        uProjection = GLES20.glGetUniformLocation(program, "uProjection")
        uNormalMatrix = GLES20.glGetUniformLocation(program, "uNormalMatrix")
        uTexture = GLES20.glGetUniformLocation(program, "uTexture")
        uLightDir = GLES20.glGetUniformLocation(program, "uLightDir")
        uViewPos = GLES20.glGetUniformLocation(program, "uViewPos")
        uBaseColor = GLES20.glGetUniformLocation(program, "uBaseColor")
        uRimColor = GLES20.glGetUniformLocation(program, "uRimColor")
        uIsShadow = GLES20.glGetUniformLocation(program, "uIsShadow")
        uUseTexture = GLES20.glGetUniformLocation(program, "uUseTexture")

        val blankBitmap = renderBlankSlotBitmap(128, colorFromFloats(hiddenBaseColor), colorFromFloats(accentColor))
        blankTextureId = uploadBitmapTexture(blankBitmap)
        blankBitmap.recycle()
        glyphTextureCache.clear()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val tileSizePx = state.tileSizePx.coerceAtLeast(1f)
        val halfWidthUnits = (viewportWidth / tileSizePx) / 2f
        val halfHeightUnits = (viewportHeight / tileSizePx) / 2f
        // near/far are distances *in front of the eye* along the view direction,
        // not raw world Z - the eye sits at world z=5 looking at the origin, so
        // near=1/far=10 comfortably brackets our tile geometry near world z=0.
        Matrix.orthoM(projectionMatrix, 0, -halfWidthUnits, halfWidthUnits, -halfHeightUnits, halfHeightUnits, 1f, 10f)

        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.rotateM(viewMatrix, 0, state.tiltPitchDeg, 1f, 0f, 0f)
        Matrix.rotateM(viewMatrix, 0, state.tiltRollDeg, 0f, 1f, 0f)

        val danger = state.dangerLevel.coerceIn(0f, 1f)
        val accent = state.accentColor
        val hiddenColor = mixColor(hiddenBaseColor, floatArrayOf(1f, 0.15f, 0.08f), danger * 0.5f)

        GLES20.glUniform3f(uLightDir, 0.35f, 0.6f, 0.75f)
        GLES20.glUniform3f(uViewPos, 0f, 0f, 5f)
        GLES20.glUniformMatrix4fv(uView, 1, false, viewMatrix, 0)
        GLES20.glUniformMatrix4fv(uProjection, 1, false, projectionMatrix, 0)

        for (tile in state.tiles) {
            drawTile(tile, hiddenColor, accent)
        }
    }

    private fun drawTile(tile: TileDrawInfo, hiddenColor: FloatArray, accent: FloatArray) {
        // Cast shadow: same mesh, scaled up, pushed back and down, flat translucent black.
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, tile.xWorld, tile.yWorld - 0.12f, -0.4f)
        Matrix.scaleM(modelMatrix, 0, 1.15f, 1.15f, 1f)
        drawMeshGroup(mesh.frontVertexBuffer, isShadow = true, useTexture = false, textureId = -1, baseColor = hiddenColor, rimColor = accent)
        drawMeshGroup(mesh.backVertexBuffer, isShadow = true, useTexture = false, textureId = -1, baseColor = hiddenColor, rimColor = accent)
        drawMeshGroup(mesh.sidesVertexBuffer, isShadow = true, useTexture = false, textureId = -1, baseColor = hiddenColor, rimColor = accent)

        // The tile itself - a real 3D rotation, not a 2D perspective fake.
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, tile.xWorld, tile.yWorld, 0f)
        Matrix.rotateM(modelMatrix, 0, tile.flipDeg, 0f, 1f, 0f)

        val glyphTextureId = glyphTextureCache.getOrPut(tile.char) {
            val bmp = renderGlyphBitmap(tile.char, 128, colorFromFloats(hiddenColor), colorFromFloats(accent), 0xFF0A0A10.toInt())
            val id = uploadBitmapTexture(bmp)
            bmp.recycle()
            id
        }

        drawMeshGroup(mesh.sidesVertexBuffer, isShadow = false, useTexture = false, textureId = -1, baseColor = accent, rimColor = accent)
        drawMeshGroup(mesh.frontVertexBuffer, isShadow = false, useTexture = true, textureId = blankTextureId, baseColor = hiddenColor, rimColor = accent)
        drawMeshGroup(mesh.backVertexBuffer, isShadow = false, useTexture = true, textureId = glyphTextureId, baseColor = hiddenColor, rimColor = accent)
    }

    private fun drawMeshGroup(
        buffer: FloatBuffer,
        isShadow: Boolean,
        useTexture: Boolean,
        textureId: Int,
        baseColor: FloatArray,
        rimColor: FloatArray
    ) {
        computeNormalMatrix(modelMatrix, normalMatrix3)

        GLES20.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0)
        GLES20.glUniformMatrix3fv(uNormalMatrix, 1, false, normalMatrix3, 0)
        GLES20.glUniform1f(uIsShadow, if (isShadow) 1f else 0f)
        GLES20.glUniform1f(uUseTexture, if (useTexture) 1f else 0f)
        GLES20.glUniform3f(uBaseColor, baseColor[0], baseColor[1], baseColor[2])
        GLES20.glUniform3f(uRimColor, rimColor[0], rimColor[1], rimColor[2])

        if (useTexture) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(uTexture, 0)
        }

        buffer.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, STRIDE, buffer)
        GLES20.glEnableVertexAttribArray(aPosition)
        buffer.position(3)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, STRIDE, buffer)
        GLES20.glEnableVertexAttribArray(aNormal)
        buffer.position(6)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, STRIDE, buffer)
        GLES20.glEnableVertexAttribArray(aUV)

        val vertexCount = buffer.capacity() / FLOATS_PER_VERTEX
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aNormal)
        GLES20.glDisableVertexAttribArray(aUV)
    }

    companion object {
        private const val FLOATS_PER_VERTEX = 8
        private const val STRIDE = FLOATS_PER_VERTEX * 4

        private fun colorFromFloats(c: FloatArray): Int {
            val r = (c[0].coerceIn(0f, 1f) * 255).toInt()
            val g = (c[1].coerceIn(0f, 1f) * 255).toInt()
            val b = (c[2].coerceIn(0f, 1f) * 255).toInt()
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        private fun mixColor(a: FloatArray, b: FloatArray, t: Float): FloatArray = floatArrayOf(
            a[0] + (b[0] - a[0]) * t,
            a[1] + (b[1] - a[1]) * t,
            a[2] + (b[2] - a[2]) * t
        )

        /** Valid for rotation+translation-only model matrices (uniform scale baked into the shared mesh). */
        private fun computeNormalMatrix(model: FloatArray, out3: FloatArray) {
            out3[0] = model[0]; out3[1] = model[1]; out3[2] = model[2]
            out3[3] = model[4]; out3[4] = model[5]; out3[5] = model[6]
            out3[6] = model[8]; out3[7] = model[9]; out3[8] = model[10]
        }
    }
}
