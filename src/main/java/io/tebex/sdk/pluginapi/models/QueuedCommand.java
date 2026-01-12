package io.tebex.sdk.pluginapi.models;

import lombok.Data;

import javax.annotation.Nullable;

@Data
public class QueuedCommand {
    private final int id;
    private final String command;
    @Nullable private final Integer payment;
    @Nullable private final Integer packageId;

    private final QueuedPlayer player;
    private final boolean online;
    private final CommandConditions conditions;

    public String getParsedCommand() {
        String parsedCommand = command;

        if (player != null) {
            parsedCommand = parsedCommand.replace("{username}", player.getName());
            parsedCommand = parsedCommand.replace("{name}", player.getName());
            parsedCommand = parsedCommand.replace("{id}", player.getUuid());
            parsedCommand = parsedCommand.replace("{uuid}", player.getUuid());
        }

        return parsedCommand;
    }
}
