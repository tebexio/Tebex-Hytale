package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import io.tebex.hytale.plugin.TebexPlugin;

import javax.annotation.Nonnull;

public class TebexRewriteJarCommand extends CommandBase {
    private final TebexPlugin plugin = TebexPlugin.get();

    public TebexRewriteJarCommand() {
        super("rewritejar", "commands.tebex.rewritejar");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandUtil.requirePermission(ctx.sender(), HytalePermissions.fromCommand("tebex.debug"));

        TebexPlugin.JarRewriteTestResult result = plugin.rewriteOwnJarForTest();
        ctx.sendMessage(Message.raw(result.message()));
        if (result.success()) {
            plugin.info("[JarRewriteTest] " + result.message());
        } else {
            plugin.warn("[JarRewriteTest] " + result.message(), "See server logs above for detailed jar rewrite failure output.");
        }
    }
}
