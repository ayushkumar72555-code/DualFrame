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
 * Samples one Camera2 external texture and renders two independently cropped
 * compositions. The upper output is 9:16; the lower output is 16:9.
 */
class DualCompositionRenderer(
    private val onInputSurfaceChanged: (SurfaceTexture?) -> Unit
) : GLSurfaceView.Renderer {

    companion object {
        private const val POSITION_ATTRIBUTE = 0
        private const val TEX_COORD_ATTRIBUTE = 1
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
            varying vec2 vTexCoord;
            void main() {
                vec2 uv = vTexCoord * uCrop.xy + uCrop.zw;
                gl_FragColor = texture2D(uCameraTexture, uv);
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

        GLES20.glClearColor(0.035f, 0.035f, 0.035f, 1f)
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
                // Camera input can be replaced while a frame is pending.
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (cameraTexture == null || programId == 0 || surfaceWidth <= 0 || surfaceHeight <= 0) return

        GLES20.glUseProgram(programId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniformMatrix4fv(
            GLES20.glGetUniformLocation(programId, "uTextureMatrix"),
            1, false, textureMatrix, 0
        )
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "uCameraTexture"), 0)

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(POSITION_ATTRIBUTE)
        GLES20.glVertexAttribPointer(POSITION_ATTRIBUTE, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
        vertexBuffer.position(2)
        GLES20.glEnableVertexAttribArray(TEX_COORD_ATTRIBUTE)
        GLES20.glVertexAttribPointer(TEX_COORD_ATTRIBUTE, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)

        drawComposition(0, surfaceHeight / 2, surfaceWidth, surfaceHeight - surfaceHeight / 2, 9f / 16f)
        drawComposition(0, 0, surfaceWidth, surfaceHeight / 2, 16f / 9f)

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

    private fun drawComposition(x: Int, y: Int, width: Int, height: Int, targetRatio: Float) {
        if (width <= 0 || height <= 0) return

        val areaRatio = width.toFloat() / height
        val viewportWidth: Int
        val viewportHeight: Int
        if (areaRatio > targetRatio) {
            viewportHeight = height
            viewportWidth = (height * targetRatio).toInt().coerceAtLeast(1)
        } else {
            viewportWidth = width
            viewportHeight = (width / targetRatio).toInt().coerceAtLeast(1)
        }

        GLES20.glViewport(
            x + (width - viewportWidth) / 2,
            y + (height - viewportHeight) / 2,
            viewportWidth,
            viewportHeight
        )

        // Camera2 input is selected as 16:9 (or the closest available ratio).
        val sourceRatio = 16f / 9f
        val cropX = if (sourceRatio > targetRatio) targetRatio / sourceRatio else 1f
        val cropY = if (sourceRatio < targetRatio) sourceRatio / targetRatio else 1f
        GLES20.glUniform4f(
            GLES20.glGetUniformLocation(programId, "uCrop"),
            cropX, cropY, (1f - cropX) / 2f, (1f - cropY) / 2f
        )
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

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

    fun releaseCameraInput() {
        queueEvent { dualRenderer.releaseInputSurface() }
    }
}
