package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import io.tebex.hytale.plugin.TebexPlugin;

import javax.annotation.Nonnull;

public class TebexDebugCommand extends CommandBase {
    private final TebexPlugin plugin = TebexPlugin.get();
    private final RequiredArg<Boolean> debugStateArg;

    public TebexDebugCommand() {
        super("debug", "commands.tebex.debug");
        debugStateArg = this.withRequiredArg("debug", "commands.tebex.debug.arg.desc", ArgTypes.BOOLEAN);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandUtil.requirePermission(ctx.sender(), HytalePermissions.fromCommand("tebex.debug"));

        plugin.setDebugMode(debugStateArg.get(ctx));
        if (debugStateArg.get(ctx)) {
            plugin.info("Tebex debug mode enabled.");
        } else {
            plugin.info("Tebex debug mode disabled.");
        }
        plugin.getConfig().get().setDebugMode(debugStateArg.get(ctx));
        plugin.getConfig().save();
    }
}
