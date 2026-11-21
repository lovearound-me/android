package me.lovearound.android.wear

import me.lovearound.android.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HiveTest {
    private fun people(n: Int) = (1..n).map { Target.Person(Contact("$it", "P$it")) }

    @Test fun rowsOfThreeAndFourWithTheStarInTheMiddle() {
        val rows = hiveRows(listOf(Target.Anyone) + people(8))
        assertEquals(listOf(3, 4, 2), rows.map { it.size })
        assertEquals(Target.Anyone, rows[0][1])
        assertEquals(listOf(Target.Anyone), hiveRows(listOf(Target.Anyone))[0])
    }

    @Test fun bubblesShrinkTowardsTheEdges() {
        assertEquals(1f, hiveScale(100f, 100f, 200f, 200f), 0f)
        val edge = hiveScale(0f, 0f, 200f, 200f)
        assertTrue(edge < 0.7f && edge >= 0.45f)
        assertEquals(0.45f, hiveScale(-500f, 900f, 200f, 200f), 0f)
    }

    @Test fun codesAreSpaced() {
        assertEquals("482 913", spaced("482913"))
        assertEquals("48291", spaced("48291"))
    }
}
