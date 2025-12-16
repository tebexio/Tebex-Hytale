package io.tebex.hytale.plugin;

import com.hypixel.hytale.builtin.asseteditor.EditorClient;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.WindowType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import io.tebex.hytale.plugin.commands.BuyCommand;
import io.tebex.hytale.plugin.commands.TebexCommand;
import io.tebex.sdk.http.IHttpProvider;
import io.tebex.sdk.http.JdkHttpProvider;
import io.tebex.sdk.pluginapi.IPluginAdapter;
import io.tebex.sdk.pluginapi.PluginApi;
import io.tebex.sdk.pluginapi.models.*;
import io.tebex.sdk.pluginapi.models.Package;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;

public class TebexPlugin extends JavaPlugin implements IPluginAdapter {
    public static final String VERSION = "0.0.1";

    // tebex apis
    @Getter private PluginApi pluginApi;

    // tebex fields
    private final Config<TebexConfig> config;
    @Nullable @Getter private ServerInformation tebexServerInfo;
    @Setter private long nextCheckQueue;
    private long nextSendPlayerEvents;
    private long nextSendServerEvents;
    private final List<PluginEvent> pluginEvents = new ArrayList<>();
    private final List<ServerEvent> serverEvents = new ArrayList<>();
    @Getter private List<Category> categoriesCache = new ArrayList<>();
    @Getter private List<Package> packagesCache = new ArrayList<>();
    @Getter private List<CommunityGoal> communityGoalsCache = new ArrayList<>();
    private final ConcurrentHashMap<Integer, QueuedCommand> completedCommands = new ConcurrentHashMap<>();

    private ScheduledExecutorService tasks;
    private static TebexPlugin instance;
    private static final JdkHttpProvider http = new JdkHttpProvider("Tebex-Hytale/" + VERSION);

