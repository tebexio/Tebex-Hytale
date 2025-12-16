package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.tebex.hytale.plugin.TebexPlugin;

import javax.annotation.Nonnull;

public class TebexReloadCommand extends CommandBase {
    private final TebexPlugin plugin = TebexPlugin.get();
    public TebexReloadCommand() {
        super("reload", "commands.tebex.forcecheck");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        plugin.refreshServerInfo();
    }
}
