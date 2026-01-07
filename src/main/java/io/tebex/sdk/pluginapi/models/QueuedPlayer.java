package io.tebex.sdk.pluginapi.models;

import lombok.Data;

@Data
public class QueuedPlayer {
    private final int id;
    private final String name;
    private final String uuid;

    /**
     * Constructs a Player instance.
     *
     * @param id The Tebex player ID.
     * @param name The player name.
     * @param uuid The player UUID.
     */
    public QueuedPlayer(int id, String name, String uuid) {
        this.id = id;
        this.name = name;
        this.uuid = uuid;
    }
}
