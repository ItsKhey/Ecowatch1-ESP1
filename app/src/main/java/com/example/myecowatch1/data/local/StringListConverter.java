package com.example.myecowatch1.data.local;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores a report's photo list as a single JSON string column.
 *
 * <p>Photo URLs are plain server strings with no local file handles, so keeping
 * them on the reports row avoids a second table and the join needed to render a
 * report detail. An empty list is stored as NULL so a report with no photos does
 * not carry a meaningless "[]".
 */
public class StringListConverter {

    private static final Gson GSON = new Gson();
    private static final Type LIST_OF_STRING = new TypeToken<List<String>>() { }.getType();

    @TypeConverter
    public static List<String> fromJson(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<String> parsed = GSON.fromJson(value, LIST_OF_STRING);
            return parsed == null ? new ArrayList<>() : parsed;
        } catch (Exception e) {
            // A malformed column must not break every read of the reports table.
            return new ArrayList<>();
        }
    }

    @TypeConverter
    public static String toJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return GSON.toJson(values, LIST_OF_STRING);
    }
}
