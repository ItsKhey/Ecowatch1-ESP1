package com.example.ecowatchpersonnel1.data.local;

import androidx.room.TypeConverter;

import com.example.ecowatchpersonnel1.data.models.CrewMember;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores a report's dispatched crew as a single JSON string column.
 *
 * <p>The crew is read as a whole with the report it belongs to and is never
 * queried on its own, so it rides along on the reports row rather than
 * becoming a second table with a join. An empty crew is stored as NULL so a
 * report that was never dispatched does not carry a meaningless "[]".
 *
 * <p>The column is plain data with no file handles, so keeping it on the
 * reports row avoids a second table and the join needed to render a report
 * detail.
 */
public class CrewMemberListConverter {

    private static final Gson GSON = new Gson();
    private static final Type LIST_OF_CREW_MEMBER =
            new TypeToken<List<CrewMember>>() { }.getType();

    @TypeConverter
    public static List<CrewMember> fromJson(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<CrewMember> parsed = GSON.fromJson(value, LIST_OF_CREW_MEMBER);
            return parsed == null ? new ArrayList<>() : parsed;
        } catch (Exception e) {
            // A malformed column must not break every read of the reports table.
            return new ArrayList<>();
        }
    }

    @TypeConverter
    public static String toJson(List<CrewMember> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return GSON.toJson(values, LIST_OF_CREW_MEMBER);
    }
}

