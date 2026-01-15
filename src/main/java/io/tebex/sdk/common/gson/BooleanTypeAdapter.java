package io.tebex.sdk.common.gson;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public final class BooleanTypeAdapter extends TypeAdapter<Boolean> {

    @Override
    public void write(JsonWriter out, Boolean value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.value(value);
    }

    @Override
    public Boolean read(JsonReader in) throws IOException {
        return switch (in.peek()) {
            case BOOLEAN -> in.nextBoolean();
            case NUMBER -> in.nextInt() != 0;
            case STRING -> Boolean.parseBoolean(in.nextString());
            case NULL -> {
                in.nextNull();
                yield false;
            }
            default -> throw new JsonParseException(
                    "Invalid boolean value: " + in.peek()
            );
        };
    }
}