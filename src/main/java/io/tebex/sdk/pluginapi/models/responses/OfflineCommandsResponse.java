package io.tebex.sdk.pluginapi.models.responses;

import io.tebex.sdk.pluginapi.models.QueuedOfflineCommand;
import io.tebex.sdk.pluginapi.models.QueuedOnlineCommand;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.util.List;

@Getter @AllArgsConstructor
public class OfflineCommandsResponse {
    private final OfflineCommandsMeta meta;
    private final List<QueuedOfflineCommand> commands;

    @Data
    public static class OfflineCommandsMeta {
        private final boolean limited;
    }
}