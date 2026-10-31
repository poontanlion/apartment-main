package com.victory.apartment.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/** Masks identity/passport values in API JSON while keeping the stored value unchanged. */
public class MaskedIdSerializer extends JsonSerializer<String> {
    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null || value.isBlank()) {
            gen.writeNull();
            return;
        }
        String compact = value.trim();
        int visible = Math.min(4, compact.length());
        gen.writeString("*".repeat(Math.max(4, compact.length() - visible)) + compact.substring(compact.length() - visible));
    }
}
