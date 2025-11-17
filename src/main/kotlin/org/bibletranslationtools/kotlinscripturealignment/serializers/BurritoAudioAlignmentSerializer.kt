package org.bibletranslationtools.kotlinscripturealignment.serializers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import org.bibletranslationtools.kotlinscripturealignment.model.BurritoAudioAlignment
import org.bibletranslationtools.kotlinscripturealignment.model.Documents
import org.bibletranslationtools.kotlinscripturealignment.model.DocumentsList
import org.bibletranslationtools.kotlinscripturealignment.model.DocumentsMap

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

        if (value.roles != null) {
            gen.writeFieldName("roles")
            gen.writeObject(value.roles)
        }

        // Conditionally serialize documents and records based on whether groups is present
        if (value.groups.isNullOrEmpty()) {
            if (value.documents != null) {
                gen.writeFieldName("documents")
                gen.writeObject(value.documents) // This will use DocumentsSerializer
            }
            if (value.records.isNotEmpty()) {
                gen.writeFieldName("records")
                gen.writeObject(value.records)
            }
        } else {
            // If groups is present, omit top-level documents and records
            gen.writeFieldName("groups")
            gen.writeObject(value.groups)
        }
        gen.writeEndObject()
    }
}
