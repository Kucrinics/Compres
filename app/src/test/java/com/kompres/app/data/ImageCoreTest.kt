package com.kompres.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageCoreTest {

    @Test
    fun centerCropSquareFromLandscape() {
        val rect = ImageCore.centerCropRect(200, 100, 1f)
        assertEquals(50, rect.left)
        assertEquals(0, rect.top)
        assertEquals(150, rect.right)
        assertEquals(100, rect.bottom)
    }

    @Test
    fun centerCropSquareFromPortrait() {
        val rect = ImageCore.centerCropRect(100, 200, 1f)
        assertEquals(0, rect.left)
        assertEquals(50, rect.top)
        assertEquals(100, rect.right)
        assertEquals(150, rect.bottom)
    }

    @Test
    fun centerCropWideRatio() {
        val rect = ImageCore.centerCropRect(200, 100, 16f / 9f)
        assertEquals(11, rect.left)
        assertEquals(0, rect.top)
        assertEquals(189, rect.right)
        assertEquals(100, rect.bottom)
    }

    @Test
    fun scaledSizeHalves() {
        assertEquals(100 to 50, ImageCore.scaledSize(200, 100, 50))
        assertEquals(200 to 100, ImageCore.scaledSize(200, 100, 100))
    }

    @Test
    fun scaledSizeNeverZero() {
        assertEquals(1 to 1, ImageCore.scaledSize(1, 1, 25))
    }

    @Test
    fun formatMetadata() {
        assertEquals("jpg", ImageFormat.JPG.extension)
        assertEquals("image/jpeg", ImageFormat.JPG.mime)
        assertEquals("jfif", ImageFormat.JFIF.extension)
        assertEquals("image/jpeg", ImageFormat.JFIF.mime)
        assertEquals("webp", ImageFormat.WEBP.extension)
        assertEquals("image/webp", ImageFormat.WEBP.mime)
    }
}
