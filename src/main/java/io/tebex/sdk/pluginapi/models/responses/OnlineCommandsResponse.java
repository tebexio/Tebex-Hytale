package io.tebex.sdk.pluginapi.models.responses;

import com.google.gson.annotations.SerializedName;
import io.tebex.sdk.pluginapi.models.QueuedCommand;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter @AllArgsConstructor
public class OnlineCommandsResponse {
    @SerializedName("commands")
    private final List<QueuedCommand> commands;
}