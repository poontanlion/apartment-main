package com.victory.apartment.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/** Masks the email local-part in API JSON while leaving the stored address unchanged. */
public class MaskedEmailSerializer extends JsonSerializer<String> {
    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null || value.isBlank()) {
            gen.writeString(value);
            return;
        }
        int at = value.indexOf('@');
        if (at <= 0 || at == value.length() - 1) {
            gen.writeString("***");
            return;
        }
        String local = value.substring(0, at);
        String maskedLocal = local.substring(0, 1) + "***";
        gen.writeString(maskedLocal + value.substring(at));
    }
}
