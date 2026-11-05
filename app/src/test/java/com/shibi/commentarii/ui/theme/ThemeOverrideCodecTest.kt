package com.shibi.commentarii.ui.theme

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeOverrideCodecTest {
    private val sample = """{"v":1,"l.bg":"FFF4F0FB","l.tab.0":"FFCDBEF2","d.primary":"FFC3B6F2","l.decoAlpha":"0.8",
        "titleFont":"PACIFICO","labelStyle":"STAMP","bullets":"STAR,HEART","tape":"true"}"""

    @Test
    fun roundTripKeepsEveryEntry() {
        val o = ThemeOverrideCodec.decode(sample)!!
        assertEquals(8, o.entries.size)
        val encoded = ThemeOverrideCodec.encode(o)
        val again = ThemeOverrideCodec.decode(encoded)!!
        assertEquals(o.entries, again.entries)
        assertEquals(1, JSONObject(encoded).getInt("v"))
        assertEquals(0xFFF4F0FBL, again.color("l.bg"))
    }

    @Test
    fun corruptOrMissingJsonIsStock() {
        for (bad in listOf(null, "", "   ", "{not json", "[1,2]", "\"x\"", "{}", """{"l.bg":"FFF4F0FB"}""", """{"v":"x"}""")) {
            assertNull("input: $bad", ThemeOverrideCodec.decode(bad))
        }
    }

    @Test
    fun futureVersionIsStock() {
        assertNull(ThemeOverrideCodec.decode("""{"v":2,"l.bg":"FFF4F0FB"}"""))
        assertNull(ThemeOverrideCodec.decode("""{"v":0,"l.bg":"FFF4F0FB"}"""))
    }

    @Test
    fun badSingleEntriesAreDroppedAlone() {
        val o = ThemeOverrideCodec.decode(
            """{"v":1,"l.bg":"80F4F0FB","d.bg":"FF112233","l.primary":"nothex!!","l.tab.99":"FFCDBEF2","l.tab.1":"FFCDBEF2",
            "l.decoAlpha":"7","d.decoAlpha":"0.5","titleFont":"COMIC","labelStyle":"PILL","bullets":"STAR,NOPE","tape":"maybe","check":"HEART"}""",
        )!!
        assertEquals(setOf("d.bg", "l.tab.1", "d.decoAlpha", "labelStyle", "check"), o.entries.keys)
    }

    @Test
    fun unknownKeysSurviveARewriteAndAreIgnored() {
        val o = ThemeOverrideCodec.decode("""{"v":1,"future.thing":"abc","l.bg":"FFF4F0FB"}""")!!
        assertEquals("abc", o["future.thing"])
        val rewritten = ThemeOverrideCodec.decode(ThemeOverrideCodec.encode(o.with("d.bg", "FF112233")))!!
        assertEquals("abc", rewritten["future.thing"])
        assertEquals(0xFF112233L, rewritten.color("d.bg"))
    }

    @Test
    fun moodKeysRoundTrip() {
        val o = ThemeOverride.Empty.withColor("l.mood.0", 0x00112233).withColor("d.onMood.4", 0xFF445566)
        val again = ThemeOverrideCodec.decode(ThemeOverrideCodec.encode(o))!!
        assertEquals(0xFF112233L, again.color("l.mood.0"))
        assertEquals(0xFF445566L, again.color("d.onMood.4"))
        val partly = ThemeOverrideCodec.decode("""{"v":1,"l.mood.5":"FF112233","l.onMood.2":"FF112233","d.mood.4":"FF000000"}""")!!
        assertEquals(setOf("l.onMood.2", "d.mood.4"), partly.entries.keys)
    }

    @Test
    fun withColorForcesOpaqueAndAnInvalidWithIsIgnored() {
        val o = ThemeOverride.Empty.withColor("l.bg", 0x00ABCDEF)
        assertEquals("FFABCDEF", o["l.bg"])
        assertSame(o, o.with("l.bg", "12345678"))
        assertTrue(o.without("l.bg").isEmpty())
    }
}
