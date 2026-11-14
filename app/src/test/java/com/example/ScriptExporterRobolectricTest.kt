package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.export.ScriptExporter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScriptExporterRobolectricTest {

    @Test
    fun `buildSuggestedFileName sanitizes title and appends extension`() {
        val pdfName = ScriptExporter.buildSuggestedFileName("Solo Leveling: Ch. 45!", "pdf")
        assertTrue(pdfName.startsWith("Solo_Leveling_Ch_45_"))
        assertTrue(pdfName.endsWith(".pdf"))

        val defaultName = ScriptExporter.buildSuggestedFileName("   ", "txt")
        assertTrue(defaultName.startsWith("Manhwa_Recap_"))
        assertTrue(defaultName.endsWith(".txt"))
    }

    @Test
    fun `writeTxtToUri writes UTF-8 Hindi and English script accurately`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempTxt = File(context.cacheDir, "test_recap.txt")
        val uri = Uri.fromFile(tempTxt)

        val scriptContent = """
            अचानक पूरे कमरे में अंधेरा छा गया, और उसकी आंखें नीली रोशनी से चमक उठीं।
            Before his enemies could react, he dashed forward in a blur of shadow.
        """.trimIndent()

        val outcome = ScriptExporter.writeTxtToUri(
            context = context,
            uri = uri,
            fileName = "test_recap.txt",
            script = scriptContent
        )

        assertEquals("test_recap.txt", outcome.fileName)
        assertTrue(tempTxt.exists())
        assertEquals(scriptContent, tempTxt.readText(Charsets.UTF_8))
        tempTxt.delete()
    }
}
