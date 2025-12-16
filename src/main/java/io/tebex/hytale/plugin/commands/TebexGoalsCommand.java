package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.tebex.hytale.plugin.TebexPlugin;

import javax.annotation.Nonnull;

public class TebexGoalsCommand extends CommandBase {
    private final TebexPlugin plugin = TebexPlugin.get();
    public TebexGoalsCommand() {
        super("reload", "commands.tebex.forcecheck");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        var goals = plugin.getCommunityGoalsCache();
        for (var goal: goals) {
            ctx.sendMessage(Message.parse(goal.getName() + ": " + goal.getCurrent() + "/" + goal.getTarget()));
        }
    }
}
