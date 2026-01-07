package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import io.tebex.hytale.plugin.TebexPlugin;

import javax.annotation.Nonnull;

public class TebexSecretCommand extends CommandBase {
    private final TebexPlugin plugin = TebexPlugin.get();
    private final RequiredArg<String> secretArg;

    public TebexSecretCommand() {
        super("secret", "commands.tebex.secret");
        this.secretArg = this.withRequiredArg("secret", "commands.tebex.secret.arg", ArgTypes.STRING);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandUtil.requirePermission(ctx.sender(), HytalePermissions.fromCommand("tebex.secret"));
        plugin.debug("setting secret key to: " + secretArg.get(ctx));

        plugin.getPluginApi().setSecretKey(secretArg.get(ctx));
        plugin.getConfig().get().setSecretKey(secretArg.get(ctx));
        plugin.getConfig().save();
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
        plugin.getConfig().save();
        plugin.setupTasks();
    }
}
