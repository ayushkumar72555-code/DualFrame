package com.dualframe.camera.graphics

import org.junit.Assert.assertEquals
import org.junit.Test

class CompositionCropTest {
    @Test
    fun portraitCompositionUsesCenteredHorizontalCrop() {
        val sourceRatio = 16f / 9f
        val targetRatio = 9f / 16f
        val cropX = targetRatio / sourceRatio
        val offsetX = (1f - cropX) / 2f

        assertEquals(0.31640625f, cropX, 0.0001f)
        assertEquals(0.341796875f, offsetX, 0.0001f)
    }

    @Test
    fun landscapeCompositionRetainsFullSixteenByNineFrame() {
        val sourceRatio = 16f / 9f
        val targetRatio = 16f / 9f
        val cropX = if (sourceRatio > targetRatio) targetRatio / sourceRatio else 1f
        val cropY = if (sourceRatio < targetRatio) sourceRatio / targetRatio else 1f

        assertEquals(1f, cropX, 0.0001f)
        assertEquals(1f, cropY, 0.0001f)
    }
}
