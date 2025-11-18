package org.bibletranslationtools.kotlinscripturealignment

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.bibletranslationtools.kotlinscripturealignment.model.BurritoAudioAlignment
import org.bibletranslationtools.kotlinscripturealignment.model.Documents
import org.bibletranslationtools.kotlinscripturealignment.model.FormatType
import org.bibletranslationtools.kotlinscripturealignment.serializers.BurritoAudioAlignmentSerializer
import org.bibletranslationtools.kotlinscripturealignment.serializers.DocumentsSerializer
import org.bibletranslationtools.kotlinscripturealignment.serializers.GroupSerializer
import org.bibletranslationtools.kotlinscripturealignment.serializers.RecordSerializer
import org.bibletranslationtools.vtt.Cue
import org.bibletranslationtools.vtt.WebVttCue
import org.bibletranslationtools.vtt.WebVttDocument
import org.bibletranslationtools.vtt.WebvttParserUtil
import org.bibletranslationtools.vtt.WebvttCueInfo
import org.bibletranslationtools.vtt.WebvttParserUtil.parseTimestampUs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BurritoAudioAlignmentVttApiTest {

    // data class TestWebVttCueInfo(val cue: Cue, val startTimeUs: Long, val endTimeUs: Long)

    private val mapper = ObjectMapper().registerKotlinModule().apply {
        configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false)
        setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        val module = com.fasterxml.jackson.databind.module.SimpleModule()
        module.addSerializer(Documents::class.java, DocumentsSerializer())
        module.addSerializer(BurritoAudioAlignment::class.java, BurritoAudioAlignmentSerializer())
        module.addSerializer(org.bibletranslationtools.kotlinscripturealignment.model.Group::class.java, GroupSerializer())
        module.addSerializer(org.bibletranslationtools.kotlinscripturealignment.model.Record::class.java, RecordSerializer())
        registerModule(module)
    }

    @Test
    fun testGetVttCuesFromAudioExample1() {
        val timingFile = File("src/test/resources/audio-example1.json")
        val alignment = BurritoAudioAlignment.Companion.load(timingFile)
        val vttCues = alignment.getVttCues()

        assertNotNull(vttCues)
        assertTrue(vttCues.isNotEmpty())
        assertEquals(52, vttCues.size) // As per previous tests

        // Assert specific cue content for the first few cues
        val firstCue = vttCues[0]
        assertEquals(0L, firstCue.cue.startTimeUs)
        assertEquals(1927000L, firstCue.cue.endTimeUs)
        assertEquals("en+ulb.EPH:0", firstCue.tag)
        assertEquals("en+ulb.EPH:0", firstCue.content)

        val secondCue = vttCues[1]
        assertEquals(1927000L, secondCue.cue.startTimeUs)
        assertEquals(3756000L, secondCue.cue.endTimeUs)
        assertEquals("en+ulb.EPH 1:0",secondCue.tag)
        assertEquals("en+ulb.EPH 1:0",secondCue.content)
    }

    @Test
    fun testSetRecordsFromVttCueContent() {
        // Create some dummy VTT cues
        val vttCues = listOf(
            WebVttDocument.WebVttCueContent(
                tag = "v1",
                content = "v1",
                cue = WebVttCue(WebvttCueInfo(Cue.Builder().build(), 0L, 1000000L))
            ),
            WebVttDocument.WebVttCueContent(
                tag = "v2",
                content = "v2",
                cue = WebVttCue(WebvttCueInfo(Cue.Builder().build(), 1000000L, 2000000L))
            )
        )

        val alignment = BurritoAudioAlignment(FormatType.ALIGNMENT, "0.3", "audio-reference", null, listOf(), listOf(), null)
        alignment.setRecordsFromVttCueContent(vttCues)

        assertNotNull(alignment.records)
        assertEquals(2, alignment.records.size)

        val firstRecord = alignment.records[0]
        assertEquals(listOf("00:00:00.000 --> 00:00:01.000"), firstRecord.cue)
        assertEquals(listOf("v1"), firstRecord.textReference)
        assertEquals(mapOf("creator" to "kotlin-aligner"), firstRecord.meta)

        val secondRecord = alignment.records[1]
        assertEquals(listOf("00:00:01.000 --> 00:00:02.000"), secondRecord.cue)
        assertEquals(listOf("v2"), secondRecord.textReference)
        assertEquals(mapOf("creator" to "kotlin-aligner"), secondRecord.meta)
    }

    @Test
    fun testVttRoundTripForAudioExample1() {
        val timingFile = File("src/test/resources/audio-example1.json")
        val originalAlignment = BurritoAudioAlignment.Companion.load(timingFile)

        // Normalize the original alignment to the cue/text-reference format
        val normalizedOriginalAlignment = BurritoAudioAlignment(
            originalAlignment.format,
            originalAlignment.version,
            originalAlignment.type,
            originalAlignment.documents,
            originalAlignment.roles,
            listOf(), // Start with empty records
            originalAlignment.groups
        )
        normalizedOriginalAlignment.setRecordsFromVttCueContent(originalAlignment.getVttCues())

        // 1. Get VTT cues from original alignment
        val vttCues = originalAlignment.getVttCues()

        // 2. Create a new alignment and set records from VTT cues
        val newAlignment = BurritoAudioAlignment(originalAlignment.format, originalAlignment.version, originalAlignment.type, originalAlignment.documents, originalAlignment.roles, listOf(), originalAlignment.groups)
        newAlignment.setRecordsFromVttCueContent(vttCues)

        // 3. Serialize both to JSON and compare
        val originalJsonNode = mapper.readTree(mapper.writeValueAsString(normalizedOriginalAlignment))
        val newJsonNode = mapper.readTree(mapper.writeValueAsString(newAlignment))

        assertEquals(originalJsonNode, newJsonNode)
    }

    @Test
    fun testVttRoundTripForAudioExample3() {
        val timingFile = File("src/test/resources/audio-example3.json")
        val originalAlignment = BurritoAudioAlignment.Companion.load(timingFile)

        // Normalize the original alignment to the cue/text-reference format
        val normalizedOriginalAlignment = BurritoAudioAlignment(
            originalAlignment.format,
            originalAlignment.version,
            originalAlignment.type,
            originalAlignment.documents,
            originalAlignment.roles,
            listOf(), // Start with empty records
            originalAlignment.groups
        )
        normalizedOriginalAlignment.setRecordsFromVttCueContent(originalAlignment.getVttCues())

        // 1. Get VTT cues from original alignment
        val vttCues = originalAlignment.getVttCues()

        // 2. Create a new alignment and set records from VTT cues
        val newAlignment = BurritoAudioAlignment(originalAlignment.format, originalAlignment.version, originalAlignment.type, originalAlignment.documents, originalAlignment.roles, listOf(), originalAlignment.groups)
        newAlignment.setRecordsFromVttCueContent(vttCues)

        // 3. Serialize both to JSON and compare
        val originalJsonNode = mapper.readTree(mapper.writeValueAsString(normalizedOriginalAlignment))
        val newJsonNode = mapper.readTree(mapper.writeValueAsString(newAlignment))

        assertEquals(originalJsonNode, newJsonNode)
    }

    @Test
    fun testVttRoundTripForApmExample() {
        val timingFile = File("src/test/resources/apm_example.json")
        val originalAlignment = BurritoAudioAlignment.Companion.load(timingFile)

        // Normalize the original alignment to the cue/text-reference format
        val normalizedOriginalAlignment = BurritoAudioAlignment(
            originalAlignment.format,
            originalAlignment.version,
            originalAlignment.type,
            originalAlignment.documents,
            originalAlignment.roles,
            listOf(), // Start with empty records
            originalAlignment.groups
        )
        normalizedOriginalAlignment.setRecordsFromVttCueContent(originalAlignment.getVttCues())

        // 1. Get VTT cues from original alignment
        val vttCues = originalAlignment.getVttCues()

        // 2. Create a new alignment and set records from VTT cues
        val newAlignment = BurritoAudioAlignment(originalAlignment.format, originalAlignment.version, originalAlignment.type, originalAlignment.documents, originalAlignment.roles, listOf(), originalAlignment.groups)
        newAlignment.setRecordsFromVttCueContent(vttCues)

        // 3. Serialize both to JSON and compare
        val originalJsonNode = mapper.readTree(mapper.writeValueAsString(normalizedOriginalAlignment))
        val newJsonNode = mapper.readTree(mapper.writeValueAsString(newAlignment))

        assertEquals(originalJsonNode, newJsonNode)
    }
}
