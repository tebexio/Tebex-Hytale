package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import io.tebex.hytale.plugin.TebexPlugin;

import javax.annotation.Nonnull;

public class TebexRebuildCommand extends CommandBase {
    private final TebexPlugin plugin = TebexPlugin.get();

    public TebexRebuildCommand() {
        super("rebuild", "commands.tebex.rebuild");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandUtil.requirePermission(ctx.sender(), HytalePermissions.fromCommand("tebex.debug"));

        TebexPlugin.AssetPackRebuildResult result = plugin.rebuildThumbnailAssetPack();
        ctx.sendMessage(Message.raw(result.summary()));
        if (result.detail() != null && !result.detail().isBlank()) {
            ctx.sendMessage(Message.raw("Reason: " + result.detail()));
        }
        if (result.nextStep() != null && !result.nextStep().isBlank()) {
            ctx.sendMessage(Message.raw("Next: " + result.nextStep()));
        }

        if (result.success()) {
            plugin.info("[ThumbnailAssetPack] " + result.summary());
        } else {
            plugin.warn(
                    "[ThumbnailAssetPack] " + result.summary(),
                    (result.detail() == null || result.detail().isBlank())
                            ? "See server logs above for detailed thumbnail asset-pack rebuild output."
                            : result.detail()
            );
        }
    }
}
