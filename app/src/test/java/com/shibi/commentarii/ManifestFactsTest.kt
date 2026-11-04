package com.shibi.commentarii

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.notify.BootReceiver
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * Checklist #218, #307, #308, #312: things that must stay absent. The manifest checks read the merged manifest through
 * PackageManager, so a permission or component added by a library counts. The source scans cover what no manifest shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManifestFactsTest {
    private lateinit var context: Context
    private lateinit var pm: PackageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        pm = context.packageManager
    }

    private fun receiverNames(intent: Intent) = pm.queryBroadcastReceivers(intent, 0).map { it.activityInfo.name }

    private fun sourceFiles(dir: String): Map<File, String> {
        val root = File("src/main/java/com/shibi/commentarii", dir)
        val files = root.walkTopDown().filter { it.extension == "kt" }.associateWith { it.readText() }
        assertTrue("read no .kt files under ${root.absolutePath}", files.isNotEmpty())
        return files
    }

    private fun Map<File, String>.matching(pattern: Regex) = filterValues { pattern.containsMatchIn(it) }.keys.map { it.name }

    @Test
    fun item307_mergedManifestHasNoNetworkPermissions() {
        val requested = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions!!.toList()
        assertTrue("POST_NOTIFICATIONS from our manifest", "android.permission.POST_NOTIFICATIONS" in requested)
        assertTrue("USE_BIOMETRIC only comes from a library, so this is the merged manifest", "android.permission.USE_BIOMETRIC" in requested)

        assertFalse(requested.toString(), "android.permission.INTERNET" in requested)
        assertFalse(requested.toString(), "android.permission.ACCESS_NETWORK_STATE" in requested)
    }

    @Test
    fun item308_systemBackupIsOffAndRulesExcludeEverything() {
        val app = pm.getApplicationInfo(context.packageName, 0)
        assertTrue("flags come from the real manifest", app.flags and ApplicationInfo.FLAG_SUPPORTS_RTL != 0)
        assertEquals(0, app.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertNull("no backup agent", app.backupAgentName)

        val excluded = mutableMapOf<String, MutableSet<String>>()
        val included = mutableListOf<String>()
        var section = ""
        val parser = context.resources.getXml(R.xml.data_extraction_rules)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "cloud-backup", "device-transfer" -> section = parser.name
                "exclude" -> excluded.getOrPut(section) { mutableSetOf() } += parser.getAttributeValue(null, "domain")
                "include" -> included += parser.getAttributeValue(null, "domain")
            }
        }
        val every = setOf("root", "file", "database", "sharedpref", "external")
        assertEquals(every, excluded["cloud-backup"])
        assertEquals(every, excluded["device-transfer"])
        assertEquals(emptyList<String>(), included)
    }

    @Test
    fun item312_noWidgetNoShortcutsNoShareTarget() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        val launchers = pm.queryIntentActivities(launcher, PackageManager.GET_META_DATA)
        assertEquals(listOf(MainActivity::class.java.name), launchers.map { it.activityInfo.name })
        assertFalse(
            "launcher shortcuts declared",
            launchers.single().activityInfo.metaData?.containsKey("android.app.shortcuts") ?: false,
        )

        assertEquals(listOf(BootReceiver::class.java.name), receiverNames(Intent(Intent.ACTION_BOOT_COMPLETED)))
        assertEquals(emptyList<String>(), receiverNames(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)))

        for (action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
            val share = Intent(action).setType("text/plain").setPackage(context.packageName)
            assertEquals(action, emptyList<Any>(), pm.queryIntentActivities(share, 0))
        }
    }

    @Test
    fun item312_sourceHasNoShareSendOrShortcutCalls() {
        val sources = sourceFiles("")
        assertEquals("the scan can see a call that is there", listOf("BootReceiver.kt"), sources.matching(Regex("""ACTION_BOOT_COMPLETED""")))
        val absent = Regex("""ACTION_SEND|createChooser|ShareCompat|ShortcutManager|ShortcutInfo|AppWidgetProvider|GlanceAppWidget""")
        assertEquals(emptyList<String>(), sources.matching(absent))
    }

    @Test
    fun item218_noScreenCallsFolderOrTagRenameOrDelete() {
        val manage = Regex("""\b(renameFolder|deleteFolder|renameTag|deleteTag)\b""")
        assertEquals(
            "the repository still offers them, so the scan can see them",
            listOf("PageRepository.kt"),
            sourceFiles("data").filterKeys { it.name == "PageRepository.kt" }.matching(manage),
        )
        val ui = sourceFiles("ui")
        assertEquals(emptyList<String>(), ui.matching(manage))
        assertEquals("no screen goes round the repository to the DAO", emptyList<String>(), ui.matching(Regex("""pageDao\(\)""")))
    }
}
