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
import io.tebex.hytale.plugin.gui.BuyGui;

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

        boolean opened = BuyGui.getInstance().open(store, ref, playerRef, world);
        if (!opened) {
            sendConfiguredBuyMessage(ctx);
        }
    }

    private void sendConfiguredBuyMessage(@Nonnull CommandContext ctx) {
        var plugin = TebexPlugin.get();
        var message = plugin.getConfig().get().getBuyCommandMessage();
        if (message == null || message.isEmpty()) {
            return;
        }

        var domain = plugin.getTebexServerInfo().getAccount().getDomain();
        var clickable = false;
        if (message.contains("{url}")) {
            message = message.replace("{url}", domain);
            clickable = true;
        }

        if (clickable) {
            ctx.sendMessage(Message.raw(message).link(domain));
        } else {
            ctx.sendMessage(Message.raw(message));
        }
    }
}
