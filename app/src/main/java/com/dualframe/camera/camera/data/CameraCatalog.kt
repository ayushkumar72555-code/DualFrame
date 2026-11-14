package com.dualframe.camera.camera.data

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.graphics.SurfaceTexture
import com.dualframe.camera.camera.model.CameraDescriptor
import com.dualframe.camera.camera.model.CameraLensFacing
import com.dualframe.camera.camera.model.CameraResolution

class CameraCatalog(
    private val cameraManager: CameraManager
) {
    fun discover(): List<CameraDescriptor> {
        return cameraManager.cameraIdList.map { cameraId ->
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val facing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> CameraLensFacing.BACK
                CameraCharacteristics.LENS_FACING_FRONT -> CameraLensFacing.FRONT
                CameraCharacteristics.LENS_FACING_EXTERNAL -> CameraLensFacing.EXTERNAL
                else -> CameraLensFacing.UNKNOWN
            }
            val hardwareLevel = when (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                else -> "UNKNOWN"
            }
            val previewSizes = characteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java)
                ?.map { CameraResolution(it.width, it.height) }
                .orEmpty()

            CameraDescriptor(
                id = cameraId,
                lensFacing = facing,
                flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                hardwareLevel = hardwareLevel,
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
                previewSizes = previewSizes
            )
        }.sortedBy { descriptor ->
            when (descriptor.lensFacing) {
                CameraLensFacing.BACK -> 0
                CameraLensFacing.FRONT -> 1
                CameraLensFacing.EXTERNAL -> 2
                CameraLensFacing.UNKNOWN -> 3
            }
        }
    }
}
