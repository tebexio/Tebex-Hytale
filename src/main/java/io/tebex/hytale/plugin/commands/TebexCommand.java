package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.CommandCollectionBase;

public class TebexCommand extends CommandCollectionBase {
    public TebexCommand() {
        super("tebex", "commands.tebex.desc");
        this.addSubCommand(new TebexHelpCommand());
        this.addSubCommand(new TebexForceCheckCommand());
        this.addSubCommand(new TebexSecretCommand());
        this.addSubCommand(new TebexInfoCommand());
        this.addSubCommand(new TebexCheckoutCommand());
        this.addSubCommand(new TebexSendLinkCommand());
        this.addSubCommand(new TebexDebugCommand());
        this.addSubCommand(new TebexBanCommand());
        this.addSubCommand(new TebexGoalsCommand());
        this.addSubCommand(new TebexLookupCommand());
        this.addSubCommand(new TebexReloadCommand());
    }
}
