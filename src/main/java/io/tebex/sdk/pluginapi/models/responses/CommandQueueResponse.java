package io.tebex.sdk.pluginapi.models.responses;

import com.google.gson.annotations.SerializedName;
import io.tebex.sdk.pluginapi.models.QueuedPlayer;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * ex.
 * {
 *     "meta": {
 *         "execute_offline": false,
 *         "next_check": 60,
 *         "more": false
 *     },
 *     "players": [
 *     ]
 * }
 */
@Getter @AllArgsConstructor
public class CommandQueueResponse {
    @Getter @AllArgsConstructor
    public static class CommandQueueMeta {
        @SerializedName("execute_offline")
        private final boolean executeOffline;
        @SerializedName("next_check")
        private final int nextCheck;
        private final boolean more;
    }

    @SerializedName("meta")
    private final CommandQueueMeta meta;

    @SerializedName("players")
    private final List<QueuedPlayer> players;

    public List<QueuedPlayer> getPlayers() {
        return players != null ? players : List.of();
    }
}