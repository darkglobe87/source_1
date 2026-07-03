package com.example.ui.render3d

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * A real, lit 3D box mesh (position + normal + uv per vertex), split into three
 * draw groups so the renderer can bind a different texture/material to each:
 * the front face (the "hidden" side, facing the camera at rest), the back face
 * (the "revealed" side, facing the camera once the tile has rotated 180deg),
 * and the four thin side faces that only become visible mid-flip - which is
 * the whole point of doing this in real 3D instead of faking it with a 2D
 * rotation trick: you actually see the tile's edge as it turns.
 */
class TileMesh(halfExtentXY: Float = 0.5f, halfDepth: Float = 0.15f) {
    val frontVertexBuffer: FloatBuffer
    val backVertexBuffer: FloatBuffer
    val sidesVertexBuffer: FloatBuffer
    val sidesIndexCount: Int

    init {
        val hw = halfExtentXY
        val hd = halfDepth

        // Interleaved layout per vertex: position(3) normal(3) uv(2) = 8 floats
        frontVertexBuffer = quad(
            floatArrayOf(-hw, -hw, hd), floatArrayOf(hw, -hw, hd),
            floatArrayOf(hw, hw, hd), floatArrayOf(-hw, hw, hd),
            normal = floatArrayOf(0f, 0f, 1f),
            uvs = arrayOf(floatArrayOf(0f, 1f), floatArrayOf(1f, 1f), floatArrayOf(1f, 0f), floatArrayOf(0f, 0f))
        )

        // Back face is wound the mirror image of the front (in -Z, facing away
        // at rest) with its U coordinate flipped so that once the box has
        // rotated 180deg about Y, the glyph reads correctly instead of mirrored.
        backVertexBuffer = quad(
            floatArrayOf(hw, -hw, -hd), floatArrayOf(-hw, -hw, -hd),
            floatArrayOf(-hw, hw, -hd), floatArrayOf(hw, hw, -hd),
            normal = floatArrayOf(0f, 0f, -1f),
            uvs = arrayOf(floatArrayOf(0f, 1f), floatArrayOf(1f, 1f), floatArrayOf(1f, 0f), floatArrayOf(0f, 0f))
        )

        val blankUvs = arrayOf(floatArrayOf(0f, 0f), floatArrayOf(0f, 0f), floatArrayOf(0f, 0f), floatArrayOf(0f, 0f))
        val sideFaces = listOf(
            // Top (+Y)
            quadFloats(
                floatArrayOf(-hw, hw, hd), floatArrayOf(hw, hw, hd),
                floatArrayOf(hw, hw, -hd), floatArrayOf(-hw, hw, -hd),
                floatArrayOf(0f, 1f, 0f), blankUvs
            ),
            // Bottom (-Y)
            quadFloats(
                floatArrayOf(-hw, -hw, -hd), floatArrayOf(hw, -hw, -hd),
                floatArrayOf(hw, -hw, hd), floatArrayOf(-hw, -hw, hd),
                floatArrayOf(0f, -1f, 0f), blankUvs
            ),
            // Right (+X)
            quadFloats(
                floatArrayOf(hw, -hw, hd), floatArrayOf(hw, -hw, -hd),
                floatArrayOf(hw, hw, -hd), floatArrayOf(hw, hw, hd),
                floatArrayOf(1f, 0f, 0f), blankUvs
            ),
            // Left (-X)
            quadFloats(
                floatArrayOf(-hw, -hw, -hd), floatArrayOf(-hw, -hw, hd),
                floatArrayOf(-hw, hw, hd), floatArrayOf(-hw, hw, -hd),
                floatArrayOf(-1f, 0f, 0f), blankUvs
            )
        )
        sidesVertexBuffer = floatBufferOf(sideFaces.reduce { acc, arr -> acc + arr })
        sidesIndexCount = 4 * 6 // 4 side faces, 6 vertices (2 tris) each

        // Winding direction is not load-bearing here: back-face culling is left
        // disabled in the renderer, so a reversed winding just costs a little
        // overdraw rather than making a face invisible.
    }

    private fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, normal: FloatArray, uvs: Array<FloatArray>): FloatBuffer =
        floatBufferOf(quadFloats(a, b, c, d, normal, uvs))

    /** Builds one quad (as two triangles, 6 vertices) in the interleaved pos(3)+normal(3)+uv(2) layout. */
    private fun quadFloats(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, normal: FloatArray, uvs: Array<FloatArray>): FloatArray {
        val verts = mutableListOf<Float>()
        val positions = listOf(a, b, c, d)
        for (i in 0..3) {
            verts.addAll(positions[i].toList())
            verts.addAll(normal.toList())
            verts.addAll(uvs[i].toList())
        }
        // Two triangles: (0,1,2) and (0,2,3)
        val expanded = mutableListOf<Float>()
        val order = intArrayOf(0, 1, 2, 0, 2, 3)
        for (idx in order) {
            val base = idx * 8
            for (k in 0 until 8) expanded.add(verts[base + k])
        }
        return expanded.toFloatArray()
    }

    companion object {
        private fun floatBufferOf(data: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(data.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(data); position(0) }
    }
}

