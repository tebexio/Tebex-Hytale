package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

public class TebexHelpCommand extends CommandBase {
    public TebexHelpCommand() {
        super("help", "commands.tebex.help");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("Available Commands:"));
        ctx.sendMessage(Message.raw("/tebex secret"));
        ctx.sendMessage(Message.raw("/tebex info"));
        ctx.sendMessage(Message.raw("/tebex forcecheck"));
        ctx.sendMessage(Message.raw("/tebex checkout <packageId>"));
        ctx.sendMessage(Message.raw("/tebex sendlink <packageId> <username>"));
        ctx.sendMessage(Message.raw("/tebex debug <true/false>"));
        ctx.sendMessage(Message.raw("/tebex ban <username> <reason>"));
        ctx.sendMessage(Message.raw("/tebex goals"));
        ctx.sendMessage(Message.raw("/tebex lookup <username>"));
        ctx.sendMessage(Message.raw("/tebex reload"));
        ctx.sendMessage(Message.raw("/tebex help"));
    }
}