    // constructor is called by the plugin manager to create a pending plugin
    public TebexPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        this.config = this.withConfig("config", TebexPlugin.TebexConfig.CODEC);
    }

    public static TebexPlugin get() {
        return instance;
    }

    @Override
    public IHttpProvider getHttpProvider() {
        return http;
    }

    // the setup phase must be completed before the HytaleServer is considered "booted". it is called early in the startup process
    @Override
    protected void setup() {
        super.setup();
        debug("Tebex has reached the setup phase.");
        registerCommands();
    }

    // the start phase is run after setup, late in the startup process, just before the "Hytale Server Booted!" splash
    @Override
    protected void start() {
        super.start();
        debug("Tebex has reached the start phase.");

        String envSecretKey = System.getenv("TEBEX_SECRET_KEY"); // to auth plugin api FIXME not detected?
        String configSecretKey = this.config != null && config.get() != null ? config.get().getSecretKey() : null;

        // authenticate store with game server secret key, required
        String secretKey = envSecretKey != null ? envSecretKey : configSecretKey;
        secretKey = "ea029815ffef9bf44990409212c72d1eaebe400a"; //FIXME testing key
        if (secretKey == null || secretKey.isBlank()) {
            warnNoLog("No Tebex secret key found.", "Please run /tebex secret <key> to connect Tebex to your store.");
            this.shutdown();
            return;
        }

        // setup the store
        info("Loading Tebex webstore...");
        pluginApi = new PluginApi(this, secretKey);
        this.refreshServerInfo(); // will set server to null if failed
        if (this.tebexServerInfo == null) {
            warnNoLog("Failed to authenticate with Tebex.", "Please check your secret key and try again.");
            this.shutdown();
            return;
        }

        //FIXME make sure it's a hytale store, or else shutdown
//        if (!this.server.getStore().getGameType().equals("hytale")) {
//            error("This plugin only works with Hytale stores. Please use a game server key associated with a Hytale store.");
//            this.shutdown();
//            return;
//        }

        // send server init on successful start
        pluginEvents.add(new PluginEvent(this, EnumEventLevel.INFO, "Server init").onServer(this.tebexServerInfo));

        // if this is a restart, we might have scheduled tasks pending, shut them down
        if (tasks != null) {
            List<Runnable> tasksKilled = tasks.shutdownNow();
            if (!tasksKilled.isEmpty()) {
                debug("shutdown " + tasksKilled.size() + " scheduled tasks.");
            }
        }

        tasks = Executors.newScheduledThreadPool(4);

        // refresh store info (new packages, categories, sales, etc.)
        tasks.scheduleWithFixedDelay(this::refreshServerInfo, 15, 15, TimeUnit.MINUTES); // wait 15 minutes first, then repeat every 15 minutes after the last task completes
        tasks.scheduleWithFixedDelay(() -> {
            // check trigger for the command queue
            // this will check if it's okay to trigger the next queue check. based on received next_check the delay between
            // requests might change, so this runnable is responsible for the preliminary check and trigger if at check time or beyond
            if (System.currentTimeMillis() > nextCheckQueue) {
                nextCheckQueue = performCheck();
            }
        }, 0 ,10, TimeUnit.SECONDS); // run now, and repeat every 10 seconds

        tasks.scheduleWithFixedDelay(() -> {
            // check trigger for player joins / leaves. triggers every 1 minute or if joins/leaves exceed 10
            if (serverEvents.size() > 10 || System.currentTimeMillis() > nextSendPlayerEvents) {
                handlePlayerEvents();
                nextSendPlayerEvents = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1);
            }
        }, 10, 10, TimeUnit.SECONDS); // run now, repeat check for trigger every 10 seconds

        tasks.scheduleWithFixedDelay(() -> {
            // check trigger for runtime metrics (warning and error logs and traces), triggers every 1 minute or if logs exceed 10
            if (pluginEvents.size() > 10 || System.currentTimeMillis() > nextSendServerEvents) {
                handlePluginEvents();
                nextSendServerEvents = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1);
            }
        }, 10, 10, TimeUnit.SECONDS); // run now, repeat check every 10 seconds

    }

    private void registerCommands() {
        this.getCommandRegistry().registerCommand(new TebexCommand());

        var buyEnabled = this.config.get().buyCommandEnabled;
        var buyName = this.config.get().buyCommandName;
        if (buyEnabled) {
            if (buyName == null || buyName.isBlank()) {
                buyName = "buy";
                debug("buy command name not set, using default: /" + buyName);
            } else {
                debug("buy command name set to: /" + buyName);
            }
            this.getCommandRegistry().registerCommand(new BuyCommand(this.config.get().buyCommandName));
        }
    }

    private void handlePlayerEvents() {

    }

    private void handlePluginEvents() {

    }

    public void refreshServerInfo() {
        try {
            ServerInformation serverInfo = pluginApi.getServerInformation();
            var store = serverInfo.getStore();
            info("Successfully authenticated with " + store.getName() + "(" + store.getDomain() + ") as " + serverInfo.getServer().getName());
            debug("Downloading store info...");
            packagesCache = pluginApi.getPackages();
            communityGoalsCache = pluginApi.getCommunityGoals();
            categoriesCache = pluginApi.getCategories();
            categoriesCache.sort(java.util.Comparator.comparingInt(Category::getOrder));
            debug("Packages: " + packagesCache.size() + ", Categories: " + categoriesCache.size() + ", Community Goals: " + communityGoalsCache.size());
            info("Tebex is set up successfully!");
            this.tebexServerInfo = serverInfo;
        } catch (Exception e) {
            error("Failed to refresh server info: " + e.getMessage(), e);
            this.tebexServerInfo = null;
        }
    }

    // @return next_check
    public long performCheck() {
        debug("checking queue...");

        // offline commands can be run immediately so check those first
        try {
            debug("retrieving offline commands...");
            var offlineCommands = pluginApi.getOfflineCommands();
            for (QueuedCommand cmd : offlineCommands.getCommands()) {
                // check we haven't already completed this command
                if (completedCommands.containsKey(cmd.getId())) {
                    continue;
                }

                try {
                    debug(String.format("Executing offline command '%s' on %s...", cmd.getCommand(), cmd.getPlayer().getName()));
                    var success = executeCommand(cmd);
                    if (!success) {
                        warn(String.format("Offline command '%s' could not be executed on %s", cmd.getCommand(), cmd.getPlayer().getName()), "Hytale failed to execute the command. Check the command syntax.");
                        continue;
                    }

                    // successful execution, save command for deletion from the queue
                    completedCommands.put(cmd.getId(), cmd);
                } catch (Exception e) {
                    error(String.format("Unexpected error executing offline command '%s' on player %s: %s", cmd.getCommand(), cmd.getPlayer().getName()), e);
                }
            }
        } catch (Exception e) { // initial get of the offline commands failed
            error("Unexpected error while getting offline commands: ", e);
        }

        // now try to get and run online commands
        try {
            debug("retrieving online commands...");
            var commandQueueResponse = pluginApi.getCommandQueue();
            for (QueuedPlayer player : commandQueueResponse.getPlayers()) {
                try {
                    // make sure player is online before we make a request to get their commands
                    if (!isPlayerOnline(player.getName())) {
                        debug(String.format("Player %s has commands available but is not online, skipping...", player.getName()));
                        continue;
                    }

                    // player is online, so check for their online commands which are due
                    var onlineCommands = pluginApi.getOnlineCommands(player.getId());
                    for (QueuedCommand onlineCommand : onlineCommands.getCommands()) {
                        // guard against duplicate executions
                        if (completedCommands.containsKey(onlineCommand.getId())) {
                            continue;
                        }

                        // check command conditions
                        if (!playerHasInventorySlotsAvailable(player, onlineCommand.getRequiredSlots())) {
                            warn(String.format("Player " + player.getName() + " does not have enough inventory slots to execute command '%s'. Need: %d",
                                onlineCommand.getCommand(), onlineCommand.getRequiredSlots()), "We will try again at the next queue check.");
                            continue;
                        }

                        // commands might have a delay, so we either schedule execution in the future or execute immediately
                        if (onlineCommand.getDelay() > 0) {
                            //TODO implement delays
                        } else { // no delay, execute now
                            debug(String.format("Executing online command '%s' on %s...", onlineCommand.getCommand(), player.getName()));
                            var success = executeCommand(onlineCommand);
                            if (!success) {
                                warn(String.format("Online command '%s' could not be executed on %s", onlineCommand.getCommand(), player.getName()), "Hytale failed to execute the command. Check the command syntax.");
                                continue;
                            }
                        }

                        // successful execution, queue the command to be deleted
                        completedCommands.put(onlineCommand.getId(), onlineCommand);
                    }
                } catch (Exception e) {
                    error("Unexpected error retrieving online commands for " + player.getName() + "): ", e);
                }
            }

            long nextCheck = commandQueueResponse.getNextCheck() * 1000L; // in seconds, convert to milliseconds
            nextCheck += System.currentTimeMillis();
            debug("next check at " + nextCheck);
            return nextCheck;
        } catch (Exception e) {
            error("Unexpected error retrieving online commands: ", e);
        }

        // always delete queued commands immediately after a check
        try {
            pluginApi.deleteCompletedCommands(completedCommands);
        } catch (Exception e) {
            error("Unexpected error while deleting completed commands! This can result in duplicated deliveries!: " + e.getMessage(), e);
        }

        return System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2); // default every 2 minutes if something went wrong
    }

    @Override
    protected void shutdown() {
        super.shutdown();
        debug("Shutting down Tebex");
        this.tebexServerInfo = null;
        this.tasks.shutdownNow();
        this.packagesCache.clear();
        this.categoriesCache.clear();
        this.communityGoalsCache.clear();
        this.handlePluginEvents(); // will empty plugin events
        this.handlePlayerEvents(); // will empty player events
    }

    public void debug(String message) {
        if (isDebugModeEnabled()) { // plugin implements a debug mode to show these messages prefixed at info level
            this.getLogger().at(Level.INFO).log("[DEBUG] [Tebex] " + message);
            return;
        }
        // otherwise, still log the message at the finest level
        this.getLogger().at(Level.FINEST).log("[DEBUG] [Tebex] " + message);
    }

    public void info(String message) {
        this.getLogger().at(Level.INFO).log("[Tebex] " + message);
    }

    public void warnNoLog(String message, String solution) {
        this.getLogger().at(Level.WARNING).log("[Tebex] " + message);
        this.getLogger().at(Level.WARNING).log("[Tebex] " + solution);
    }

    public void warn(String message, String solution) {
        this.getLogger().at(Level.WARNING).log("[Tebex] " + message);
        this.getLogger().at(Level.WARNING).log("[Tebex] " + solution);
        pluginEvents.add(new PluginEvent(this, EnumEventLevel.WARNING, message + " " + solution).onServer(this.tebexServerInfo));
    }

    public void error(String message, Throwable throwable) {
        this.getLogger().at(Level.SEVERE).withCause(throwable).log("[Tebex] " + message);
        pluginEvents.add(new PluginEvent(this, EnumEventLevel.ERROR, message).withTrace(throwable).onServer(this.tebexServerInfo));
    }

    @Override
    public boolean playerHasInventorySlotsAvailable(QueuedPlayer player, int slots) {
        return false;
    }

    @Override
    public boolean executeCommand(QueuedCommand command) {
        var commandSender = ConsoleSender.INSTANCE;
        HytaleServer.get().getCommandManager().handleCommand(commandSender, command.getCommand());
        return true;
    }

    @Override
    public boolean isPlayerOnline(String username) {
//        Player playerComponent = (Player)store.getComponent(ref, Player.getComponentType());
//
//        assert playerComponent != null;
//
//        PlayerRef targetPlayerRef = (PlayerRef)this.targetPlayerArg.get(context);
//        Ref<EntityStore> targetRef = targetPlayerRef.getReference();
//        if (targetRef != null && targetRef.isValid()) {
//        } else {
//            context.sendMessage(MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD);
//        }
        return false;
    }

    @Override
    public boolean isDebugModeEnabled() {
        if (config.get() != null) {
            return config.get().debugMode;
        }
        return false;
    }

    @Override
    public void tellPlayer(String userId, String message) {
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Data
    public static class TebexConfig {
        public static final BuilderCodec<TebexPlugin.TebexConfig> CODEC;

        private @Nonnull String secretKey = "";
        private boolean buyCommandEnabled = true;
        private boolean debugMode = false;
        private String buyCommandName = "buy";

        static {
            CODEC =BuilderCodec.builder(TebexPlugin.TebexConfig.class, TebexPlugin.TebexConfig::new)
                    .append(new KeyedCodec<String>("SecretKey", Codec.STRING),
                            TebexConfig::setSecretKey, TebexConfig::getSecretKey).add()
                    .append(new KeyedCodec<String>("BuyCommandName", Codec.STRING),
                            TebexConfig::setBuyCommandName, TebexConfig::getBuyCommandName).add()
                    .append(new KeyedCodec<Boolean>("BuyCommandEnabled", Codec.BOOLEAN),
                            TebexConfig::setBuyCommandEnabled, TebexConfig::isBuyCommandEnabled).add()
                    .append(new KeyedCodec<Boolean>("DebugMode", Codec.BOOLEAN),
                            TebexConfig::setDebugMode, TebexConfig::isDebugMode).add()
                    .build();

        }
    }
}
