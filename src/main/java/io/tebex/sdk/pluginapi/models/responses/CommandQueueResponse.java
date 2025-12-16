package io.tebex.sdk.pluginapi.models.responses;

import io.tebex.sdk.pluginapi.models.QueuedPlayer;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter @AllArgsConstructor
public class CommandQueueResponse {
    private final boolean executeOffline;
    private final int nextCheck;
    private final boolean more;
    private final List<QueuedPlayer> players;
}