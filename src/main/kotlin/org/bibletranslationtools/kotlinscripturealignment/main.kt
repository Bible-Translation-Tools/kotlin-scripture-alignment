package org.bibletranslationtools.kotlinscripturealignment

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.bibletranslationtools.vtt.*
import java.io.File
import java.util.regex.Matcher

// Custom Deserializer for the 'documents' field
class DocumentsDeserializer @JvmOverloads constructor(vc: Class<*>? = null) : StdDeserializer<Any>(vc) {
    override fun deserialize(jp: com.fasterxml.jackson.core.JsonParser, ctxt: DeserializationContext): Any? {
        val node: JsonNode = jp.codec.readTree(jp)
        println("DocumentsDeserializer: node type is ${node.nodeType}")
        return if (node.isArray) {
            println("DocumentsDeserializer: node is Array")
            val listType = ctxt.typeFactory.constructCollectionType(List::class.java, DocumentReference::class.java)
            val listValue = jp.codec.readValue(node.traverse(), listType) as List<DocumentReference>
            println("DocumentsDeserializer: deserialized listValue = $listValue")
            listValue
        } else if (node.isObject) {
            println("DocumentsDeserializer: node is Object")
            val mapType = ctxt.typeFactory.constructMapLikeType(Map::class.java, String::class.java, DocumentReference::class.java)
            val mapValue = jp.codec.readValue<Map<String, DocumentReference>>(node.traverse(), mapType)
            println("DocumentsDeserializer: deserialized mapValue = $mapValue")
            mapValue
        } else {
            println("DocumentsDeserializer: node is neither Array nor Object")
            null
        }
    }
}

data class BurritoAudioAlignment(
    @JsonProperty("format")
    var format: FormatType = FormatType.ALIGNMENT,

    @JsonProperty("version")
    var version: String = "0.3",

    @JsonProperty("type")
    var type: String,

    @JsonDeserialize(using = DocumentsDeserializer::class)
    @JsonProperty("documents")
    var documents: Any = Any(),

    @JsonProperty("roles")
    var roles: List<String>? = null,

    @JsonProperty("records")
    var records: List<Record> = listOf()
) {

    @JsonIgnore
    var alignmentFile: File? = null

    fun audioFileName(): String {
        val timecodeDocList = (documents as? List<DocumentReference>)?.firstOrNull { it.scheme == "vtt-timecode" }
        if (timecodeDocList != null) return timecodeDocList.docid ?: timecodeDocList.scheme ?: ""

        val timecodeDocMap = (documents as? Map<String, DocumentReference>)?.get("timecode")
        if (timecodeDocMap != null) return timecodeDocMap.docid ?: timecodeDocMap.scheme ?: ""

        return ""
    }

    @JsonIgnore
    fun getVttCues(): List<WebVttDocument.WebVttCueContent> {
        val cues = records.map { record ->
            record.toWebVttCueContent(this.roles)!!
        }.toMutableList()

        cues.sortWith { first, second ->
            val startIsSame = first.startTimeUs == second.startTimeUs
            val endIsGreater = first.endTimeUs > second.endTimeUs
            when {
                startIsSame && endIsGreater -> -1 // the greater end should come first
                startIsSame && !endIsGreater -> 1
                else -> first.startTimeUs.compareTo(second.startTimeUs)
            }
        }
        return cues
    }

    fun write(outFile: File) {
        val mapper = ObjectMapper().registerKotlinModule()
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
        records = content.map {
            val cue = it.cue
            val timecodeRef = listOf("${timestamp(cue.startTimeUs)} --> ${timestamp(cue.endTimeUs)}")
            val textRef = listOf(it.tag)
            val orderedRefs = if (this.roles?.getOrNull(0) == "timecode") listOf(timecodeRef, textRef) else listOf(textRef, timecodeRef)

            val record = Record(orderedRefs, mapOf("creator" to "kotlin-aligner"))
            println(record)
            record
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

            val alignment = BurritoAudioAlignment(
                FormatType.ALIGNMENT,
                "0.3",
                "audio-reference",
                listOf(
                    DocumentReference("vtt-timecode", audioFile.name),
                    DocumentReference("u23003", null)
                ),
                listOf("timecode", "text-reference"),
                listOf()
            )

            val mapper = ObjectMapper().registerKotlinModule()
            timingFile.outputStream().use {
                mapper.writeValue(it, alignment)
            }
            alignment.alignmentFile = timingFile // Assign the file to the returned alignment
            return alignment
        }

        fun load(timingFile: File): BurritoAudioAlignment {
            val mapper = ObjectMapper().registerKotlinModule()
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val timing = mapper.readValue(timingFile.readText(), BurritoAudioAlignment::class.java)
            timing.alignmentFile = timingFile
            return timing
        }

        fun load(timing: String): BurritoAudioAlignment {
            val mapper = ObjectMapper().registerKotlinModule()
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val deserializedTiming = mapper.readValue(timing, BurritoAudioAlignment::class.java)
            return deserializedTiming
        }
    }
}

