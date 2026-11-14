package com.dualframe.camera.camera

import com.dualframe.camera.camera.model.CameraResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewSizeSelectorTest {

    private val sizes = listOf(
        CameraResolution(640, 480),
        CameraResolution(1280, 720),
        CameraResolution(1440, 1080),
        CameraResolution(1920, 1080),
        CameraResolution(3840, 2160)
    )

    @Test
    fun selectsLargestBoundedSizeWithMatchingWideAspectRatio() {
        val selected = PreviewSizeSelector.select(sizes, targetAspectRatio = 16f / 9f)

        assertEquals(CameraResolution(1920, 1080), selected)
    }

    @Test
    fun selectsFourByThreeSizeWhenThatMatchesTheTarget() {
        val selected = PreviewSizeSelector.select(sizes, targetAspectRatio = 4f / 3f)

        assertEquals(CameraResolution(1440, 1080), selected)
    }

    @Test
    fun returnsNullWhenNoSizesAreAvailable() {
        assertNull(PreviewSizeSelector.select(emptyList(), targetAspectRatio = 16f / 9f))
    }
}
