package com.dualframe.camera.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dualframe.camera.camera.model.CameraDescriptor
import com.dualframe.camera.camera.model.CameraResolution

/** Camera2 producer that feeds one SurfaceTexture owned by the OpenGL renderer. */
class Camera2PreviewController(context: Context) : DefaultLifecycleObserver {

    private val appContext = context.applicationContext
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val handler = Handler(Looper.getMainLooper())

    var onPreviewError: ((String) -> Unit)? = null
    var onPreviewStarted: (() -> Unit)? = null

    private var inputSurfaceTexture: SurfaceTexture? = null
    private var selectedCamera: CameraDescriptor? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var isStarted = false
    private var isOpening = false
    private var openGeneration = 0

    fun attachInputSurface(surfaceTexture: SurfaceTexture) {
        handler.post {
            if (inputSurfaceTexture === surfaceTexture) return@post
            closeCamera()
            inputSurfaceTexture = surfaceTexture
            openCameraIfReady()
        }
    }

    fun detachInputSurface(surfaceTexture: SurfaceTexture? = null) {
        handler.post {
            if (surfaceTexture == null || inputSurfaceTexture === surfaceTexture) {
                closeCamera()
                inputSurfaceTexture = null
            }
        }
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
        inputSurfaceTexture = null
        onPreviewError = null
        onPreviewStarted = null
    }

    override fun onResume(owner: LifecycleOwner) = start()
    override fun onPause(owner: LifecycleOwner) = stop()

    private fun openCameraIfReady() {
        val camera = selectedCamera ?: return
        val texture = inputSurfaceTexture ?: return
        if (!isStarted || cameraDevice != null || isOpening) return

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onPreviewError?.invoke("Camera permission is required.")
            return
        }

        val generation = ++openGeneration
        try {
            val outputSizes = cameraManager.getCameraCharacteristics(camera.id)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java)
                .orEmpty()

            val selectedSize = PreviewSizeSelector.select(
                sizes = outputSizes.map { CameraResolution(it.width, it.height) },
                targetAspectRatio = 16f / 9f,
                maxWidth = 1920,
                maxHeight = 1080
            ) ?: throw IllegalStateException("No supported camera preview size was found.")

            texture.setDefaultBufferSize(selectedSize.width, selectedSize.height)
            isOpening = true
            cameraManager.openCamera(camera.id, createCameraStateCallback(camera.id, generation), handler)
        } catch (exception: SecurityException) {
            if (generation == openGeneration) isOpening = false
            onPreviewError?.invoke("Camera permission was revoked. Grant permission and try again.")
        } catch (exception: Exception) {
            if (generation == openGeneration) isOpening = false
            onPreviewError?.invoke(exception.message ?: "Unable to open the camera.")
        }
    }

    private fun createCameraStateCallback(requestedCameraId: String, generation: Int) =
        object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                if (generation != openGeneration || !isStarted ||
                    selectedCamera?.id != requestedCameraId || inputSurfaceTexture == null
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
                    isOpening = false
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
        val texture = inputSurfaceTexture ?: run {
            onPreviewError?.invoke("The OpenGL camera input is unavailable.")
            return
        }

        try {
            previewSurface = Surface(texture)
            val surface = previewSurface ?: return
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }

            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (!isStarted || cameraDevice !== camera || inputSurfaceTexture !== texture) {
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
                        onPreviewError?.invoke("The camera could not configure an OpenGL preview session.")
                    }
                },
                handler
            )
        } catch (exception: Exception) {
            onPreviewError?.invoke(exception.message ?: "Unable to create the OpenGL preview.")
        }
    }

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
    }
}
