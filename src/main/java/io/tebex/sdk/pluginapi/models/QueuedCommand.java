package io.tebex.sdk.pluginapi.models;

import lombok.Data;

@Data
public class QueuedCommand {
    private final int id;
    private final String command;
    private final Integer payment;
    private final Integer packageId; // may be null
    private final Integer delay;
    private final Integer requiredSlots;
    private final QueuedPlayer player;
    private final boolean online;

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
