package org.bibletranslationtools.kotlinscripturealignment.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

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
