package io.tebex.sdk.pluginapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Data
public class QueuedOfflineCommand implements ICommand {
    private final int id;
    private final String command;
    @Nullable private final Integer payment;
    @SerializedName("package")
    @Nullable private final Integer packageId;
    private final CommandConditions conditions;
    @Nonnull private final QueuedPlayer player;

    public String getParsedCommand(String uuid) {
        String parsedCommand = command;

        if (player != null) {
            parsedCommand = parsedCommand.replace("{username}", player.getName());
            parsedCommand = parsedCommand.replace("{name}", player.getName());
            parsedCommand = parsedCommand.replace("{id}", uuid);
            parsedCommand = parsedCommand.replace("{uuid}", uuid);
        }

        return parsedCommand;
    }
}
