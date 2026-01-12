package io.tebex.sdk.pluginapi.models;

import lombok.Data;

@Data
public class QueuedPlayer {
    private final int id;
    private final String name;
    private final String uuid;
}
