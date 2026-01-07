package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import io.tebex.hytale.plugin.TebexPlugin;

import javax.annotation.Nonnull;

public class TebexForceCheckCommand extends CommandBase {
    private final TebexPlugin plugin = TebexPlugin.get();
    public TebexForceCheckCommand() {
        super("forcecheck", "commands.tebex.forcecheck");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandUtil.requirePermission(ctx.sender(), HytalePermissions.fromCommand("tebex.forcecheck"));
        if (plugin.getTebexServerInfo() == null) {
            ctx.sendMessage(Message.raw("Tebex is not setup!"));
            return;
        }

        ctx.sendMessage(Message.raw("Performing check..."));
        var nextCheck = plugin.performCheck();
        plugin.setNextCheckQueue(nextCheck);
        ctx.sendMessage(Message.raw("Check completed. Next check in " + nextCheck + " seconds."));
    }
}
