package com.victory.apartment.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/** Masks phone numbers in API JSON while leaving persisted values unchanged. */
public class MaskedPhoneSerializer extends JsonSerializer<String> {
    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null || value.isBlank()) {
            gen.writeString(value);
            return;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 7) {
            gen.writeString("x".repeat(value.length()));
            return;
        }
        gen.writeString("x".repeat(digits.length() - 4) + digits.substring(digits.length() - 4));
    }
}
