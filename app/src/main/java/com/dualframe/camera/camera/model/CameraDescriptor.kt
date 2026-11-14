package com.dualframe.camera.camera.model

enum class CameraLensFacing {
    FRONT,
    BACK,
    EXTERNAL,
    UNKNOWN
}

data class CameraResolution(
    val width: Int,
    val height: Int
) {
    init {
        require(width > 0) { "Camera width must be positive." }
        require(height > 0) { "Camera height must be positive." }
    }

    val aspectRatio: Float
        get() = width.toFloat() / height.toFloat()
}

data class CameraDescriptor(
    val id: String,
    val lensFacing: CameraLensFacing,
    val flashAvailable: Boolean,
    val hardwareLevel: String,
    val sensorOrientation: Int,
    val previewSizes: List<CameraResolution>
) {
    val displayName: String
        get() = when (lensFacing) {
            CameraLensFacing.BACK -> "Rear camera"
            CameraLensFacing.FRONT -> "Front camera"
            CameraLensFacing.EXTERNAL -> "External camera"
            CameraLensFacing.UNKNOWN -> "Camera $id"
        }
}
