package io.tebex.sdk.pluginapi.models;

import com.google.gson.JsonObject;
import lombok.Data;

@Data
public class QueuedPlayer {
    private final int id;
    private final String name;
//    private final String uuid; FIXME

    /**
     * Constructs a Player instance.
     *
     * @param id The Tebex player ID.
     * @param name The player name.
     * @param uuid The player UUID. If truncated, is transformed into java-style uuid ("00000000-0000-0000-etc...")
     */
    public QueuedPlayer(int id, String name, String uuid) {
        this.id = id;
        this.name = name;
        //FIXME
        //this.uuid = String.valueOf(UUIDUtil.mojangIdToJavaId(uuid)); // tebex API returns truncated uuids
    }

    public static QueuedPlayer fromJson(JsonObject object) {
        return new QueuedPlayer(
                object.get("id").getAsInt(),
                object.get("name").getAsString(),
                !object.get("uuid").isJsonNull() ? object.get("uuid").getAsString() : null
        );
    }
}
