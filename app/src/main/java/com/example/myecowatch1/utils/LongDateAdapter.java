package com.example.myecowatch1.utils;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class LongDateAdapter implements JsonDeserializer<Long> {
    private static final String[] DATE_FORMATS = new String[]{
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ"
    };

    @Override
    public Long deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json.isJsonPrimitive()) {
            if (json.getAsJsonPrimitive().isNumber()) {
                return json.getAsLong();
            } else if (json.getAsJsonPrimitive().isString()) {
                String dateStr = json.getAsString();
                if (dateStr == null || dateStr.isEmpty()) {
                    return null;
                }
                
                // Try to parse as long first in case it's a stringified number
                try {
                    return Long.parseLong(dateStr);
                } catch (NumberFormatException ignored) {}

                for (String formatStr : DATE_FORMATS) {
                    try {
                        SimpleDateFormat format = new SimpleDateFormat(formatStr, Locale.US);
                        format.setTimeZone(TimeZone.getTimeZone("UTC"));
                        Date date = format.parse(dateStr);
                        if (date != null) {
                            return date.getTime();
                        }
                    } catch (ParseException ignored) {}
                }
            }
        }
        return null;
    }
}
