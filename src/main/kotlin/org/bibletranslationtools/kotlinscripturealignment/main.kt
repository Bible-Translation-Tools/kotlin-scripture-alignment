package org.bibletranslationtools.kotlinscripturealignment

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.annotation.JsonInclude
// import org.bibletranslationtools.vtt.Cue
// import org.bibletranslationtools.vtt.WebVttCue
// import org.bibletranslationtools.vtt.WebVttDocument
// import org.bibletranslationtools.vtt.WebvttParserUtil
// import org.bibletranslationtools.vtt.WebVttCueInfo
import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern
import com.fasterxml.jackson.databind.SerializationFeature

sealed interface Documents
data class DocumentsList(val list: List<DocumentReference>) : Documents
data class DocumentsMap(val map: Map<String, DocumentReference>) : Documents

data class Group(
    @JsonProperty("documents")
    val documents: Documents? = null,
    @JsonProperty("records")
    val records: List<Record> = listOf()
)

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
        mapper.registerModule(module)
        
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false)
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)

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
            mapper.registerModule(module)

            mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false)
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)

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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val docid: String? = null
)

class BurritoAudioAlignmentDeserializer @JvmOverloads constructor(vc: Class<*>? = null) : StdDeserializer<BurritoAudioAlignment>(vc) {
    override fun deserialize(jp: com.fasterxml.jackson.core.JsonParser, ctxt: DeserializationContext): BurritoAudioAlignment? {
        val node: JsonNode = jp.codec.readTree(jp)

        val format = node.get("format")?.asText()?.let { FormatType.fromValue(it) } ?: FormatType.ALIGNMENT
        val version = node.get("version")?.asText() ?: "0.3"
        val type = node.get("type")?.asText() ?: throw MismatchedInputException.from(jp, BurritoAudioAlignment::class.java, "Missing required field: type")

        val documentsNode = node.get("documents")
        val documents: Documents? = if (documentsNode != null && !documentsNode.isNull) {
            if (documentsNode.isArray) {
                val listType = ctxt.typeFactory.constructCollectionType(List::class.java, DocumentReference::class.java)
                DocumentsList(jp.codec.readValue(documentsNode.traverse(), listType))
            } else if (documentsNode.isObject) {
                val mapEntries = mutableMapOf<String, DocumentReference>()
                documentsNode.fields().forEach { (key, valueNode) ->
                    mapEntries[key] = jp.codec.treeToValue(valueNode, DocumentReference::class.java)
                }
                DocumentsMap(mapEntries)
            } else {
                null
            }
        } else {
            null
        }

        val roles = node.get("roles")?.map { it.asText() }

        val recordsNode = node.get("records")
        val records: List<Record> = if (recordsNode != null && recordsNode.isArray) {
            val listType = ctxt.typeFactory.constructCollectionType(List::class.java, Record::class.java)
            jp.codec.readValue(recordsNode.traverse(), listType)
        } else {
            listOf()
        }

        val groupsNode = node.get("groups")
        val groups: List<Group>? = if (groupsNode != null && groupsNode.isArray) {
            val groupList = mutableListOf<Group>()
            groupsNode.forEach { groupNode ->
                // Manually deserialize each Group object within the groups array
                val groupDocumentsNode = groupNode.get("documents")
                val groupRecordsNode = groupNode.get("records")

                val groupDocuments: Documents? = if (groupDocumentsNode != null && !groupDocumentsNode.isNull) {
                    if (groupDocumentsNode.isArray) {
                        val listType = ctxt.typeFactory.constructCollectionType(List::class.java, DocumentReference::class.java)
                        DocumentsList(jp.codec.readValue(groupDocumentsNode.traverse(), listType))
                    } else if (groupDocumentsNode.isObject) {
                        val mapEntries = mutableMapOf<String, DocumentReference>()
                        groupDocumentsNode.fields().forEach { (key, valueNode) ->
                            mapEntries[key] = jp.codec.treeToValue(valueNode, DocumentReference::class.java)
                        }
                        DocumentsMap(mapEntries)
                    } else {
                        null
                    }
                } else {
                    null
                }

                val groupRecords: List<Record> = if (groupRecordsNode != null && groupRecordsNode.isArray) {
                    if (groupRecordsNode.isEmpty) {
                        listOf()
                    } else {
                        val listType = ctxt.typeFactory.constructCollectionType(List::class.java, Record::class.java)
                        jp.codec.readValue(groupRecordsNode.traverse(), listType)
                    }
                } else {
                    listOf()
                }
                groupList.add(Group(groupDocuments, groupRecords))
            }
            groupList
        } else {
            null
        }

        return BurritoAudioAlignment(format, version, type, documents, roles, records, groups)
    }
}