private const val VERTEX_SHADER = """
attribute vec3 aPosition;
attribute vec3 aNormal;
attribute vec2 aUV;

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;
uniform mat3 uNormalMatrix;

varying vec3 vNormalWorld;
varying vec3 vWorldPos;
varying vec2 vUV;

void main() {
    vec4 worldPos = uModel * vec4(aPosition, 1.0);
    vWorldPos = worldPos.xyz;
    vNormalWorld = normalize(uNormalMatrix * aNormal);
    vUV = aUV;
    gl_Position = uProjection * uView * worldPos;
}
"""

private const val FRAGMENT_SHADER = """
precision mediump float;

varying vec3 vNormalWorld;
varying vec3 vWorldPos;
varying vec2 vUV;

uniform sampler2D uTexture;
uniform vec3 uLightDir;
uniform vec3 uViewPos;
uniform vec3 uBaseColor;
uniform vec3 uRimColor;
uniform float uIsShadow;
uniform float uUseTexture;

void main() {
    if (uIsShadow > 0.5) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 0.32);
        return;
    }

    vec3 n = normalize(vNormalWorld);
    vec3 l = normalize(uLightDir);
    vec3 v = normalize(uViewPos - vWorldPos);
    vec3 h = normalize(l + v);

    float diff = max(dot(n, l), 0.0);
    float spec = pow(max(dot(n, h), 0.0), 48.0);

    vec3 texColor = uBaseColor;
    if (uUseTexture > 0.5) {
        vec4 tex = texture2D(uTexture, vUV);
        texColor = mix(uBaseColor, tex.rgb, tex.a);
    }

    vec3 ambient = texColor * 0.38;
    vec3 diffuse = texColor * diff * 0.62;
    vec3 specular = uRimColor * spec * 0.9;

    gl_FragColor = vec4(ambient + diffuse + specular, 1.0);
}
"""

/** Compiles/links the single shared shader program used for every tile, side and shadow draw. */
fun buildTileShaderProgram(): Int {
    val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
    val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
    val program = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vs)
    GLES20.glAttachShader(program, fs)
    GLES20.glLinkProgram(program)
    val linkStatus = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
    if (linkStatus[0] == 0) {
        val log = GLES20.glGetProgramInfoLog(program)
        GLES20.glDeleteProgram(program)
        error("Tile shader link failed: $log")
    }
    return program
}

private fun compileShader(type: Int, source: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, source)
    GLES20.glCompileShader(shader)
    val status = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
    if (status[0] == 0) {
        val log = GLES20.glGetShaderInfoLog(shader)
        GLES20.glDeleteShader(shader)
        error("Tile shader compile failed: $log")
    }
    return shader
}

/** Uploads a bitmap as a GL_TEXTURE_2D and returns its texture id. */
fun uploadBitmapTexture(bitmap: Bitmap): Int {
    val ids = IntArray(1)
    GLES20.glGenTextures(1, ids, 0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    return ids[0]
}

/** Renders a small square bitmap for the "hidden" (not-yet-guessed) face of every tile. */
fun renderBlankSlotBitmap(sizePx: Int, fillColor: Int, rimColor: Int): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = rimColor
        style = Paint.Style.STROKE
        strokeWidth = sizePx * 0.05f
    }
    val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
    val corner = sizePx * 0.16f
    canvas.drawRoundRect(rect, corner, corner, fillPaint)
    val inset = rimPaint.strokeWidth / 2f
    canvas.drawRoundRect(RectF(inset, inset, sizePx - inset, sizePx - inset), corner, corner, rimPaint)
    return bmp
}

/** Renders a single uppercase glyph onto a square bitmap for the "revealed" face of a tile. */
fun renderGlyphBitmap(char: Char, sizePx: Int, fillColor: Int, rimColor: Int, textColor: Int): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = rimColor
        style = Paint.Style.STROKE
        strokeWidth = sizePx * 0.05f
    }
    val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
    val corner = sizePx * 0.16f
    canvas.drawRoundRect(rect, corner, corner, fillPaint)
    val inset = rimPaint.strokeWidth / 2f
    canvas.drawRoundRect(RectF(inset, inset, sizePx - inset, sizePx - inset), corner, corner, rimPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = sizePx * 0.62f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val text = char.uppercaseChar().toString()
    val textY = sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(text, sizePx / 2f, textY, textPaint)
    return bmp
}
