package io.tebex.sdk.pluginapi.models.responses;

import io.tebex.sdk.pluginapi.models.QueuedCommand;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter @AllArgsConstructor
public class OfflineCommandsResponse {
    private final boolean limited;
    private final List<QueuedCommand> commands;
}