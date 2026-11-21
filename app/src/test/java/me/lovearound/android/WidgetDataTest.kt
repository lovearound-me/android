package me.lovearound.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import me.lovearound.android.widget.Widgets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the widgets and quick settings draw and send to (WidgetData), as on the iPhone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetDataTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val people = Widgets.sample(ru = true)

    @Before fun quiet() { WidgetData.onChange = {} }

    @Test fun closeFirstInTheOwnersOrder() {
        val book = listOf(
            Contact("a", "Ваня", 0, false), Contact("b", "Мама", 2, true),
            Contact("c", "Аня", 1, true), Contact("d", "Дима", 1, false),
        )
        assertEquals(listOf("c", "b", "a", "d"), WidgetData.from(book).map { it.id })
    }

    @Test fun signedOutIsNotAnEmptyBook() {
        WidgetData.save(context, emptyList())
        assertEquals(emptyList<WidgetData.Person>(), WidgetData.people(context))
        WidgetData.clear(context)
        assertNull(WidgetData.people(context))
    }

    @Test fun signingOutKeepsMyColorOnly() {
        WidgetData.setMyColor(context, 5)
        WidgetData.save(context, people)
        WidgetData.choose(context, "tile", "3")
        WidgetData.clear(context)
        assertEquals(5, WidgetData.myColor(context))
        assertNull(WidgetData.chosen(context, "tile"))
    }

    @Test fun aBubbleShowsItsHeartForTwoSeconds() {
        WidgetData.sent(context, "2", at = 1_000)
        assertEquals("2" to 3_000L, WidgetData.justSent(context, now = 2_999))
        assertNull(WidgetData.justSent(context, now = 3_000))
    }

    @Test fun pagesOnlyWithMoreThanFourClose() {
        assertNull(WidgetData.pagedPeople(people))
        assertEquals(1, WidgetData.pageCount(people))
        val six = people.mapIndexed { i, p -> p.copy(close = i < 6) }
        assertEquals(2, WidgetData.pageCount(six))
        val nine = people.map { it.copy(close = true) } + people.first().copy(id = "9", close = true)
        assertEquals(3, WidgetData.pageCount(nine))
    }

    @Test fun nextPageWrapsAndGoesBackAfterAMinute() {
        WidgetData.save(context, people.mapIndexed { i, p -> p.copy(close = i < 6) })
        WidgetData.nextPage(context, now = 10_000)
        assertEquals(1 to 70_000L, WidgetData.page(context, now = 10_001))
        assertEquals(0 to null, WidgetData.page(context, now = 70_000))
        WidgetData.nextPage(context, now = 20_000)
        WidgetData.nextPage(context, now = 21_000)
        // Two pages: the third «›» is back on the second.
        assertEquals(1, WidgetData.page(context, now = 21_001).first)
        WidgetData.nextPage(context, now = 22_000)
        assertEquals(0, WidgetData.page(context, now = 22_001).first)
    }

    @Test fun hiveIsThreeFourTwoWithAnyoneSecond() {
        val rows = WidgetData.hiveRows(people)
        assertEquals(listOf(3, 4, 2), rows.map { it.size })
        assertNull(rows[0][1])
        assertEquals(listOf("1", "2"), listOf(rows[0][0]!!.id, rows[0][2]!!.id))
        assertEquals(listOf(listOf<String?>("1", null)), WidgetData.hiveRows(people.take(1)).map { r -> r.map { it?.id } })
    }

    @Test fun aChosenPersonWhoLeftFallsBackToTheFirst() {
        WidgetData.save(context, people)
        assertEquals("1", WidgetData.personFor(context, "widget-7")?.id)
        WidgetData.choose(context, "widget-7", "4")
        assertEquals("4", WidgetData.personFor(context, "widget-7")?.id)
        WidgetData.save(context, people.filter { it.id != "4" })
        assertEquals("1", WidgetData.personFor(context, "widget-7")?.id)
    }

    @Test fun initialIsWholeEvenForAnEmoji() {
        assertEquals("А", WidgetData.Person("1", " аня").initial)
        assertEquals("🐱", WidgetData.Person("1", "🐱 Котик").initial)
        assertEquals("♥", WidgetData.Person("1", "  ").initial)
    }

    @Test fun anyoneWaitsAnHourAfterAWidgetSentOne() {
        AppModel.noteSent(context, AppModel.ANYONE_ID)
        val wait = AppModel.anyoneWait(context)
        assertEquals(60, wait)
        assertNull(AppModel.anyoneWait(context, java.time.Instant.now().plusSeconds(3601)))
    }
}
