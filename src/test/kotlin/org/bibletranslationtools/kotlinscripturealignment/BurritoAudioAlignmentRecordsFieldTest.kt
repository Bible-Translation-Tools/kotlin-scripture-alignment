package org.bibletranslationtools.kotlinscripturealignment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import org.bibletranslationtools.kotlinscripturealignment.model.BurritoAudioAlignment
import org.bibletranslationtools.kotlinscripturealignment.model.Record

class BurritoAudioAlignmentRecordsFieldTest {

    @Test
    fun testEmptyRecordsList() {
        val json = """
            {
                "format": "alignment",
                "version": "0.3",
                "type": "audio-reference",
                "documents": [],
                "records": []
            }
        """
        val tempFile = File.createTempFile("empty_records_list", ".json")
        tempFile.writeText(json)

        val alignment = BurritoAudioAlignment.load(tempFile)
        assertNotNull(alignment.records)
        assertEquals(0, alignment.records.size)

        tempFile.delete()
    }

    @Test
    fun testRecordsWithCueAndTextReference() {
        val json = """
            {
                "format": "alignment",
                "version": "0.3",
                "type": "audio-reference",
                "documents": [],
                "records": [
                    {
                        "cue": ["00:00:00.000 --> 00:00:01.000"],
                        "text-reference": ["text-ref-1"]
                    }
                ]
            }
        """
        val tempFile = File.createTempFile("records_cue_text_ref", ".json")
        tempFile.writeText(json)

        val alignment = BurritoAudioAlignment.load(tempFile)
        assertEquals(1, alignment.records.size)
        val record = alignment.records.first()
        assertEquals(listOf("00:00:00.000 --> 00:00:01.000"), record.cue)
        assertEquals(listOf("text-ref-1"), record.textReference)
        assertTrue(record.references.isEmpty())

        tempFile.delete()
    }

    @Test
    fun testRecordsWithReferences() {
        val json = """
            {
                "format": "alignment",
                "version": "0.3",
                "type": "audio-reference",
                "documents": [],
                "records": [
                    {
                        "references": [["00:00:00.000 --> 00:00:01.000"], ["text-ref-1"]]
                    }
                ]
            }
        """
        val tempFile = File.createTempFile("records_references", ".json")
        tempFile.writeText(json)

        val alignment = BurritoAudioAlignment.load(tempFile)
        assertEquals(1, alignment.records.size)
        val record = alignment.records.first()
        assertEquals(listOf(listOf("00:00:00.000 --> 00:00:01.000"), listOf("text-ref-1")), record.references)
        assertEquals(null, record.cue)
        assertEquals(null, record.textReference)

        tempFile.delete()
    }

    @Test
    fun testRecordsWithDiverseMetaData() {
        val json = """
            {
                "format": "alignment",
                "version": "0.3",
                "type": "audio-reference",
                "documents": [],
                "records": [
                    {
                        "cue": ["00:00:00.000 --> 00:00:01.000"],
                        "text-reference": ["text-ref-1"],
                        "meta": {
                            "creator": "test-tool",
                            "duration": 1.0,
                            "is_verified": true,
                            "tags": ["tag1", "tag2"],
                            "nested": {"key": "value"}
                        }
                    }
                ]
            }
        """
        val tempFile = File.createTempFile("records_diverse_meta", ".json")
        tempFile.writeText(json)

        val alignment = BurritoAudioAlignment.load(tempFile)
        assertEquals(1, alignment.records.size)
        val record = alignment.records.first()
        assertNotNull(record.meta)
        assertEquals("test-tool", (record.meta as Map<String, Any>)["creator"])
        assertEquals(1.0, (record.meta as Map<String, Any>)["duration"])
        assertEquals(true, (record.meta as Map<String, Any>)["is_verified"])
        assertTrue((record.meta as Map<String, Any>)["tags"] is List<*>)
        assertEquals(listOf("tag1", "tag2"), (record.meta as Map<String, Any>)["tags"])
        assertTrue((record.meta as Map<String, Any>)["nested"] is Map<*, *>)
        assertEquals(mapOf("key" to "value"), (record.meta as Map<String, Any>)["nested"])

        tempFile.delete()
    }
}
