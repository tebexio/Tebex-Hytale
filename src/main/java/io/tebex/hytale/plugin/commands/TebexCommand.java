package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class TebexCommand extends AbstractCommandCollection {
    public TebexCommand() {
        super("tebex", "commands.tebex.desc");
        this.addSubCommand(new TebexForceCheckCommand());
        this.addSubCommand(new TebexSecretCommand());
        this.addSubCommand(new TebexInfoCommand());
        this.addSubCommand(new TebexCheckoutCommand());
        this.addSubCommand(new TebexSendLinkCommand());
        this.addSubCommand(new TebexDebugCommand());
        this.addSubCommand(new TebexGoalsCommand());
    }
}
