package com.roufsyed.cabin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangeTest {

    @Test
    fun `saved order wins and new items follow in the order given`() {
        assertEquals(listOf("c", "a", "b", "d"), Arrange.ordered(saved = listOf("c", "a"), present = listOf("a", "b", "c", "d")))
    }

    @Test
    fun `saved items that are gone are dropped`() {
        assertEquals(listOf("b", "a"), Arrange.ordered(saved = listOf("x", "b", "y", "a"), present = listOf("a", "b")))
    }

    @Test
    fun `duplicates in a saved order are kept once`() {
        assertEquals(listOf("b", "a"), Arrange.ordered(saved = listOf("b", "b", "a"), present = listOf("a", "b")))
    }

    @Test
    fun `with nothing saved the present order is used`() {
        assertEquals(listOf("a", "b"), Arrange.ordered(emptyList(), listOf("a", "b")))
    }

    @Test
    fun `move swaps with the neighbour`() {
        val order = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), Arrange.move(order, "b", -1))
        assertEquals(listOf("a", "c", "b"), Arrange.move(order, "b", 1))
    }

    @Test
    fun `move does nothing at either end or for an unknown item`() {
        val order = listOf("a", "b", "c")
        assertEquals(order, Arrange.move(order, "a", -1))
        assertEquals(order, Arrange.move(order, "c", 1))
        assertEquals(order, Arrange.move(order, "z", 1))
    }

    @Test
    fun `toggle hides then shows`() {
        assertEquals(setOf("a"), Arrange.toggle(emptySet(), "a"))
        assertEquals(emptySet<String>(), Arrange.toggle(setOf("a"), "a"))
    }

    @Test
    fun `the last visible item cannot be hidden`() {
        val order = listOf("a", "b", "c")
        assertTrue(Arrange.canHide(order, hidden = setOf("c"), item = "a"))
        assertFalse(Arrange.canHide(order, hidden = setOf("b", "c"), item = "a"))
    }

    @Test
    fun `a hidden item can always be shown again`() {
        assertTrue(Arrange.canHide(listOf("a", "b"), hidden = setOf("a", "b"), item = "a"))
    }
}
