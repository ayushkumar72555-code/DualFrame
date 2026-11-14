package com.dualframe.camera.camera

import com.dualframe.camera.camera.model.CameraResolution
import kotlin.math.abs

object PreviewSizeSelector {
    fun select(
        sizes: List<CameraResolution>,
        targetAspectRatio: Float,
        maxWidth: Int = 1920,
        maxHeight: Int = 1080
    ): CameraResolution? {
        if (sizes.isEmpty() || targetAspectRatio <= 0f) return null

        val boundedSizes = sizes.filter {
            it.width <= maxWidth && it.height <= maxHeight
        }
        val candidates = boundedSizes.ifEmpty { sizes }

        return candidates.minWithOrNull(
            compareBy<CameraResolution> { abs(it.aspectRatio - targetAspectRatio) }
                .thenByDescending { it.width.toLong() * it.height.toLong() }
        )
    }
}
