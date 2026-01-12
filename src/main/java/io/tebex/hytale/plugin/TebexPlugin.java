package io.tebex.hytale.plugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import io.tebex.hytale.plugin.commands.BuyCommand;
import io.tebex.hytale.plugin.commands.TebexCommand;
import io.tebex.sdk.http.IHttpProvider;
import io.tebex.sdk.http.JdkHttpProvider;
import io.tebex.sdk.pluginapi.IPluginAdapter;
import io.tebex.sdk.pluginapi.PluginApi;
import io.tebex.sdk.pluginapi.models.*;
import io.tebex.sdk.pluginapi.models.Package;
import io.tebex.sdk.pluginapi.models.responses.CommandQueueResponse;
import io.tebex.sdk.pluginapi.models.responses.OfflineCommandsResponse;
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
    public static final String VERSION = "{{VERSION}}";

    // tebex apis
    @Getter private PluginApi pluginApi;

    // tebex fields
    @Getter private final Config<TebexConfig> config;
    @Nullable @Getter private ServerInformation tebexServerInfo;
    @Setter private long nextCheckQueue;
    private long nextSendPlayerEvents;
    private long nextSendServerEvents;
    private final CopyOnWriteArrayList<PluginEvent> pluginEvents = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ServerEvent> serverEvents = new CopyOnWriteArrayList<>();
    @Getter private final ConcurrentHashMap<Integer, Category> categoriesCache = new ConcurrentHashMap<>();
    @Getter private final ConcurrentHashMap<Integer, Package> packagesCache = new ConcurrentHashMap<>();
    @Getter private CopyOnWriteArrayList<CommunityGoal> communityGoalsCache = new CopyOnWriteArrayList<>();
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
        registerEvents();
    }

    // the start phase is run after setup, late in the startup process, just before the "Hytale Server Booted!" splash
    @Override
    protected void start() {
        super.start();
        debug("Tebex has reached the start phase.");
        pluginApi = new PluginApi(this);

        String envSecretKey = System.getenv("TEBEX_SECRET_KEY"); // to auth plugin api
        String configSecretKey = this.config != null && config.get() != null ? config.get().getSecretKey() : null;

        // authenticate store with the game server secret key, required
        String secretKey = "";
        if (envSecretKey != null && !envSecretKey.isBlank()) {
            info("Using TEBEX_SECRET_KEY environment variable");
            secretKey = envSecretKey;
        } else if (configSecretKey != null && !configSecretKey.isBlank()) {
            info("Using secret key from config.yml");
            secretKey = configSecretKey;
        }

        if (secretKey.isBlank()) {
            warnNoLog("No Tebex secret key is set.", "Please run /tebex secret <key> to connect Tebex to your store, or set the TEBEX_SECRET_KEY environment variable.");
            this.shutdown();
            return;
        }

        // set up the store
        pluginApi.setSecretKey(secretKey);
        info("Loading Tebex webstore...");
        this.refreshServerInfo(); // will set server to null if failed
        if (this.tebexServerInfo == null) {
            warnNoLog("Failed to authenticate with Tebex.", "Please check your secret key and try again.");
            return;
        }

        info("Successfully authenticated with " + tebexServerInfo.getAccount().getName() + "(" + tebexServerInfo.getAccount().getDomain() + ") as " + tebexServerInfo.getServer().getName());
        if (!this.tebexServerInfo.getAccount().getGameType().equalsIgnoreCase("hytale")) {
            error("This plugin only works with Hytale stores. Please use a game server key associated with a Hytale store.", new Throwable("Invalid game server key, a Hytale store is required."));
            this.shutdown();
            return;
        }

        // send server init on successful start
        pluginEvents.add(PluginEvent.logLine(EnumEventLevel.INFO, "Server init").onStore(this.tebexServerInfo));

        // start the scheduled tasks
        setupTasks();
    }

    public void setupTasks() {
        debug("setting up tasks...");

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
            // this will check if it's okay to trigger the next queue check. based on received next check the delay between
            // requests might change, so this runnable is responsible for the preliminary check and trigger if at check time or beyond
            if (System.currentTimeMillis() >= nextCheckQueue) {
                int nextCheckWaitSeconds = performCheck();
                nextCheckQueue = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(nextCheckWaitSeconds);
            }
        }, 0 ,1, TimeUnit.SECONDS); // run now, then repeat time check every 1 seconds

        tasks.scheduleWithFixedDelay(() -> {
            // check trigger for player joins / leaves. triggers every 1 minute or if joins/leaves exceed 10
            if (serverEvents.size() > 10 || System.currentTimeMillis() > nextSendPlayerEvents) {
                handlePlayerEvents();
                nextSendPlayerEvents = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1);
            }
        }, 10, 10, TimeUnit.SECONDS); // run now, repeat for trigger every 10 seconds

        tasks.scheduleWithFixedDelay(() -> {
            // check trigger for runtime metrics (warning and error logs and traces), triggers every 1 minute or if logs exceed 10
            if (pluginEvents.size() > 10 || System.currentTimeMillis() > nextSendServerEvents) {
                handlePluginEvents();
                nextSendServerEvents = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1);
            }
        }, 10, 10, TimeUnit.SECONDS); // run now, repeat every 10 seconds
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

    private void registerEvents() {
        this.getEventRegistry().register(PlayerConnectEvent.class, connection -> {
            this.serverEvents.add(new ServerEvent(
                    connection.getPlayerRef().getUuid().toString(),
                    connection.getPlayerRef().getUsername(),
                    "127.0.0.1", ServerEvent.EnumServerEventType.JOIN)); //TODO player ip
        });
        this.getEventRegistry().register(PlayerDisconnectEvent.class, connection -> {
            this.serverEvents.add(new ServerEvent(
                    connection.getPlayerRef().getUuid().toString(),
                    connection.getPlayerRef().getUsername(),
                    "127.0.0.1", ServerEvent.EnumServerEventType.LEAVE)); //TODO player ip
        });
    }
    private void handlePlayerEvents() {
        if (tebexServerInfo == null) { // don't send events for non-connected stores
            return;
        }

        if (serverEvents.isEmpty()) {
            return;
        }

        try {
            List<ServerEvent> eventsToSubmit = new ArrayList<>(serverEvents);
            pluginApi.submitServerEvents(eventsToSubmit);
            serverEvents.clear();
        } catch (Exception e) {
            error("Failed to submit player events to analytics", e);
        }
    }

    private void handlePluginEvents() {
        if (tebexServerInfo == null) { // don't send events for non-connected stores
            return;
        }

        if (pluginEvents.isEmpty()) {
            return;
        }

        try {
            List<PluginEvent> eventsToSubmit = new ArrayList<>(pluginEvents);
            pluginApi.submitPluginEvents(eventsToSubmit);
            pluginEvents.clear();
        } catch (Exception e) {
            error("Failed to submit plugin events to logs system", e);
        }
    }

    public void refreshServerInfo() {
        try {
            ServerInformation serverInfo = pluginApi.getServerInformation();
            debug("Downloading store info...");
            packagesCache.clear();
            pluginApi.getPackages().forEach(p -> {
                packagesCache.put(p.getId(), p);
            });
            var remoteCategories = pluginApi.getCategories();
            categoriesCache.clear();
            remoteCategories.sort(java.util.Comparator.comparingInt(Category::getOrder));
            pluginApi.getCategories().forEach(c -> {
                categoriesCache.put(c.getId(), c);
            });
            communityGoalsCache = new CopyOnWriteArrayList<>(pluginApi.getCommunityGoals());
            debug("Packages: " + packagesCache.size() + ", Categories: " + categoriesCache.size() + ", Community Goals: " + communityGoalsCache.size());
            this.tebexServerInfo = serverInfo;
        } catch (Exception e) {
            error("Failed to refresh server info: " + e.getMessage(), e);
            this.tebexServerInfo = null;
        }
    }

    private void handleOfflineCommands() {
        debug("retrieving offline commands...");
        OfflineCommandsResponse offlineCommands = null;
        try {
            offlineCommands = pluginApi.getOfflineCommands();
        } catch (Exception ex) {
            error("Unexpected error while getting offline commands: ", ex);
            return;
        }

        for (QueuedCommand offlineCommand : offlineCommands.getCommands()) {
            // check we haven't already completed this command
            if (completedCommands.containsKey(offlineCommand.getId())) {
                continue;
            }

            // commands might have a delay, so we either will schedule execution in the future or execute immediately
            if (offlineCommand.getConditions().getDelay() > 0) {
                info(String.format(
                        "Scheduling offline command (ID:%d) '%s' on %s to run in %d seconds...",
                        offlineCommand.getId(),
                        offlineCommand.getCommand(),
                        offlineCommand.getPlayer().getName(),
                        offlineCommand.getConditions().getDelay()
                ));

                tasks.schedule(() -> {
                    info(String.format("Executing scheduled offline command (ID:%d) '%s' on %s...", offlineCommand.getId(), offlineCommand.getCommand(), offlineCommand.getPlayer().getName()));
                    boolean success = executeCommand(offlineCommand);
                    if (!success) {
                        warn(String.format("Scheduled offline command (ID:%d) '%s' could not be executed on %s", offlineCommand.getId(), offlineCommand.getCommand(), offlineCommand.getPlayer().getName()), "Hytale failed to execute the command. Check the command syntax.");
                        return;
                    }
                    // for scheduled commands, add immediately to completed and purge
                    completedCommands.put(offlineCommand.getId(), offlineCommand);
                    try {
                        pluginApi.deleteCompletedCommands(completedCommands);
                    } catch (Exception e) {
                        error("Unexpected error while flushing completed commands! This can result in duplicated deliveries!: " + e.getMessage(), e);
                    }
                }, offlineCommand.getConditions().getDelay(), TimeUnit.SECONDS);
                continue; // command is scheduled, move on to the next
            }

            // no delay, execute this command now
            try {
                info(String.format("Executing offline command (ID:%d) '%s' on %s...", offlineCommand.getId(), offlineCommand.getCommand(), offlineCommand.getPlayer().getName()));
                var success = executeCommand(offlineCommand);
                if (!success) {
                    warn(String.format("Offline command '%s' could not be executed on %s", offlineCommand.getCommand(), offlineCommand.getPlayer().getName()), "Hytale failed to execute the command. Check the command syntax.");
                    continue; // process the next command
                }

                // successful execution, save command for deletion from the queue
                completedCommands.put(offlineCommand.getId(), offlineCommand);
            } catch (Exception e) {
                error(String.format("Unexpected error executing offline command '%s' on player %s", offlineCommand.getCommand(), offlineCommand.getPlayer().getName()), e);
            }
        }
    }

    private int handleOnlineCommands() {
        debug("retrieving online commands...");
        CommandQueueResponse commandQueueResponse = null;
        try {
            commandQueueResponse = pluginApi.getCommandQueue();
        } catch (Exception e) {
            error("Unexpected error retrieving online commands: ", e);
            return 120;
        }

        for (QueuedPlayer player : commandQueueResponse.getMeta().getPlayers()) {
            try {
                // make sure the player is online before we make a request to get their commands
                if (!isPlayerOnline(player.getName())) {
                    debug(String.format("Player %s has commands available but is not online, skipping...", player.getName()));
                    continue;
                }

                // player is online, so check for their online commands that are due
                var onlineCommands = pluginApi.getOnlineCommands(player.getId());
                for (QueuedCommand onlineCommand : onlineCommands.getCommands()) {
                    // guard against duplicate executions
                    if (completedCommands.containsKey(onlineCommand.getId())) {
                        continue;
                    }

                    // check command conditions - check inventory slots before applying the command
                    Integer requiredSlots = onlineCommand.getConditions().getRequiredSlots();
                    if (requiredSlots != null && requiredSlots > 0) {
                        if (!playerHasInventorySlotsAvailable(player, requiredSlots)) {
                            warn(String.format("Player " + player.getName() + " does not have enough inventory slots to execute command '%s'. Need: %d",
                                    onlineCommand.getCommand(), requiredSlots), "We will try again at the next queue check.");
                            continue;
                        }
                    }

                    // commands might have a delay, so we either will schedule execution in the future or execute immediately
                    if (onlineCommand.getConditions().getDelay() > 0) {
                        info(String.format(
                                "Scheduling online command (ID: %d) '%s' on %s to run in %d seconds...",
                                onlineCommand.getId(),
                                onlineCommand.getCommand(),
                                player.getName(),
                                onlineCommand.getConditions().getDelay()
                        ));

                        tasks.schedule(() -> {
                            info(String.format("Executing scheduled online command (ID:%d) '%s' on %s...", onlineCommand.getId(), onlineCommand.getCommand(), player.getName()));
                            boolean success = executeCommand(onlineCommand);
                            if (!success) {
                                warn(String.format("Scheduled online command (ID:%d) '%s' could not be executed on %s", onlineCommand.getId(), onlineCommand.getCommand(), player.getName()), "Hytale failed to execute the command. Check the command syntax.");
                                return;
                            }
                            // for scheduled commands, add immediately to completed and purge
                            completedCommands.put(onlineCommand.getId(), onlineCommand);
                            try {
                                pluginApi.deleteCompletedCommands(completedCommands);
                            } catch (Exception e) {
                                error("Unexpected error while flushing completed commands! This can result in duplicated deliveries!: " + e.getMessage(), e);
                            }
                        }, onlineCommand.getConditions().getDelay(), TimeUnit.SECONDS);
                    } else { // no delay, execute now
                        info(String.format("Executing online command (ID:%d) '%s' on %s...", onlineCommand.getId(), onlineCommand.getCommand(), player.getName()));
                        var success = executeCommand(onlineCommand);
                        if (!success) {
                            warn(String.format("Online command (ID: %d) '%s' could not be executed on %s", onlineCommand.getId(), onlineCommand.getCommand(), player.getName()), "Hytale failed to execute the command. Check the command syntax.");
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

        return commandQueueResponse.getMeta().getNextCheck();
    }
    // @return seconds to wait until we can check again. do NOT call this from anywhere except the main timing trigger loop,
    // otherwise you risk duplicating commands. if you need to check immediately, set the next check time to the current system time
    // and it will be picked up when the trigger is next checked (ideally <1 second)
    public int performCheck() {
        debug("checking queue...");

        // offline commands can be run immediately, so check those first. if any error continue processing online commands
        try {
            handleOfflineCommands();
        } catch (Exception e) {
            error("Unexpected error while handling offline commands: ", e);
        }

        var nextCheck = 120;
        // now try to get and run online commands
        try {
            nextCheck = handleOnlineCommands();
            debug("next check after " + nextCheck + " seconds");
        } catch (Exception e) {
            error("Unexpected error retrieving online commands: ", e);
        }

        // always delete queued commands immediately after a check
        try {
            pluginApi.deleteCompletedCommands(completedCommands);
        } catch (Exception e) {
            error("Unexpected error while deleting completed commands! This can result in duplicated deliveries!: " + e.getMessage(), e);
        }

        return nextCheck;
    }

    @Override
    protected void shutdown() {
        super.shutdown();
        debug("Shutting down Tebex plugin");
        this.tebexServerInfo = null;
        if (this.tasks != null) {
            this.tasks.shutdownNow();
        }
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
        pluginEvents.add(PluginEvent.logLine(EnumEventLevel.WARNING, message + " " + solution).onStore(this.tebexServerInfo));
    }

    public void error(String message, Throwable throwable) {
        this.getLogger().at(Level.SEVERE).withCause(throwable).log("[Tebex] " + message);
        pluginEvents.add(PluginEvent.logLine(EnumEventLevel.ERROR, message).withTrace(throwable).onStore(this.tebexServerInfo));
    }

    @Override
    public boolean playerHasInventorySlotsAvailable(QueuedPlayer player, int slots) {
        if (slots <= 0) {
            return true; // no slot requirement
        }

        try {
            PlayerRef playerRef = findPlayerByName(player.getName());
            if (playerRef == null) {
                return false; // player not found
            }

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                return false; // player reference invalid
            }

            Store<EntityStore> store = ref.getStore();
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            if (playerComponent == null) {
                return false; // player component not found
            }

            ItemContainer inventory = playerComponent.getInventory().getCombinedEverything();
            int availableSlots = 0;
            short totalSlots = inventory.getCapacity();
            for (short i = 0; i < totalSlots; i++) {
                var stack = inventory.getItemStack(i);
                if (stack == null || stack.isEmpty()) {
                    availableSlots++;
                }
            }

            return availableSlots >= slots;
        } catch (Exception e) {
            error("Error checking inventory slots for player " + player.getName(), e);
            return false;
        }
    }

    @Override
    public boolean executeCommand(QueuedCommand command) {
        try {
            String parsedCommand = command.getParsedCommand();
            
            // If command is for a specific player, and they're online, execute on that player
            if (command.getPlayer() != null && command.isOnline()) {
                PlayerRef playerRef = findPlayerByName(command.getPlayer().getName());
                if (playerRef == null) {
                    warn("Player not found: " + command.getPlayer().getName(), "Please check the username and try again.");
                    return false;
                }
                Ref<EntityStore> storeRef = playerRef.getReference();
                if (storeRef == null || !storeRef.isValid()) {
                    warn("Player reference invalid: " + command.getPlayer().getName(), "Please check the username and try again.");
                    return false;
                }

                Store<EntityStore> store = storeRef.getStore();
                Player playerComponent = store.getComponent(storeRef, Player.getComponentType());
                if (playerComponent == null) {
                    warn("Player component not found: " + command.getPlayer().getName(), "Please check the username and try again.");
                    return false;
                }

                // Execute command on the player as the console
                World world = (store.getExternalData()).getWorld();
                world.execute(() -> {
                    HytaleServer.get().getCommandManager().handleCommand(ConsoleSender.INSTANCE, parsedCommand);
                });
                return true;
            }

            // Fallback for offline commands or if player not present
            var commandSender = ConsoleSender.INSTANCE;
            HytaleServer.get().getCommandManager().handleCommand(commandSender, parsedCommand);
            return true;
        } catch (Exception e) {
            error("Error executing command: " + command.getCommand(), e);
            return false;
        }
    }

    @Override
    public boolean isPlayerOnline(String username) {
        try {
            PlayerRef playerRef = findPlayerByName(username);
            return playerRef != null && playerRef.getReference() != null && playerRef.getReference().isValid();
        } catch (Exception e) {
            debug("Error checking if player is online: " + username + " - " + e.getMessage());
            return false;
        }
    }

    @Nullable
    private PlayerRef findPlayerByName(String username) {
        try {
            Universe universe = Universe.get();
            if (universe == null) {
                return null;
            }
            var player = universe.getPlayerByUsername(username, NameMatching.EXACT);
            if (player == null) {
                warn("Player not found: " + username, "Please check the username and try again.");
            }
            return player;
        } catch (Exception e) {
            debug("Error finding player by name: " + username + " - " + e.getMessage());
        }
        return null;
    }

    @Override
    public boolean isDebugModeEnabled() {
        if (config.get() != null) {
            return config.get().debugMode;
        }
        return false;
    }

    public void setDebugMode(boolean value) {
        if (config.get() != null) {
            config.get().debugMode = value;
            config.save();
            if (value) {
                info("Tebex debug mode enabled");
            } else {
                info("Tebex debug mode disabled");
            }
        }
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
        private String buyCommandMessage = "Buy packages at {url}";
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
                    .append(new KeyedCodec<String>("BuyCommandMessage", Codec.STRING),
                            TebexConfig::setBuyCommandMessage, TebexConfig::getBuyCommandMessage).add()
                    .build();

        }
    }
}
