package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.tebex.hytale.plugin.TebexPlugin;

import javax.annotation.Nonnull;

public class TebexSecretCommand extends CommandBase {
    private final TebexPlugin plugin = TebexPlugin.get();
    public TebexSecretCommand() {
        super("secret", "commands.tebex.secret");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        plugin.debug("setting secret key to: " + ctx.getInputString());

        //TODO set in config file
        plugin.getPluginApi().setSecretKey(ctx.getInputString());
        plugin.refreshServerInfo();

        // make sure the secret key worked by checking if server is non-null
        var info = plugin.getTebexServerInfo();
        if (info == null) {
            ctx.sendMessage(Message.raw("Invalid secret key! Double check that it is correct and try again."));
            plugin.warnNoLog("Invalid secret key!", "Double check that it is correct and try again.");
            plugin.getPluginApi().setSecretKey("");
            return;
        }

        // server was successfully authed and info has been set
        ctx.sendMessage(Message.raw("Successfully connected to " + info.getStore().getName() + " as " + info.getServer().getName()));
    }
}
