package org.bibletranslationtools.kotlinscripturealignment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

class BurritoAudioAlignmentTest {

    @Test
    fun testLoadAudioExample1() {
        val resource = javaClass.classLoader.getResource("audio-example1.json")
        val file = File(resource!!.file)
        val alignment = BurritoAudioAlignment.load(file)

        assertEquals(FormatType.ALIGNMENT, alignment.format)
        assertEquals("0.3", alignment.version)
        assertEquals("audio-reference", alignment.type)
        assertEquals(2, (alignment.documents as? List<DocumentReference>)?.size)
        assertEquals("vtt-timecode", (alignment.documents as? List<DocumentReference>)?.get(0)?.scheme)
        assertEquals("ephesians_example_with_footnotes.mp3", (alignment.documents as? List<DocumentReference>)?.get(0)?.docid)
        assertEquals("u23003", (alignment.documents as? List<DocumentReference>)?.get(1)?.scheme)
        assertEquals(null, (alignment.documents as? List<DocumentReference>)?.get(1)?.docid)
        assertEquals(52, alignment.records.size)
    }

    @Test
    fun testLoadAudioExample2() {
        val resource = javaClass.classLoader.getResource("audio-example2.json")
        val file = File(resource!!.file)
        val alignment = BurritoAudioAlignment.load(file)

        assertEquals(FormatType.ALIGNMENT, alignment.format)
        assertEquals("0.3", alignment.version)
        assertEquals("audio-reference", alignment.type)
        // Documents should be a Map for audio-example2.json
        val documentsMap = alignment.documents as? Map<String, DocumentReference>
        assertNotNull(documentsMap)
        assertEquals(2, documentsMap?.size)
        assertEquals("vtt-timestamp", (alignment.documents as? Map<String, DocumentReference>)?.get("timecode")?.scheme)
        assertEquals("ephesians_example_with_footnotes.mp3", (alignment.documents as? Map<String, DocumentReference>)?.get("timecode")?.docid)
        assertEquals("u23003", (alignment.documents as? Map<String, DocumentReference>)?.get("text-reference")?.scheme)
        assertEquals(null, (alignment.documents as? Map<String, DocumentReference>)?.get("text-reference")?.docid)
        assertNull(alignment.roles)
        // Check that there are more than 2 records
        assertTrue(alignment.records.size > 2)
        assertEquals(listOf(listOf("00:00:00.000 --> 00:00:01.927"), listOf("en+ulb.EPH:0")), alignment.records[0].references)
    }
}