class DocumentsSerializer @JvmOverloads constructor(t: Class<Documents>? = null) : StdSerializer<Documents>(t) {
    override fun serialize(value: Documents?, gen: JsonGenerator, provider: SerializerProvider) {
        if (value == null) {
            gen.writeNull()
            return
        }
        when (value) {
            is DocumentsList -> gen.writeObject(value.list)
            is DocumentsMap -> {
                gen.writeStartObject()
                value.map.forEach { (key, docRef) ->
                    gen.writeFieldName(key)
                    gen.writeStartObject()
                    gen.writeStringField("scheme", docRef.scheme)
                    if (docRef.docid != null) {
                        gen.writeStringField("docid", docRef.docid)
                    }
                    gen.writeEndObject()
                }
                gen.writeEndObject()
            }
        }
    }
}

class BurritoAudioAlignmentSerializer @JvmOverloads constructor(t: Class<BurritoAudioAlignment>? = null) : StdSerializer<BurritoAudioAlignment>(t) {
    override fun serialize(value: BurritoAudioAlignment?, gen: JsonGenerator, provider: SerializerProvider) {
        if (value == null) {
            gen.writeNull()
            return
        }

        gen.writeStartObject()
        gen.writeStringField("format", value.format.value())
        gen.writeStringField("version", value.version)
        gen.writeStringField("type", value.type)

        // Conditionally serialize documents and records based on whether groups is present
        if (value.groups.isNullOrEmpty()) {
            if (value.documents != null) {
                gen.writeFieldName("documents")
                gen.writeObject(value.documents) // This will use DocumentsSerializer
            }
            if (value.roles != null) {
                gen.writeFieldName("roles")
                gen.writeObject(value.roles)
            }
            if (value.records.isNotEmpty()) {
                gen.writeFieldName("records")
                gen.writeObject(value.records)
            }
        } else {
            // If groups is present, omit top-level documents, roles, and records
            gen.writeFieldName("groups")
            gen.writeObject(value.groups)
        }
        gen.writeEndObject()
    }
}

class GroupSerializer @JvmOverloads constructor(t: Class<Group>? = null) : StdSerializer<Group>(t) {
    override fun serialize(value: Group?, gen: JsonGenerator, provider: SerializerProvider) {
        if (value == null) {
            gen.writeNull()
            return
        }

        gen.writeStartObject()
        if (value.documents != null) {
            gen.writeFieldName("documents")
            gen.writeObject(value.documents)
        }
        // This condition 'value.records != null' is always true because records is List<Record> = listOf()
        // and `Group`'s `records` is not nullable.
        // We need to write it if it's not empty, or if `WRITE_EMPTY_JSON_ARRAYS` is true (which we set to false globally).
        // So, only write if not empty, which `isNotEmpty()` already handles.
        if (value.records.isNotEmpty()) {
            gen.writeFieldName("records")
            gen.writeObject(value.records)
        }
        gen.writeEndObject()
    }
}

// Original Record definition, kept for clarity of what BurritoAudioAlignmentDeserializer needs
class Record(
    @JsonProperty("cue")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val cue: List<String>? = null,

    @JsonProperty("timecode")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val timecode: List<String>? = null,

    @JsonProperty("text-reference")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val textReference: List<String>? = null,

    @JsonProperty("references")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val references: List<List<String>> = listOf(),
    @JsonProperty("meta")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val meta: Map<String, Any>? = null
) {
    // Removed vtt-related functions
}