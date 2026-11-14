package com.dualframe.camera.graphics

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Renders one camera texture as a blurred full-screen backdrop with two sharp,
 * independently cropped composition windows layered over it.
 */
class DualCompositionRenderer(
    private val onInputSurfaceChanged: (SurfaceTexture?) -> Unit
) : GLSurfaceView.Renderer {

    enum class Layout { PIP, STACKED }
    @Volatile var layout: Layout = Layout.PIP
    @Volatile var swapped: Boolean = false
    @Volatile var pipX: Float = 0.56f
    @Volatile var pipY: Float = 0.075f
    @Volatile var pipWidth: Float = 0.40f
    @Volatile var zoom: Float = 1f

    companion object {
        private const val POSITION_ATTRIBUTE = 0
        private const val TEX_COORD_ATTRIBUTE = 1
        private const val SOURCE_RATIO = 16f / 9f
        private val VERTICES = floatArrayOf(
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f
        )

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uTextureMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTextureMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uCameraTexture;
            uniform vec4 uCrop;
            uniform float uBlur;
            uniform float uZoom;
            varying vec2 vTexCoord;
            void main() {
                vec2 uv = (vTexCoord - vec2(0.5)) / uZoom + vec2(0.5);
                uv = uv * uCrop.xy + uCrop.zw;
                vec4 color = texture2D(uCameraTexture, uv) * 0.20;
                color += texture2D(uCameraTexture, uv + vec2(0.006, 0.0)) * 0.10;
                color += texture2D(uCameraTexture, uv - vec2(0.006, 0.0)) * 0.10;
                color += texture2D(uCameraTexture, uv + vec2(0.0, 0.006)) * 0.10;
                color += texture2D(uCameraTexture, uv - vec2(0.0, 0.006)) * 0.10;
                color += texture2D(uCameraTexture, uv + vec2(0.004, 0.004)) * 0.10;
                color += texture2D(uCameraTexture, uv + vec2(-0.004, 0.004)) * 0.10;
                color += texture2D(uCameraTexture, uv + vec2(0.004, -0.004)) * 0.10;
                color += texture2D(uCameraTexture, uv + vec2(-0.004, -0.004)) * 0.10;
                vec4 sharp = texture2D(uCameraTexture, uv);
                gl_FragColor = mix(sharp, color, uBlur);
            }
        """
    }

    @Volatile private var inputSurface: SurfaceTexture? = null
    @Volatile private var frameAvailable = false
    @Volatile var onFrameAvailableCallback: (() -> Unit)? = null

    private var textureId = 0
    private var programId = 0
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private val textureMatrix = FloatArray(16)
    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(VERTICES.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(VERTICES); position(0) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        inputSurface?.let {
            onInputSurfaceChanged(null)
            it.setOnFrameAvailableListener(null)
            it.release()
        }
        inputSurface = null
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        textureId = createExternalTexture()
        inputSurface = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener {
                frameAvailable = true
                onFrameAvailableCallback?.invoke()
            }
        }
        onInputSurfaceChanged(inputSurface)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val cameraTexture = inputSurface
        if (cameraTexture != null && frameAvailable) {
            try {
                cameraTexture.updateTexImage()
                cameraTexture.getTransformMatrix(textureMatrix)
                frameAvailable = false
            } catch (_: RuntimeException) {
                // A camera surface may be replaced while a frame is pending.
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (cameraTexture == null || programId == 0 || surfaceWidth <= 0 || surfaceHeight <= 0) return

        GLES20.glUseProgram(programId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniformMatrix4fv(location("uTextureMatrix"), 1, false, textureMatrix, 0)
        GLES20.glUniform1i(location("uCameraTexture"), 0)

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(POSITION_ATTRIBUTE)
        GLES20.glVertexAttribPointer(POSITION_ATTRIBUTE, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
        vertexBuffer.position(2)
        GLES20.glEnableVertexAttribArray(TEX_COORD_ATTRIBUTE)
        GLES20.glVertexAttribPointer(TEX_COORD_ATTRIBUTE, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)

        if (layout == Layout.PIP) {
            drawPass(0, 0, surfaceWidth, surfaceHeight, if (swapped) 16f / 9f else 9f / 16f, 0f)
            val pipW = (surfaceWidth * pipWidth).toInt().coerceAtLeast(1)
            val pipRatio = if (swapped) 9f / 16f else 16f / 9f
            val pipH = (pipW / pipRatio).toInt().coerceAtLeast(1)
            val px = (surfaceWidth * pipX).toInt().coerceIn(0, (surfaceWidth - pipW).coerceAtLeast(0))
            val top = (surfaceHeight * pipY).toInt().coerceIn(0, (surfaceHeight - pipH).coerceAtLeast(0))
            drawPass(px, surfaceHeight - top - pipH, pipW, pipH, pipRatio, 0f)
        } else {
            val pad = (surfaceHeight * 0.018f).toInt()
            val gap = (surfaceHeight * 0.014f).toInt()
            val landscapeW = surfaceWidth - 2 * pad
            val landscapeH = (landscapeW * 9f / 16f).toInt()
            drawPass(pad, surfaceHeight - pad - landscapeH, landscapeW, landscapeH, 16f / 9f, 0f)
            val portraitH = surfaceHeight - 2 * pad - landscapeH - gap
            val portraitW = (portraitH * 9f / 16f).toInt().coerceAtMost(surfaceWidth - 2 * pad)
            drawPass((surfaceWidth - portraitW) / 2, pad, portraitW, portraitH, 9f / 16f, 0f)
        }

        GLES20.glDisableVertexAttribArray(POSITION_ATTRIBUTE)
        GLES20.glDisableVertexAttribArray(TEX_COORD_ATTRIBUTE)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    fun releaseInputSurface() {
        inputSurface?.let {
            onInputSurfaceChanged(null)
            it.setOnFrameAvailableListener(null)
            it.release()
        }
        inputSurface = null
        frameAvailable = false
    }

    private fun drawPass(x: Int, y: Int, width: Int, height: Int, targetRatio: Float, blur: Float) {
        if (width <= 0 || height <= 0) return
        GLES20.glViewport(x, y, width, height)
        val cropX = if (SOURCE_RATIO > targetRatio) targetRatio / SOURCE_RATIO else 1f
        val cropY = if (SOURCE_RATIO < targetRatio) SOURCE_RATIO / targetRatio else 1f
        GLES20.glUniform4f(
            location("uCrop"),
            cropX, cropY, (1f - cropX) / 2f, (1f - cropY) / 2f
        )
        GLES20.glUniform1f(location("uBlur"), blur)
        GLES20.glUniform1f(location("uZoom"), zoom.coerceIn(1f, 5f))
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun location(name: String) = GLES20.glGetUniformLocation(programId, name)

    private fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glBindAttribLocation(program, POSITION_ATTRIBUTE, "aPosition")
        GLES20.glBindAttribLocation(program, TEX_COORD_ATTRIBUTE, "aTexCoord")
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val message = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw IllegalStateException("Unable to link DualFrame shader: $message")
        }
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val message = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Unable to compile DualFrame shader: $message")
        }
        return shader
    }
}

class DualCompositionGLSurfaceView(context: android.content.Context) : GLSurfaceView(context) {
    @Volatile var onInputSurfaceChanged: ((SurfaceTexture?) -> Unit)? = null

    private val dualRenderer = DualCompositionRenderer { surfaceTexture ->
        onInputSurfaceChanged?.invoke(surfaceTexture)
    }

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(dualRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        dualRenderer.onFrameAvailableCallback = { requestRender() }
    }

    fun setLayout(layout: DualCompositionRenderer.Layout, swapped: Boolean) { dualRenderer.layout = layout; dualRenderer.swapped = swapped; requestRender() }
    fun setZoom(value: Float) { dualRenderer.zoom = value; requestRender() }
    fun setPipPosition(x: Float, y: Float) { dualRenderer.pipX = x.coerceIn(0f, 0.75f); dualRenderer.pipY = y.coerceIn(0f, 0.8f); requestRender() }
    fun setPipWidth(value: Float) { dualRenderer.pipWidth = value.coerceIn(0.30f, 0.50f); requestRender() }

    fun releaseCameraInput() {
        queueEvent { dualRenderer.releaseInputSurface() }
    }
}
