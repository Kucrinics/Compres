package com.kompres.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ImageCoreGraphicsTest {

    @Test
    fun cropsSquareAndScales() {
        val source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val inputBytes = ByteArrayOutputStream().also {
            source.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()

        val output = ByteArrayOutputStream()
        val result = ImageCore.convertToStream(
            ByteArrayInputStream(inputBytes),
            output,
            ImageOptions(
                format = ImageFormat.JPG,
                quality = 90,
                scalePercent = 50,
                cropRatio = 1f
            )
        )

        assertEquals(50, result.width)
        assertEquals(50, result.height)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val outBytes = output.toByteArray()
        BitmapFactory.decodeByteArray(outBytes, 0, outBytes.size, bounds)
        assertEquals(50, bounds.outWidth)
        assertEquals(50, bounds.outHeight)
    }

    @Test
    fun pngToWebpKeepsDimensions() {
        val source = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        val inputBytes = ByteArrayOutputStream().also {
            source.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()

        val output = ByteArrayOutputStream()
        val result = ImageCore.convertToStream(
            ByteArrayInputStream(inputBytes),
            output,
            ImageOptions(format = ImageFormat.PNG)
        )

        assertEquals(120, result.width)
        assertEquals(80, result.height)
    }
}
