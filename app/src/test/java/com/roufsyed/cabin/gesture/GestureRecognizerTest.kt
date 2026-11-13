package com.roufsyed.cabin.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureRecognizerTest {

    private val recognizer = GestureRecognizer()
    private val none = emptyList<Gesture>()

    @Test
    fun `a tap is not a gesture and is left to the button`() {
        assertEquals(none, recognizer.down(1, 100f, 100f, 0))
        assertFalse(recognizer.claimed)
        assertEquals(none, recognizer.move(1, 105f, 104f, 20))
        assertFalse(recognizer.claimed)
        assertEquals(none, recognizer.up(1, 60))
        assertFalse(recognizer.claimed)
    }

    @Test
    fun `a drag past 12 pixels is claimed before it is a swipe`() {
        recognizer.down(1, 100f, 100f, 0)
        assertEquals(none, recognizer.move(1, 113f, 100f, 10))
        assertTrue(recognizer.claimed)
        assertEquals(none, recognizer.move(1, 160f, 100f, 20))
    }

    @Test
    fun `a swipe fires once at 70 pixels, in its main direction`() {
        recognizer.down(1, 400f, 300f, 0)
        assertEquals(none, recognizer.move(1, 331f, 300f, 10))
        assertEquals(listOf(Gesture.SwipeLeft), recognizer.move(1, 330f, 300f, 20))
        assertEquals(none, recognizer.move(1, 200f, 300f, 30))
        assertEquals(none, recognizer.up(1, 40))
    }

    @Test
    fun `each direction is recognised`() {
        fun swipe(dx: Float, dy: Float): List<Gesture> {
            val fresh = GestureRecognizer()
            fresh.down(1, 500f, 400f, 0)
            return fresh.move(1, 500f + dx, 400f + dy, 10)
        }
        assertEquals(listOf(Gesture.SwipeRight), swipe(80f, 0f))
        assertEquals(listOf(Gesture.SwipeLeft), swipe(-80f, 0f))
        assertEquals(listOf(Gesture.SwipeDown), swipe(0f, 80f))
        assertEquals(listOf(Gesture.SwipeUp), swipe(0f, -80f))
    }

    @Test
    fun `a diagonal drag goes with the longer side`() {
        recognizer.down(1, 500f, 400f, 0)
        assertEquals(listOf(Gesture.SwipeDown), recognizer.move(1, 540f, 480f, 10))

        val other = GestureRecognizer()
        other.down(1, 500f, 400f, 0)
        assertEquals(listOf(Gesture.SwipeLeft), other.move(1, 410f, 440f, 10))
    }

    @Test
    fun `two fingers change the volume one step per 24 pixels, up for louder`() {
        recognizer.down(1, 500f, 500f, 0)
        recognizer.down(2, 560f, 500f, 5)
        assertTrue(recognizer.claimed)
        assertEquals(none, recognizer.move(1, 500f, 492f, 10))
        assertEquals(listOf(Gesture.VolumeBegin, Gesture.Volume(1)), recognizer.move(1, 500f, 480f, 20))
        assertEquals(listOf(Gesture.Volume(3)), recognizer.move(1, 500f, 428f, 30))
        assertEquals(none, recognizer.move(1, 500f, 426f, 35))
        assertEquals(listOf(Gesture.Volume(-2)), recognizer.move(1, 500f, 548f, 40))
        assertEquals(none, recognizer.up(2, 50))
        assertEquals(listOf(Gesture.VolumeEnd), recognizer.up(1, 60))
    }

    @Test
    fun `volume is measured from where the first finger was when the second landed`() {
        recognizer.down(1, 500f, 500f, 0)
        recognizer.move(1, 500f, 490f, 5)
        recognizer.down(2, 560f, 495f, 10)
        // 30 px above the start, but only 20 above where the drag became a volume drag.
        assertEquals(listOf(Gesture.VolumeBegin, Gesture.Volume(1)), recognizer.move(1, 500f, 470f, 20))
    }

    @Test
    fun `only the first finger steers`() {
        recognizer.down(1, 500f, 500f, 0)
        recognizer.down(2, 560f, 500f, 5)
        assertEquals(none, recognizer.move(2, 560f, 300f, 10))
    }

    @Test
    fun `two fingers never swipe`() {
        recognizer.down(1, 500f, 500f, 0)
        recognizer.down(2, 560f, 500f, 5)
        val events = recognizer.move(1, 300f, 500f, 10)
        assertEquals(listOf(Gesture.VolumeBegin), events)
    }

    @Test
    fun `a second finger after the swipe has fired changes nothing`() {
        recognizer.down(1, 500f, 400f, 0)
        assertEquals(listOf(Gesture.SwipeLeft), recognizer.move(1, 400f, 400f, 10))
        recognizer.down(2, 600f, 400f, 15)
        assertEquals(none, recognizer.move(1, 400f, 300f, 20))
        assertEquals(none, recognizer.up(1, 30))
        assertEquals(none, recognizer.up(2, 35))
    }

    @Test
    fun `a touch just after a swipe is claimed so it cannot tap`() {
        recognizer.down(1, 500f, 400f, 0)
        recognizer.move(1, 400f, 400f, 50)
        recognizer.up(1, 100)

        recognizer.down(1, 700f, 300f, 300)
        assertTrue(recognizer.claimed)
        recognizer.up(1, 340)

        recognizer.down(1, 700f, 300f, 600)
        assertFalse(recognizer.claimed)
    }

    @Test
    fun `a drag that never became a swipe still blocks the next quick tap`() {
        recognizer.down(1, 500f, 400f, 0)
        recognizer.move(1, 530f, 400f, 50)
        recognizer.up(1, 100)

        recognizer.down(1, 500f, 400f, 200)
        assertTrue(recognizer.claimed)
    }

    @Test
    fun `losing the touch ends a volume drag`() {
        recognizer.down(1, 500f, 500f, 0)
        recognizer.down(2, 560f, 500f, 5)
        recognizer.move(1, 500f, 440f, 10)
        assertEquals(listOf(Gesture.VolumeEnd), recognizer.cancel())
        assertFalse(recognizer.claimed)
        assertEquals(none, recognizer.cancel())
    }

    @Test
    fun `distances can be scaled for a screen that is not 1280 wide`() {
        val scaled = GestureRecognizer(slop = 24f, swipeDistance = 140f, volumeStep = 48f)
        scaled.down(1, 500f, 400f, 0)
        assertEquals(none, scaled.move(1, 600f, 400f, 10))
        assertEquals(listOf(Gesture.SwipeRight), scaled.move(1, 640f, 400f, 20))
    }
}
