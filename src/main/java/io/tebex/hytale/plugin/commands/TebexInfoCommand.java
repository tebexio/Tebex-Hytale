package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import io.tebex.hytale.plugin.TebexPlugin;

import javax.annotation.Nonnull;

public class TebexInfoCommand extends CommandBase {
    private final TebexPlugin plugin = TebexPlugin.get();
    public TebexInfoCommand() {
        super("info", "commands.tebex.info");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandUtil.requirePermission(ctx.sender(), HytalePermissions.fromCommand("tebex.info"));

        if (plugin.getTebexServerInfo() == null) {
            ctx.sendMessage(Message.raw("Tebex is not setup!"));
            return;
        }

        var info = plugin.getTebexServerInfo();

        if (ctx.isPlayer()) {
            ctx.sendMessage(Message.raw("Click to visit the webstore at " + info.getStore().getDomain()).link(info.getStore().getDomain()));
        } else {
            ctx.sendMessage(Message.raw("Plugin version: " + TebexPlugin.VERSION));
            ctx.sendMessage(Message.raw("Tebex is connected to " + info.getStore().getName() + " (" + info.getStore().getDomain() + ") as " + info.getServer().getName()));
        }
    }
}
