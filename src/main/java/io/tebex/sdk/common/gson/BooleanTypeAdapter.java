package io.tebex.sdk.common.gson;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Some APIs can return booleans as numbers (0/1,"0"/"1") instead of true/false.
 * GSON would throw JsonSyntaxException when deserializing package data, ex. `create_giftcard`
 */
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
            case STRING -> {
                var str = in.nextString();
                if (str.equalsIgnoreCase("true") || str.equalsIgnoreCase("1")) yield true;
                else if (str.equalsIgnoreCase("false") || str.equalsIgnoreCase("0")) yield false;
                else {
                    var message = (str.length() > 32 ? (str.substring(0,32) + "...(truncated)") : str);
                    throw new JsonParseException("Invalid boolean value '" + message + "'");
                }
            }
            case NULL -> {
                in.nextNull();
                yield false;
            }
            default -> throw new JsonParseException(
                    "Invalid boolean value of type: " + in.peek() + " at " + in.getPath()
            );
        };
    }
}