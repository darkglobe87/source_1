package com.example.ui.render3d

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private class MenuObject(
    val mesh: TileMesh,
    val halfWidth: Float,
    val halfHeight: Float,
    val centerY: Float,
    val idleAmplitudeDeg: Float,
    val idleFrequency: Float,
    val idlePhase: Float,
    val labelText: String
) {
    var textureId: Int = -1
}

/**
 * Renders the menu's title + PLAY + STORE as real lit 3D objects (reusing the
 * same box mesh/shader as the letter tiles): a gentle continuous idle rotation
 * per object, and a press-in (scale down + push back in Z) animation on the
 * two buttons driven by [MenuSceneState]. All three objects and their label
 * textures are static and built once in onSurfaceCreated - unlike the letter
 * grid, nothing here changes shape/count at runtime.
 */
class MenuSceneRenderer(
    private val state: MenuSceneState,
    private val hiddenBaseColor: FloatArray,
    private val accentColor: FloatArray
) : GLSurfaceView.Renderer {

    private var program = 0

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

    private val titleObj = MenuObject(
        mesh = TileMesh(MenuSceneLayout.titleHalfW, MenuSceneLayout.titleHalfH),
        halfWidth = MenuSceneLayout.titleHalfW, halfHeight = MenuSceneLayout.titleHalfH,
        centerY = MenuSceneLayout.titleCenterY,
        idleAmplitudeDeg = 18f, idleFrequency = 0.5f, idlePhase = 0f,
        labelText = "BAD PLOTS"
    )
    private val playObj = MenuObject(
        mesh = TileMesh(MenuSceneLayout.playHalfW, MenuSceneLayout.playHalfH),
        halfWidth = MenuSceneLayout.playHalfW, halfHeight = MenuSceneLayout.playHalfH,
        centerY = MenuSceneLayout.playCenterY,
        idleAmplitudeDeg = 6f, idleFrequency = 0.65f, idlePhase = 1.3f,
        labelText = "PLAY"
    )
    private val storeObj = MenuObject(
        mesh = TileMesh(MenuSceneLayout.storeHalfW, MenuSceneLayout.storeHalfH),
        halfWidth = MenuSceneLayout.storeHalfW, halfHeight = MenuSceneLayout.storeHalfH,
        centerY = MenuSceneLayout.storeCenterY,
        idleAmplitudeDeg = 6f, idleFrequency = 0.65f, idlePhase = 2.6f,
        labelText = "STORE"
    )

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val normalMatrix3 = FloatArray(9)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
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

        val fillColor = colorFromFloats(hiddenBaseColor)
        val rimColor = colorFromFloats(accentColor)
        for (obj in listOf(titleObj, playObj, storeObj)) {
            val widthPx = 480
            val heightPx = (widthPx * (obj.halfHeight / obj.halfWidth)).toInt().coerceAtLeast(1)
            val bmp = renderLabelBitmap(obj.labelText, widthPx, heightPx, fillColor, rimColor, rimColor)
            obj.textureId = uploadBitmapTexture(bmp)
            bmp.recycle()
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        Matrix.orthoM(
            projectionMatrix, 0,
            -MenuSceneLayout.halfWidthUnits, MenuSceneLayout.halfWidthUnits,
            -MenuSceneLayout.halfHeightUnits, MenuSceneLayout.halfHeightUnits,
            1f, 10f
        )
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 1f, 0f)

        GLES20.glUniform3f(uLightDir, 0.35f, 0.6f, 0.75f)
        GLES20.glUniform3f(uViewPos, 0f, 0f, 5f)
        GLES20.glUniformMatrix4fv(uView, 1, false, viewMatrix, 0)
        GLES20.glUniformMatrix4fv(uProjection, 1, false, projectionMatrix, 0)

        val elapsed = state.elapsedSec
        drawObject(titleObj, elapsed, pressProgress = 0f)
        drawObject(playObj, elapsed, pressProgress = state.playPressProgress.coerceIn(0f, 1f))
        drawObject(storeObj, elapsed, pressProgress = state.storePressProgress.coerceIn(0f, 1f))
    }

    private fun drawObject(obj: MenuObject, elapsedSec: Float, pressProgress: Float) {
        val angleRad = elapsedSec * obj.idleFrequency * 2f * Math.PI.toFloat() + obj.idlePhase
        val rotationY = obj.idleAmplitudeDeg * kotlin.math.sin(angleRad)
        val scale = 1f - pressProgress * 0.08f
        val zPush = -pressProgress * 0.12f

        // Cast shadow.
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0f, obj.centerY - 0.12f, -0.4f)
        Matrix.scaleM(modelMatrix, 0, 1.1f, 1.1f, 1f)
        drawMeshGroup(obj.mesh.frontVertexBuffer, isShadow = true, useTexture = false, textureId = -1, baseColor = hiddenBaseColor, rimColor = accentColor)
        drawMeshGroup(obj.mesh.backVertexBuffer, isShadow = true, useTexture = false, textureId = -1, baseColor = hiddenBaseColor, rimColor = accentColor)
        drawMeshGroup(obj.mesh.sidesVertexBuffer, isShadow = true, useTexture = false, textureId = -1, baseColor = hiddenBaseColor, rimColor = accentColor)

        // The object itself - only the front face is ever visible (oscillation
        // never approaches edge-on), so the back face is skipped entirely.
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0f, obj.centerY, zPush)
        Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, scale, scale, scale)

        drawMeshGroup(obj.mesh.sidesVertexBuffer, isShadow = false, useTexture = false, textureId = -1, baseColor = accentColor, rimColor = accentColor)
        drawMeshGroup(obj.mesh.frontVertexBuffer, isShadow = false, useTexture = true, textureId = obj.textureId, baseColor = hiddenBaseColor, rimColor = accentColor)
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

        private fun computeNormalMatrix(model: FloatArray, out3: FloatArray) {
            out3[0] = model[0]; out3[1] = model[1]; out3[2] = model[2]
            out3[3] = model[4]; out3[4] = model[5]; out3[5] = model[6]
            out3[6] = model[8]; out3[7] = model[9]; out3[8] = model[10]
        }
    }
}
