package com.shibi.commentarii.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The stock-identical gate for theme customization: every scheme, extras and art value of the 8 themes in both modes,
 * dumped to text and compared with app/src/test/resources/golden/theme_stock.txt. A diff means the stock look changed.
 * To regenerate on purpose, run with UPDATE_GOLDEN=true in the environment (writes the file; commit it).
 */
class ThemeStockGoldenTest {
    private fun Color.hex() = "%08X".format(toArgb())
    private fun Color?.hexOrNone() = this?.hex() ?: "none"

    private fun schemeLines(s: ColorScheme): List<String> =
        ColorScheme::class.java.declaredMethods
            .filter { it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE && it.name.startsWith("get") }
            .map { m -> m.name.removePrefix("get").substringBefore('-') to Color((m.invoke(s) as Long).toULong()) }
            .sortedBy { it.first }
            .map { (n, c) -> "scheme.$n=${c.hex()}" }

    private fun fontName(f: FontFamily) = when (f) {
        ArtFonts.PatrickHand -> "PatrickHand"
        ArtFonts.Pacifico -> "Pacifico"
        ArtFonts.Sniglet -> "Sniglet"
        ArtFonts.Dancing -> "Dancing"
        else -> "other"
    }

    private fun artColorLines(a: ArtColors) = listOf(
        "art.accents=" + a.accents.joinToString(",") { it.hex() },
        "art.ink=${a.ink.hex()}",
        "art.title=" + a.title.joinToString(",") { it.hex() },
        "art.outline=${a.outline.hexOrNone()}",
        "art.paper=${a.paper.hex()}",
        "art.decoAlpha=${a.decoAlpha}",
        "art.cardEdge=${a.cardEdge.hexOrNone()}",
    )

    private fun dump(): String = buildString {
        for (t in AppThemes.all) {
            appendLine(
                "theme ${t.id} header=${t.headerStyle} animated=${t.animated} period=${t.periodMillis} " +
                    "phase=${t.staticPhase} time=${t.staticTimeSec} deco=${t.decoration != null} scene=${t.scene != null} " +
                    "titleFont=${fontName(t.titleFont)}",
            )
            val art = themeArt(t.id)
            if (art == null) {
                appendLine("art=none")
            } else {
                appendLine(
                    "art.titleFont=${fontName(art.titleFont)} coloring=${art.titleColoring} titleTrail=${art.titleTrail} " +
                        "label=${art.labelStyle} labelFont=${fontName(art.labelFont)} labelTrail=${art.labelTrail} " +
                        "bullets=${art.bullets.joinToString(",")} check=${art.check} paper=${art.paper} tape=${art.tape} salt=${art.cardSalt}",
                )
            }
            for ((name, mode, ac) in listOf(Triple("light", t.light, art?.light), Triple("dark", t.dark, art?.dark))) {
                appendLine("mode $name titleBrush=${mode.titleBrush != null}")
                schemeLines(mode.scheme).forEach { appendLine(it) }
                val e = mode.extras
                appendLine(
                    "extras.tab=${e.tab.hex()} onTab=${e.onTab.hex()} sage=${e.sage.hex()} onSage=${e.onSage.hex()} " +
                        "card=${e.card.hex()} onCard=${e.onCard.hex()}",
                )
                appendLine("extras.mood=" + e.mood.joinToString(",") { it.hex() })
                appendLine("extras.onMood=" + e.onMood.joinToString(",") { it.hex() })
                appendLine("extras.tabs=" + e.tabs.joinToString(",") { it.hex() })
                if (ac != null) artColorLines(ac).forEach { appendLine(it) }
            }
        }
    }

    @Test
    fun stockThemesMatchTheGolden() {
        val actual = dump()
        if (System.getenv("UPDATE_GOLDEN") == "true") {
            File(System.getProperty("user.dir"), "src/test/resources/golden/theme_stock.txt").writeText(actual)
        }
        val golden = javaClass.classLoader!!.getResourceAsStream("golden/theme_stock.txt")
        assertNotNull("golden/theme_stock.txt missing; generate with UPDATE_GOLDEN=true", golden)
        assertEquals(golden!!.readBytes().toString(Charsets.UTF_8).replace("\r\n", "\n"), actual)
    }

    @Test
    fun dumpCoversEightThemesInBothModes() {
        val d = dump()
        assertEquals(8, Regex("^theme ", RegexOption.MULTILINE).findAll(d).count())
        assertEquals(16, Regex("^mode ", RegexOption.MULTILINE).findAll(d).count())
    }
}
