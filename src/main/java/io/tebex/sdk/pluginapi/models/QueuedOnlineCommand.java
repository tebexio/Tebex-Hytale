package io.tebex.sdk.pluginapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Data
public class QueuedOnlineCommand {
    private final int id;
    private final String command;
    @Nullable private final Integer payment;
    @SerializedName("package")
    @Nullable private final Integer packageId;
    private final CommandConditions conditions;

    public String getParsedCommand(@Nonnull QueuedPlayer player) {
        String parsedCommand = command;
        parsedCommand = parsedCommand.replace("{username}", player.getName());
        parsedCommand = parsedCommand.replace("{name}", player.getName());
        parsedCommand = parsedCommand.replace("{id}", player.getUuid());
        parsedCommand = parsedCommand.replace("{uuid}", player.getUuid());
        return parsedCommand;
    }
}
