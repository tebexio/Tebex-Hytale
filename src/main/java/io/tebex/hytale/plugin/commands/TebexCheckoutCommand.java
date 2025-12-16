package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.tebex.hytale.plugin.TebexPlugin;
import io.tebex.sdk.pluginapi.models.requests.CheckoutRequest;

import javax.annotation.Nonnull;

public class TebexCheckoutCommand extends CommandBase {
    private final TebexPlugin plugin = TebexPlugin.get();
    public TebexCheckoutCommand() {
        super("checkout", "commands.tebex.checkout");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        if (plugin.getTebexServerInfo() == null) {
            ctx.sendMessage(Message.raw("Tebex is not setup!"));
            return;
        }

        var packageId = ctx.getInputString(); // TODO arg
        var pack = plugin.getPackagesCache().get(packageId);
        if (pack == null) {
            ctx.sendMessage(Message.raw("Package not found!"));
            return;
        }

        if (!ctx.isPlayer()) {
            ctx.sendMessage(Message.raw("Checkout can only be used in-game."));
            return;
        }

        var req = new CheckoutRequest(ctx.senderAsPlayer().getDisplayName(), pack.getId());
        try {
            var url = plugin.getPluginApi().checkout(req);
            plugin.info(ctx.senderAsPlayer().getDisplayName() + " created a checkout link for " + pack.getName());
            ctx.sendMessage(Message.raw("Click here to complete checkout: " + url.getUrl()).link(url.getUrl()));
        } catch (Exception e) {
            plugin.error("Failed to checkout package " + pack.getId(), e);
            ctx.sendMessage(Message.raw("Failed to create a checkout URL: " + e.getMessage()));
            return;
        }
    }
}
