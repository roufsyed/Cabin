package com.roufsyed.cabin

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.media.Covers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** How a player's cover becomes the picture Cabin keeps, whether it was sent or has to be read. */
@RunWith(AndroidJUnit4::class)
class CoversTest {

    private val resolver get() = ApplicationProvider.getApplicationContext<Context>().contentResolver

    private fun picture(width: Int, height: Int, colour: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(colour) }

    @Test
    fun aLargeCoverIsScaledDownToTheSizeCabinKeeps() {
        val cover = Covers.scaled(picture(800, 800, Color.BLUE)).asAndroidBitmap()
        assertEquals(Covers.SIDE_PX, cover.width)
        assertEquals(Covers.SIDE_PX, cover.height)
        assertEquals(Color.BLUE, cover.getPixel(Covers.SIDE_PX / 2, Covers.SIDE_PX / 2))
    }

    @Test
    fun aCoverThatIsNotSquareLosesItsEdgesAndKeepsItsMiddle() {
        // 800 x 600 with a red band 100 wide down each side: the square in the middle is all green.
        val wide = picture(800, 600, Color.GREEN)
        val red = Paint().apply { color = Color.RED }
        Canvas(wide).apply {
            drawRect(0f, 0f, 100f, 600f, red)
            drawRect(700f, 0f, 800f, 600f, red)
        }
        val cover = Covers.scaled(wide).asAndroidBitmap()
        assertEquals(Covers.SIDE_PX, cover.width)
        assertEquals(Covers.SIDE_PX, cover.height)
        val far = Covers.SIDE_PX - 2
        val middle = Covers.SIDE_PX / 2
        for ((x, y) in listOf(1 to 1, far to 1, 1 to far, far to far, middle to middle)) {
            assertEquals("at $x, $y", Color.GREEN, cover.getPixel(x, y))
        }
    }

    @Test
    fun aSmallCoverKeepsItsSizeAndIsCabinsOwnCopy() {
        // A radio app's station picture came as 70 x 70 on the emulator.
        val sent = picture(70, 70, Color.YELLOW)
        val cover = Covers.scaled(sent).asAndroidBitmap()
        assertEquals(70, cover.width)
        assertEquals(70, cover.height)
        assertNotSame(sent, cover)
        // The player reusing its bitmap for the next track does not repaint the one on show.
        sent.eraseColor(Color.BLACK)
        assertEquals(Color.YELLOW, cover.getPixel(35, 35))
    }

    @Test
    fun aCoverIsReadFromAnAddressAndScaledDown() {
        // A picture of the test's own in Android's shared picture store: an address like a player's.
        val details = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "cabin-cover-test-${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        val address = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), details)!!
        try {
            resolver.openOutputStream(address)!!.use { picture(1000, 800, Color.MAGENTA).compress(Bitmap.CompressFormat.PNG, 100, it) }
            val cover = Covers.read(resolver, address)!!.asAndroidBitmap()
            assertEquals(Covers.SIDE_PX, cover.width)
            assertEquals(Covers.SIDE_PX, cover.height)
            assertEquals(Color.MAGENTA, cover.getPixel(Covers.SIDE_PX / 2, Covers.SIDE_PX / 2))
        } finally {
            resolver.delete(address, null, null)
        }
    }

    @Test
    fun anAddressThatCannotBeReadGivesNoCover() {
        // Nothing answers at this address.
        assertNull(Covers.read(resolver, Uri.parse("content://com.roufsyed.cabin.nowhere/cover/1")))
        // Something answers here, but it is not a picture.
        assertNull(Covers.read(resolver, Uri.parse("content://settings/system/screen_brightness")))
    }
}
