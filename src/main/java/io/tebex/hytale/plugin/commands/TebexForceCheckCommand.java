package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.tebex.hytale.plugin.TebexPlugin;

import javax.annotation.Nonnull;

public class TebexForceCheckCommand extends CommandBase {
    private final TebexPlugin plugin = TebexPlugin.get();
    public TebexForceCheckCommand() {
        super("forcecheck", "commands.tebex.forcecheck");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        var nextCheck = plugin.performCheck();
        plugin.setNextCheckQueue(nextCheck);
    }
}
