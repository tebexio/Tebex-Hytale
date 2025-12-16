package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.tebex.hytale.plugin.TebexPlugin;

import javax.annotation.Nonnull;

public class TebexInfoCommand extends CommandBase {
    private final TebexPlugin plugin = TebexPlugin.get();
    public TebexInfoCommand() {
        super("info", "commands.tebex.info");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        if (plugin.getTebexServerInfo() == null) {
            ctx.sendMessage(Message.raw("Tebex is not setup!"));
            return;
        }

        var info = plugin.getTebexServerInfo();
        ctx.sendMessage(Message.raw(info.getStore().getName()));
        ctx.sendMessage(Message.raw("Click to visit the webstore at " + info.getStore().getDomain()).link(info.getStore().getDomain()));
    }
}
