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

    // @JsonIgnore
    // fun getVttCues(): List<WebVttDocument.WebVttCueContent> {
    //     val cues = records.map { record ->
    //         record.toWebVttCueContent(this.roles)!!
    //     }.toMutableList()
    //
    //     cues.sortWith { first, second ->
    //         val startIsSame = first.startTimeUs == second.startTimeUs
    //         val endIsGreater = first.endTimeUs > second.endTimeUs
    //         when {
    //             startIsSame && endIsGreater -> -1 // the greater end should come first
    //             startIsSame && !endIsGreater -> 1
    //             else -> first.startTimeUs.compareTo(second.startTimeUs)
    //         }
    //     }
    //     return cues
    // }

    fun write(outFile: File) {
        val mapper = ObjectMapper().registerKotlinModule()
        val module = SimpleModule()
        module.addSerializer(Documents::class.java, DocumentsSerializer())
        module.addSerializer(BurritoAudioAlignment::class.java, BurritoAudioAlignmentSerializer())
        module.addSerializer(Group::class.java, GroupSerializer())
        module.addSerializer(Record::class.java, RecordSerializer())
        mapper.registerModule(module)
        
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false)
        // mapper.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false) // Removed
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

    // fun setRecordsFromVttCueContent(content: List<WebVttDocument.WebVttCueContent>) {
    //     records = content.map {
    //         // val cue = it.cue
    //         // val timecodeRef = listOf("${Companion.timestamp(cue.startTimeUs)} --> ${Companion.timestamp(cue.endTimeUs)}")
    //         // val textRef = listOf(it.tag)
    //         // We are setting cue and textReference directly here, not relying on 'references' initially
    //         Record(meta = mapOf("creator" to "kotlin-aligner")) // Simplified for now
    //     }
    // }

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
            // mapper.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false) // Removed
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
            mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)

            timingFile.outputStream().use {
                mapper.writeValue(it, alignment)
            }
            alignment.alignmentFile = timingFile // Assign the file to the returned alignment
            return alignment
        }

        private fun timestamp(timeUs: Long): String {
            // return WebvttParserUtil.toVttTimestamp(timeUs)
            return ""
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
