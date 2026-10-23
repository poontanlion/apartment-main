package com.victory.apartment.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Masks phone-like digit sequences in emergency contact details before returning JSON. */
public class MaskedEmergencyContactSerializer extends JsonSerializer<String> {
    private static final Pattern PHONE_LIKE = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\s().-]*){7,}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null || value.isBlank()) {
            gen.writeNull();
            return;
        }
        Matcher matcher = PHONE_LIKE.matcher(value);
        StringBuffer masked = new StringBuffer();
        while (matcher.find()) {
            String original = matcher.group();
            int contentEnd = original.length();
            while (contentEnd > 0 && Character.isWhitespace(original.charAt(contentEnd - 1))) contentEnd--;
            String trailingWhitespace = original.substring(contentEnd);
            String digits = original.substring(0, contentEnd).replaceAll("\\D", "");
            String replacement = "x".repeat(Math.max(0, digits.length() - Math.min(4, digits.length())))
                    + digits.substring(Math.max(0, digits.length() - 4)) + trailingWhitespace;
            matcher.appendReplacement(masked, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(masked);

        Matcher emailMatcher = EMAIL.matcher(masked.toString());
        StringBuffer maskedContacts = new StringBuffer();
        while (emailMatcher.find()) {
            String address = emailMatcher.group();
            int at = address.indexOf('@');
            String replacement = address.substring(0, 1) + "***" + address.substring(at);
            emailMatcher.appendReplacement(maskedContacts, Matcher.quoteReplacement(replacement));
        }
        emailMatcher.appendTail(maskedContacts);
        gen.writeString(maskedContacts.toString());
    }
}
