package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.tebex.hytale.plugin.TebexPlugin;
import io.tebex.sdk.pluginapi.models.requests.CheckoutRequest;

import javax.annotation.Nonnull;

public class TebexCheckoutCommand extends CommandBase {
    private final TebexPlugin plugin = TebexPlugin.get();
    private final RequiredArg<Integer> packageId;

    public TebexCheckoutCommand() {
        super("checkout", "commands.tebex.checkout");
        this.packageId = this.withRequiredArg("packageId", "commands.tebex.checkout.packageId.desc", ArgTypes.INTEGER);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        if (plugin.getTebexServerInfo() == null) {
            ctx.sendMessage(Message.raw("Tebex is not setup!"));
            return;
        }

        var pack = plugin.getPackagesCache().get(packageId.get(ctx));
        if (pack == null) {
            ctx.sendMessage(Message.raw("Package not found!"));
            return;
        }

        if (!ctx.isPlayer()) {
            ctx.sendMessage(Message.raw("Checkout can only be used in-game."));
            return;
        }

        var req = new CheckoutRequest(ctx.sender().getDisplayName(), pack.getId());
        try {
            var url = plugin.getPluginApi().checkout(req);
            plugin.info(ctx.sender().getDisplayName() + " created a checkout link for " + pack.getName());
            plugin.info("Checkout URL: " + url.getUrl());
            ctx.sendMessage(Message.raw("Checkout a " + pack.getName() + " by clicking here: " + url.getUrl()).link(url.getUrl()));
        } catch (Exception e) {
            plugin.error("Failed to checkout package " + pack.getId(), e);
            ctx.sendMessage(Message.raw("Failed to create a checkout URL: " + e.getMessage()));
        }
    }
}
