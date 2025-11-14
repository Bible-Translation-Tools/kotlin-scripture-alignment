package org.bibletranslationtools.kotlinscripturealignment

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.bibletranslationtools.vtt.*
import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern

sealed interface Documents
data class DocumentsList(val list: List<DocumentReference>) : Documents
data class DocumentsMap(val map: Map<String, DocumentReference>) : Documents

class DocumentsDeserializer @JvmOverloads constructor(vc: Class<*>? = null) : StdDeserializer<Documents>(vc) {
    override fun deserialize(jp: com.fasterxml.jackson.core.JsonParser, ctxt: DeserializationContext): Documents? {
        val node: JsonNode = jp.codec.readTree(jp)
        return if (node.isArray) {
            val listType = ctxt.typeFactory.constructCollectionType(List::class.java, DocumentReference::class.java)
            val listValue = jp.codec.readValue(node.traverse(), listType) as List<DocumentReference>
            DocumentsList(listValue)
        } else if (node.isObject) {
            val mapType = ctxt.typeFactory.constructMapLikeType(Map::class.java, String::class.java, DocumentReference::class.java)
            val mapValue = jp.codec.readValue<Map<String, DocumentReference>>(node.traverse(), mapType)
            DocumentsMap(mapValue)
        } else {
            null
        }
    }
}

data class Group(
    @JsonProperty("documents")
    val documents: Documents? = null,
    @JsonProperty("records")
    val records: List<Record> = listOf()
)

// Custom Deserializer for the 'Record' class
data class Record(
    @JsonProperty("cue")
    val cue: List<String>? = null,

    @JsonProperty("timecode")
    val timecode: List<String>? = null,

    @JsonProperty("text-reference")
    val textReference: List<String>? = null,

    @JsonProperty("references")
    val references: List<List<String>> = listOf(),
    @JsonProperty("meta")
    val meta: Map<String, Any>? = null
) {
    fun toWebVttCueContent(roles: List<String>?): WebVttDocument.WebVttCueContent? {
        val rawTimestamp: String?
        val rawReference: String?

        // Prioritize direct fields if available
        rawTimestamp = this.cue?.firstOrNull() ?: this.timecode?.firstOrNull()
        rawReference = this.textReference?.firstOrNull()

        if (rawTimestamp == null || rawReference == null) {
            // Fallback to references list if direct fields are not present
            val timecodeIndex = roles?.indexOf("timecode") ?: -1
            val textReferenceIndex = roles?.indexOf("text-reference") ?: -1

            if (timecodeIndex == -1 || textReferenceIndex == -1) {
                return null // Roles not found, cannot determine which reference is which
            }
            val timestampFromRefs = references.getOrNull(timecodeIndex)?.firstOrNull()
            val referenceFromRefs = references.getOrNull(textReferenceIndex)?.firstOrNull()
            if (timestampFromRefs == null || referenceFromRefs == null) {
                return null
            }
            return parseAndCreateCueContent(timestampFromRefs, referenceFromRefs)
        } else {
            return parseAndCreateCueContent(rawTimestamp, rawReference)
        }
    }

    private fun parseAndCreateCueContent(timestamp: String, reference: String): WebVttDocument.WebVttCueContent? {
        val cue = Cue.Builder().build()
        var cueHeaderMatcher: Matcher = Pattern.compile("^(\\S+)\\s+-->\\s+(\\S+)(.*)?$").matcher(timestamp)
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
                    else -> { /* Handle null or other Documents types if necessary */}
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
                else -> { /* Handle null or other Documents types if necessary */}
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
            // We are setting cue and textReference directly here, not relying on 'references' initially
            Record(cue = timecodeRef, textReference = textRef, meta = mapOf("creator" to "kotlin-aligner"))
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
            timingFile.outputStream().use {
                mapper.writeValue(it, alignment)
            }
            alignment.alignmentFile = timingFile // Assign the file to the returned alignment
            return alignment
        }

        fun load(timingFile: File): BurritoAudioAlignment {
            val mapper = ObjectMapper().registerKotlinModule()
            val module = SimpleModule()
            module.addDeserializer(Documents::class.java, DocumentsDeserializer())
            mapper.registerModule(module)

            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val timing = mapper.readValue(timingFile.readText(), BurritoAudioAlignment::class.java)
            timing.alignmentFile = timingFile
            return timing
        }

        fun load(timing: String): BurritoAudioAlignment {
            val mapper = ObjectMapper().registerKotlinModule()
            val module = SimpleModule()
            module.addDeserializer(Documents::class.java, DocumentsDeserializer())
            mapper.registerModule(module)

            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val deserializedTiming = mapper.readValue(timing, BurritoAudioAlignment::class.java)
            return deserializedTiming
        }
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

class DocumentReference(
    @JsonProperty("scheme")
    val scheme: String,
    @JsonProperty("docid")
    val docid: String? = null
)