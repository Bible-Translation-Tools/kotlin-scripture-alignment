package org.bibletranslationtools.kotlinscripturealignment

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.bibletranslationtools.vtt.*
import java.io.File
import java.util.regex.Matcher

class BurritoAudioAlignment(
    @JsonProperty("format")
    var format: FormatType,

    @JsonProperty("version")
    var version: String,

    @JsonProperty("type")
    var type: ReferenceType,

    @JsonProperty("documents")
    var documents: AudioDocument,

    @JsonProperty("records")
    var records: List<Record> = listOf()
) {

    @JsonIgnore
    var alignmentFile: File? = null

    fun audioFileName(): String {
        return documents.timecode.docid
    }

    @JsonIgnore
    fun getVttCues(): List<WebVttDocument.WebVttCueContent> {
        val cues = records.map { record ->
            record.toWebVttCueContent(documents.timecode.scheme)!!
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
            val record = Record(
                listOf("${timestamp(cue.startTimeUs)} --> ${timestamp(cue.endTimeUs)}"),
                listOf(it.tag)
            )
            println(record)
            record
        }
        println(records)
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
                ReferenceType.AUDIO_REFERENCE,
                AudioDocument(
                    Timecode(
                        "vtt-timestamp",
                        audioFile.name
                    ),
                    TextReference("u23003")
                )
            )

            alignment.alignmentFile = timingFile

            val mapper = ObjectMapper().registerKotlinModule()
            timingFile.outputStream().use {
                mapper.writeValue(it, alignment)
            }
            return alignment
        }

        fun load(timingFile: File): BurritoAudioAlignment {
            val mapper = ObjectMapper().registerKotlinModule()
            val timing = mapper.readValue(timingFile, BurritoAudioAlignment::class.java)
            timing.alignmentFile = timingFile
            return timing
        }

        fun load(timing: String): BurritoAudioAlignment {
            val mapper = ObjectMapper().registerKotlinModule()
            return mapper.readValue(timing, BurritoAudioAlignment::class.java)
        }
    }
}

class AudioDocument(
    @JsonProperty("timecode")
    var timecode: Timecode,

    @JsonProperty("text-reference")
    var textReference: TextReference
)

class Timecode(
    @JsonProperty("scheme")
    val scheme: String,

    @JsonProperty("docid")
    val docid: String
)

class TextReference(
    @JsonProperty("scheme")
    val scheme: String
)


// const val CUE_HEADER_PATTERN: Pattern = Pattern.compile("^(\\S+)\\s+-->\\s+(\\S+)(.*)?$")
class Record(
    @JsonProperty("timecode")
    val timecode: List<String> = listOf(),

    @JsonProperty("text-reference")
    val textReference: List<String> = listOf()
) {
    fun toWebVttCueContent(scheme: String): WebVttDocument.WebVttCueContent? {
        val timestamp = timecode.first()
        val reference = textReference.first()
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

enum class ReferenceType(private val value: String) {
    AUDIO_REFERENCE("audio-reference");

    override fun toString(): String {
        return this.value
    }

    @JsonValue
    fun value(): String {
        return this.value
    }

    companion object {
        private val CONSTANTS: MutableMap<String, ReferenceType> = HashMap()

        init {
            for (c in values()) {
                ReferenceType.CONSTANTS[c.value] = c
            }
        }

        @JsonCreator
        fun fromValue(value: String): ReferenceType {
            val constant = ReferenceType.CONSTANTS[value]
            requireNotNull(constant) { value }
            return constant
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