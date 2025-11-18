package org.bibletranslationtools.kotlinscripturealignment.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.annotation.JsonInclude
import org.bibletranslationtools.kotlinscripturealignment.deserializers.BurritoAudioAlignmentDeserializer
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
import java.io.File

data class BurritoAudioAlignment(
    @JsonProperty("format")
    var format: FormatType = FormatType.ALIGNMENT,

    @JsonProperty("version")
    var version: String = "0.3",

    @JsonProperty("type")
    var type: String,

    @JsonProperty("documents")
    var documents: Documents? = null,

    @JsonProperty("roles")
    var roles: List<String>? = null,

    @JsonProperty("records")
    var records: List<Record> = listOf(),

    @JsonProperty("groups")
    var groups: List<Group>? = null
) {

    @JsonIgnore
    var alignmentFile: File? = null

    fun audioFileName(): String {
        if (groups != null) {
            for (group in groups!!) {
                val currentGroupDocuments = group.documents
                when (currentGroupDocuments) {
                    is DocumentsList -> {
                        val timecodeDoc = currentGroupDocuments.list.firstOrNull { it.scheme == "vtt-timecode" }
                        if (timecodeDoc?.docid != null) return timecodeDoc.docid
                    }
                    is DocumentsMap -> {
                        val timecodeDoc = currentGroupDocuments.map["timecode"]
                        if (timecodeDoc?.docid != null) return timecodeDoc.docid
                    }
                    else -> { /* Handle null or other Documents types if necessary */} // Added else branch
                }
            }
        } else if (documents != null) {
            val currentDocuments = documents
            when (currentDocuments) {
                is DocumentsList -> {
                    val timecodeDoc = currentDocuments.list.firstOrNull { it.scheme == "vtt-timecode" }
                    if (timecodeDoc?.docid != null) return timecodeDoc.docid
                }
                is DocumentsMap -> {
                    val timecodeDoc = currentDocuments.map["timecode"]
                    if (timecodeDoc?.docid != null) return timecodeDoc.docid
                }
                else -> { /* Handle null or other Documents types if necessary */} // Added else branch
            }
        }
        return ""
    }

    @JsonIgnore
    fun getVttCues(): List<WebVttDocument.WebVttCueContent> {
        val cues = records.map { record ->
            record.toWebVttCueContent(this.roles)!!
        }.toMutableList()

        cues.sortWith { first, second ->
            val startIsSame = first.cue.startTimeUs == second.cue.startTimeUs
            val endIsGreater = first.cue.endTimeUs > second.cue.endTimeUs
            when {
                startIsSame && endIsGreater -> -1 // the greater end should come first
                startIsSame && !endIsGreater -> 1
                else -> first.cue.startTimeUs.compareTo(second.cue.startTimeUs)
            }
        }
        return cues
    }

    fun write(outFile: File) {
        val mapper = ObjectMapper().registerKotlinModule()
        val module = SimpleModule()
        module.addSerializer(Documents::class.java, DocumentsSerializer())
        module.addSerializer(BurritoAudioAlignment::class.java, BurritoAudioAlignmentSerializer())
        module.addSerializer(Group::class.java, GroupSerializer())
        module.addSerializer(Record::class.java, RecordSerializer())
        mapper.registerModule(module)
        
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false)
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)

        outFile.outputStream().use {
            mapper.writeValue(it, this)
        }
    }

    fun update() {
        alignmentFile?.let {
            write(it)
        }
    }

    fun setRecordsFromVttCueContent(content: List<WebVttDocument.WebVttCueContent>) {
        val newRecords = content.map { vttCueContent ->
            val cueText = listOf("${Companion.timestamp(vttCueContent.cue.startTimeUs)} --> ${Companion.timestamp(vttCueContent.cue.endTimeUs)}")
            val textRef = listOf(vttCueContent.tag)
            Record(cue = cueText, textReference = textRef, meta = mapOf("creator" to "kotlin-aligner"))
        }

        if (!groups.isNullOrEmpty()) {
            // Update records of the first group
            val firstGroup = groups!!.first()
            val updatedGroup = firstGroup.copy(records = newRecords)
            groups = listOf(updatedGroup) + groups!!.drop(1)
        } else {
            // Update top-level records
            records = newRecords
        }
    }

    companion object {

        fun create(audioFile: File, timingFile: File): BurritoAudioAlignment {
            if (!timingFile.exists()) {
                timingFile.createNewFile()
            } else {
                timingFile.delete()
                timingFile.createNewFile()
            }

            val groupDocuments = DocumentsList(
                listOf(
                    DocumentReference("vtt-timecode", audioFile.name),
                    DocumentReference("u23003", null)
                )
            )

            val group = Group(documents = groupDocuments, records = listOf())

            val alignment = BurritoAudioAlignment(
                FormatType.ALIGNMENT,
                "0.3",
                "audio-reference",
                null, // documents is null as it's now in groups
                listOf("timecode", "text-reference"),
                listOf(), // records is null as it's now in groups
                listOf(group)
            )

            val mapper = ObjectMapper().registerKotlinModule()
            val module = SimpleModule()
            module.addSerializer(Documents::class.java, DocumentsSerializer())
            module.addSerializer(BurritoAudioAlignment::class.java, BurritoAudioAlignmentSerializer())
            module.addSerializer(Group::class.java, GroupSerializer())
            module.addSerializer(Record::class.java, RecordSerializer())
            mapper.registerModule(module)

            mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false)
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
            mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)

            timingFile.outputStream().use {
                mapper.writeValue(it, alignment)
            }
            alignment.alignmentFile = timingFile // Assign the file to the returned alignment
            return alignment
        }

        private fun timestamp(timeUs: Long): String {
            val hours = timeUs / 3_600_000_000L
            val minutes = (timeUs % 3_600_000_000L) / 60_000_000L
            val seconds = (timeUs % 60_000_000L) / 1_000_000L
            val milliseconds = (timeUs % 1_000_000L) / 1000L

            return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds)
        }

        fun load(timingFile: File): BurritoAudioAlignment {
            val mapper = ObjectMapper().registerKotlinModule()
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

            val module = SimpleModule()
            module.addDeserializer(BurritoAudioAlignment::class.java, BurritoAudioAlignmentDeserializer())
            // module.addDeserializer(Documents::class.java, DocumentsDeserializer())
            // module.addDeserializer(Group::class.java, GroupDeserializer())
            // module.addDeserializer(Record::class.java, RecordDeserializer())
            mapper.registerModule(module)

            val timing = mapper.readValue(timingFile.readText(), BurritoAudioAlignment::class.java)
            timing.alignmentFile = timingFile
            return timing
        }

        fun load(timing: String): BurritoAudioAlignment {
            val mapper = ObjectMapper().registerKotlinModule()
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

            val module = SimpleModule()
            module.addDeserializer(BurritoAudioAlignment::class.java, BurritoAudioAlignmentDeserializer())
            // module.addDeserializer(Documents::class.java, DocumentsDeserializer())
            // module.addDeserializer(Group::class.java, GroupDeserializer())
            // module.addDeserializer(Record::class.java, RecordDeserializer())
            mapper.registerModule(module)

            val deserializedTiming = mapper.readValue(timing, BurritoAudioAlignment::class.java)
            return deserializedTiming
        }
    }
}
