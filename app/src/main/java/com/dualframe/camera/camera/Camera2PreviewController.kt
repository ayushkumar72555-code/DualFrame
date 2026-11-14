package com.dualframe.camera.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dualframe.camera.camera.model.CameraDescriptor
import com.dualframe.camera.camera.model.CameraLensFacing
import com.dualframe.camera.camera.model.CameraResolution
import kotlin.math.max

class Camera2PreviewController(
    context: Context
) : DefaultLifecycleObserver {

    private val appContext = context.applicationContext
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val handler = Handler(Looper.getMainLooper())

    var onPreviewError: ((String) -> Unit)? = null
    var onPreviewStarted: (() -> Unit)? = null

    private var textureView: TextureView? = null
    private var selectedCamera: CameraDescriptor? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var previewSize: Size? = null
    private var isStarted = false
    private var isOpening = false
    private var openGeneration = 0

    fun attach(view: TextureView) {
        textureView = view
        view.surfaceTextureListener = surfaceTextureListener
        if (view.isAvailable) openCameraIfReady()
    }

    fun selectCamera(camera: CameraDescriptor) {
        if (selectedCamera?.id == camera.id) return
        selectedCamera = camera
        closeCamera()
        openCameraIfReady()
    }

    fun start() {
        isStarted = true
        openCameraIfReady()
    }

    fun stop() {
        isStarted = false
        closeCamera()
    }

    fun release() {
        stop()
        textureView?.surfaceTextureListener = null
        textureView = null
        onPreviewError = null
        onPreviewStarted = null
    }

    override fun onResume(owner: LifecycleOwner) {
        start()
    }

    override fun onPause(owner: LifecycleOwner) {
        stop()
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCameraIfReady()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            closeCamera()
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }

    private fun openCameraIfReady() {
        val view = textureView ?: return
        val camera = selectedCamera ?: return
        if (!isStarted || !view.isAvailable || cameraDevice != null || isOpening) return

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onPreviewError?.invoke("Camera permission is required.")
            return
        }

        val generation = ++openGeneration
        try {
            val characteristics = cameraManager.getCameraCharacteristics(camera.id)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSizes = map?.getOutputSizes(SurfaceTexture::class.java).orEmpty()
            val isRotated = camera.sensorOrientation % 180 != 0
            val targetRatio = if (isRotated) {
                view.height.toFloat() / view.width.coerceAtLeast(1)
            } else {
                view.width.toFloat() / view.height.coerceAtLeast(1)
            }
            val selectedSize = PreviewSizeSelector.select(
                sizes = outputSizes.map { CameraResolution(it.width, it.height) },
                targetAspectRatio = targetRatio
            ) ?: throw IllegalStateException("This camera does not expose a supported preview size.")

            previewSize = Size(selectedSize.width, selectedSize.height)
            view.surfaceTexture?.setDefaultBufferSize(selectedSize.width, selectedSize.height)
            configureTransform(view.width, view.height)

            isOpening = true
            cameraManager.openCamera(
                camera.id,
                createCameraStateCallback(camera.id, generation),
                handler
            )
        } catch (exception: SecurityException) {
            if (generation == openGeneration) isOpening = false
            onPreviewError?.invoke("Camera permission was revoked. Grant permission and try again.")
        } catch (exception: Exception) {
            if (generation == openGeneration) isOpening = false
            onPreviewError?.invoke(exception.message ?: "Unable to open the camera.")
        }
    }

    private fun createCameraStateCallback(
        requestedCameraId: String,
        generation: Int
    ) = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            if (generation != openGeneration ||
                !isStarted ||
                selectedCamera?.id != requestedCameraId
            ) {
                camera.close()
                return
            }
            isOpening = false
            cameraDevice = camera
            createPreviewSession(camera)
        }

        override fun onDisconnected(camera: CameraDevice) {
            if (generation == openGeneration) {
                if (cameraDevice === camera) cameraDevice = null
                closeSession()
                onPreviewError?.invoke("The camera disconnected.")
            }
            camera.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            if (generation == openGeneration) {
                if (cameraDevice === camera) cameraDevice = null
                isOpening = false
                closeSession()
                onPreviewError?.invoke("Camera error ($error). Close other camera apps and try again.")
            }
            camera.close()
        }
    }

    private fun createPreviewSession(camera: CameraDevice) {
        val texture = textureView?.surfaceTexture ?: run {
            onPreviewError?.invoke("The camera preview surface is unavailable.")
            return
        }
        val size = previewSize ?: return

        try {
            texture.setDefaultBufferSize(size.width, size.height)
            previewSurface = Surface(texture)
            val surface = previewSurface ?: return
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }

            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (!isStarted || cameraDevice !== camera) {
                            session.close()
                            return
                        }
                        captureSession = session
                        try {
                            session.setRepeatingRequest(requestBuilder.build(), null, handler)
                            onPreviewStarted?.invoke()
                        } catch (exception: Exception) {
                            onPreviewError?.invoke(exception.message ?: "Unable to start the camera preview.")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        session.close()
                        onPreviewError?.invoke("The camera could not configure a preview session.")
                    }
                },
                handler
            )
        } catch (exception: Exception) {
            onPreviewError?.invoke(exception.message ?: "Unable to create the camera preview.")
        }
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val view = textureView ?: return
        val size = previewSize ?: return
        if (viewWidth <= 0 || viewHeight <= 0) return

        val rotation = view.display?.rotation ?: Surface.ROTATION_0
        val displayDegrees = when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val sensorOrientation = selectedCamera?.sensorOrientation ?: 0
        val isFront = selectedCamera?.lensFacing == CameraLensFacing.FRONT
        val relativeRotation = if (isFront) {
            (sensorOrientation + displayDegrees) % 360
        } else {
            (sensorOrientation - displayDegrees + 360) % 360
        }

        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(
            0f,
            0f,
            size.height.toFloat(),
            size.width.toFloat()
        ).apply {
            offset(centerX - centerX(), centerY - centerY())
        }

        val matrix = Matrix()
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
        val scale = max(
            viewHeight.toFloat() / size.height,
            viewWidth.toFloat() / size.width
        )
        matrix.postScale(scale, scale, centerX, centerY)
        matrix.postRotate(relativeRotation.toFloat(), centerX, centerY)
        if (isFront) matrix.postScale(-1f, 1f, centerX, centerY)
        view.setTransform(matrix)
    }

    private fun RectF.centerX(): Float = (left + right) / 2f
    private fun RectF.centerY(): Float = (top + bottom) / 2f

    private fun closeSession() {
        captureSession?.close()
        captureSession = null
        previewSurface?.release()
        previewSurface = null
    }

    private fun closeCamera() {
        openGeneration += 1
        isOpening = false
        closeSession()
        cameraDevice?.close()
        cameraDevice = null
        previewSize = null
    }
}
