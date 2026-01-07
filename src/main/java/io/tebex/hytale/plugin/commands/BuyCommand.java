package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.tebex.hytale.plugin.TebexPlugin;

import javax.annotation.Nonnull;

public class BuyCommand extends AbstractPlayerCommand {
    public BuyCommand(String name) {
        super(name, "commands.tebex.buy.desc");
    }

    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        CommandUtil.requirePermission(ctx.sender(), HytalePermissions.fromCommand("tebex.buy"));

        if (TebexPlugin.get().getTebexServerInfo() == null) {
            ctx.sendMessage(Message.raw("Tebex is not setup!"));
            return;
        }

        // respond with the configured buy message.
        var message = TebexPlugin.get().getConfig().get().getBuyCommandMessage();
        var clickable = false;
        if (message == null || message.isEmpty()) {
            return;
        }

        if (message.contains("{url}")) {
            message = message.replace("{url}", TebexPlugin.get().getTebexServerInfo().getStore().getDomain());
            clickable = true;
        }

        if (clickable) { // requested store url, add clickable link
            ctx.sendMessage(Message.raw(message).link(TebexPlugin.get().getTebexServerInfo().getStore().getDomain()));
        } else { // basic message
            ctx.sendMessage(Message.raw(message));
        }
    }
}