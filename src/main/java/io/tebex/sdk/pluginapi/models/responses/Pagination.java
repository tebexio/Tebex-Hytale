package io.tebex.sdk.pluginapi.models.responses;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@RequiredArgsConstructor
@ToString
public class Pagination {
    private final int totalResults;
    private final int currentPage;
    private final int lastPage;
    private final String previous;
    private final String next;

    public static Pagination fromJsonObject(JsonObject jsonObject) {
        return new Pagination(
                jsonObject.get("totalResults").getAsInt(),
                jsonObject.get("currentPage").getAsInt(),
                jsonObject.get("lastPage").getAsInt(),
                !jsonObject.get("previous").isJsonNull() ? jsonObject.get("previous").getAsString() : null,
                !jsonObject.get("next").isJsonNull() ? jsonObject.get("next").getAsString() : null
        );
    }
}
