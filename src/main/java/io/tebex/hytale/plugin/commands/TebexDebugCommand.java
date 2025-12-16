package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.tebex.hytale.plugin.TebexPlugin;

import javax.annotation.Nonnull;

public class TebexDebugCommand extends CommandBase {
    private final TebexPlugin plugin = TebexPlugin.get();
    public TebexDebugCommand() {
        super("debug", "commands.tebex.debug");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        var arg = ctx.getInputString();
        plugin.setDebugMode(Boolean.parseBoolean(arg));
    }
}