class DocumentReference(
    @JsonProperty("scheme")
    val scheme: String,
    @JsonProperty("docid")
    val docid: String? = null
)

// const val CUE_HEADER_PATTERN: Pattern = Pattern.compile("^(\\S+)\\s+-->\\s+(\\S+)(.*)?$")
class Record(
    @JsonProperty("references")
    val references: List<List<String>> = listOf(),
    @JsonProperty("meta")
    val meta: Map<String, Any>? = null
) {
    fun toWebVttCueContent(roles: List<String>?): WebVttDocument.WebVttCueContent? {
        val timecodeIndex = roles?.indexOf("timecode") ?: -1
        val textReferenceIndex = roles?.indexOf("text-reference") ?: -1

        if (timecodeIndex == -1 || textReferenceIndex == -1) {
            return null // Roles not found, cannot determine which reference is which
        }

        val timestamp = references.getOrNull(timecodeIndex)?.firstOrNull()
        val reference = references.getOrNull(textReferenceIndex)?.firstOrNull()

        if (timestamp == null || reference == null) {
            return null
        }

        val cue = Cue.Builder().build()
        var cueHeaderMatcher: Matcher = CUE_HEADER_PATTERN.matcher(timestamp)
        try {
            cueHeaderMatcher.matches()
            // Parse the cue start and end times.
            val startTimeUs = WebvttParserUtil.parseTimestampUs(checkNotNull(cueHeaderMatcher.group(1)))
            val endTimeUs = WebvttParserUtil.parseTimestampUs(checkNotNull(cueHeaderMatcher.group(2)))

            val wvc = WebVttCue(WebvttCueInfo(cue, startTimeUs, endTimeUs))
            return WebVttDocument.WebVttCueContent(reference, reference, wvc)
        } catch (e: NumberFormatException) {
            return null
        }
    }

    fun addCue(
        cues: MutableList<WebVttCue>,
        startTimeUs: Long,
        endTimeUs: Long,
        tag: String,
        content: String
    ): WebVttDocument.WebVttCueContent {
        val cue = Cue.Builder().build()
        val wvc = WebVttCue(WebvttCueInfo(cue, startTimeUs, endTimeUs))
        cues.sortWith { first, second ->
            val startIsSame = first.startTimeUs == second.startTimeUs
            val endIsGreater = first.endTimeUs > second.endTimeUs
            when {
                startIsSame && endIsGreater -> -1 // the greater end should come first
                startIsSame && !endIsGreater -> 1
                else -> first.startTimeUs.compareTo(second.startTimeUs)
            }
        }
        return WebVttDocument.WebVttCueContent(tag, content, wvc)
    }
}

enum class FormatType(private val value: String) {
    ALIGNMENT("alignment");

    override fun toString(): String {
        return this.value
    }

    @JsonValue
    fun value(): String {
        return this.value
    }

    companion object {
        private val CONSTANTS: MutableMap<String, FormatType> = HashMap()

        init {
            for (c in values()) {
                FormatType.CONSTANTS[c.value] = c
            }
        }

        @JsonCreator
        fun fromValue(value: String): FormatType {
            val constant = FormatType.CONSTANTS[value]
            requireNotNull(constant) { value }
            return constant
        }
    }
}