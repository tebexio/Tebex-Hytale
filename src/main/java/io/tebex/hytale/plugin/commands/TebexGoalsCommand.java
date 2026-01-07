package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import io.tebex.hytale.plugin.TebexPlugin;

import javax.annotation.Nonnull;

public class TebexGoalsCommand extends CommandBase {
    private final TebexPlugin plugin = TebexPlugin.get();
    public TebexGoalsCommand() {
        super("goals", "commands.tebex.goals");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandUtil.requirePermission(ctx.sender(), HytalePermissions.fromCommand("tebex.goals"));
        if (plugin.getTebexServerInfo() == null) {
            ctx.sendMessage(Message.raw("Tebex is not setup!"));
            return;
        }

        var goals = plugin.getCommunityGoalsCache();
        for (var goal: goals) {
            ctx.sendMessage(Message.raw(goal.getName() + ": " + goal.getCurrent() + "/" + goal.getTarget()));
        }
    }
}
