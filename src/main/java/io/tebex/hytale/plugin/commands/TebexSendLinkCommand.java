package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.tebex.hytale.plugin.TebexPlugin;
import io.tebex.sdk.pluginapi.models.requests.CheckoutRequest;

import javax.annotation.Nonnull;

public class TebexSendLinkCommand extends AbstractPlayerCommand {
    private final RequiredArg<PlayerRef> targetPlayerArg;
    private final RequiredArg<Integer> targetPackageArg;

    private final TebexPlugin plugin = TebexPlugin.get();
    public TebexSendLinkCommand() {
        super("sendlink", "commands.tebex.sendlink");
        this.targetPlayerArg = this.withRequiredArg("player", "commands.tebex.sendlink.player.desc", ArgTypes.PLAYER_REF);
        this.targetPackageArg = this.withRequiredArg("packageId", "commands.tebex.sendlink.player.desc", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        CommandUtil.requirePermission(ctx.sender(), HytalePermissions.fromCommand("tebex.sendlink"));

        if (plugin.getTebexServerInfo() == null) {
            ctx.sendMessage(Message.raw("Tebex is not setup!"));
            return;
        }

        var pack = this.plugin.getPackagesCache().get(targetPackageArg.get(ctx));
        if (pack == null) {
            ctx.sendMessage(Message.raw("Package not found!"));
            return;
        }

        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        assert playerComponent != null;

        PlayerRef targetPlayerRef = this.targetPlayerArg.get(ctx);
        Ref<EntityStore> targetRef = targetPlayerRef.getReference();
        if (targetRef != null && targetRef.isValid()) {
            Store<EntityStore> targetStore = targetRef.getStore();
            World targetWorld = (targetStore.getExternalData()).getWorld();
            targetWorld.execute(() -> {
                Player targetPlayerComponent = targetStore.getComponent(targetRef, Player.getComponentType());
                if (targetPlayerComponent == null) {
                    ctx.sendMessage(Message.raw("Target player was not found!"));
                } else {
                    // send the checkout link to the player
                    var req = new CheckoutRequest(targetPlayerArg.getName(), targetPackageArg.get(ctx));
                    try {
                        var url = plugin.getPluginApi().checkout(req);
                        targetPlayerComponent.sendMessage(Message.raw(ctx.sender().getDisplayName() + " has sent you a checkout link:"));
                        targetPlayerComponent.sendMessage(Message.raw("Buy " + pack.getName() + " by clicking here: " + url).link(url.getUrl()));
                    } catch (Exception e) {
                        ctx.sendMessage(Message.raw("An unexpected error occurred while checking out the target player!"));
                        plugin.error("Could not send link to player, failed to generate checkout link", e);
                    }
                }
            });
        } else {
            ctx.sendMessage(Message.raw("Target player was not found!"));
        }
    }
}
