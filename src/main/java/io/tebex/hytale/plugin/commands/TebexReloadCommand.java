package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.tebex.hytale.plugin.TebexPlugin;

import javax.annotation.Nonnull;

public class TebexReloadCommand extends CommandBase {
    private final TebexPlugin plugin = TebexPlugin.get();
    public TebexReloadCommand() {
        super("reload", "commands.tebex.reload");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        plugin.info("Reloading Tebex...");
        try {
            plugin.getConfig().load().wait();
            plugin.getPluginApi().setSecretKey(plugin.getConfig().get().getSecretKey());
            plugin.refreshServerInfo(); // server info will be set to null if this fails

            if (plugin.getTebexServerInfo() == null) {
                ctx.sendMessage(Message.raw("Reloading failed, Tebex is not setup!"));
                return;
            }

            ctx.sendMessage(Message.raw("Tebex reloaded successfully!"));
        } catch (Exception e) {
            plugin.error("Error running /reload: ", e);
            ctx.sendMessage(Message.parse("An unexpected error occurred while reloading: " + e.getMessage()));
            return;
        }
        plugin.refreshServerInfo();
    }
}
