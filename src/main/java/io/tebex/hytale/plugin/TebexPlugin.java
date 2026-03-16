package io.tebex.hytale.plugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.auth.ProfileServiceClient;
import com.hypixel.hytale.server.core.auth.ServerAuthManager;
import com.hypixel.hytale.server.core.auth.SessionServiceClient;
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
import io.tebex.sdk.headlessapi.HeadlessApi;
import io.tebex.sdk.headlessapi.models.HeadlessCategory;
import io.tebex.sdk.headlessapi.models.HeadlessPackage;
import io.tebex.sdk.headlessapi.models.SidebarModule;
import io.tebex.sdk.headlessapi.models.Webstore;
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
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class TebexPlugin extends JavaPlugin implements IPluginAdapter {
    public static final String VERSION = "{{VERSION}}";
    private static final String THUMBNAIL_CACHE_DIRECTORY = "thumbnail-cache";
    private static final String RUNTIME_THUMBNAIL_DIRECTORY = "UI/Custom/Pages/Assets/TebexStoreThumbnails";
    private static final String RUNTIME_THUMBNAIL_TEXTURE_PREFIX = "UI/Custom/Pages/Assets/TebexStoreThumbnails";
    private static final List<String> RUNTIME_THUMBNAIL_COMPATIBILITY_ALIAS_DIRECTORIES = List.of(
            "TebexStoreThumbnails",
            "Common/TebexStoreThumbnails"
    );
    private static final List<String> LEGACY_RUNTIME_CACHE_DIRECTORIES = List.of("runtime-assets");
    private static final String RUNTIME_THUMBNAIL_PLACEHOLDER = "_placeholder.png";
    private static final int RUNTIME_THUMBNAIL_SIZE = 96;
    private static final int RUNTIME_THUMBNAIL_SIZE_2X = 192;

    // tebex apis
    @Getter private PluginApi pluginApi;
    @Getter private HeadlessApi headlessApi;

    // tebex fields
    @Getter private final Config<TebexConfig> config;
    @Nullable @Getter private ServerInformation tebexServerInfo;
    @Nullable @Getter private Webstore headlessWebstore;
    @Setter private long nextCheckQueue;
    private long nextSendPlayerEvents;
    private long nextSendServerEvents;
    private final CopyOnWriteArrayList<PluginEvent> pluginEvents = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ServerEvent> serverEvents = new CopyOnWriteArrayList<>();
    @Getter private final ConcurrentHashMap<Integer, Category> categoriesCache = new ConcurrentHashMap<>();
    @Getter private final ConcurrentHashMap<Integer, Package> packagesCache = new ConcurrentHashMap<>();
    @Getter private CopyOnWriteArrayList<CommunityGoal> communityGoalsCache = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Integer, String> completedCommands = new ConcurrentHashMap<>();
    private boolean warnedMissingHeadlessToken = false;
    private boolean warnedHeadlessAccountMismatch = false;
    private boolean loggedInformationPayload = false;
    private String configuredHeadlessPrivateKey = "";
    private final ConcurrentHashMap<Integer, String> packageThumbnailSources = new ConcurrentHashMap<>();
    private final HttpClient thumbnailHttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

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
        headlessApi = new HeadlessApi(this);

        String envSecretKey = System.getenv("TEBEX_SECRET_KEY"); // to auth plugin api
        String configSecretKey = this.config != null && config.get() != null ? config.get().getSecretKey() : null;
        String envHeadlessPrivateKey = System.getenv("TEBEX_HEADLESS_PRIVATE_KEY");
        String configHeadlessPrivateKey = this.config != null && config.get() != null ? config.get().getHeadlessPrivateKey() : null;

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

        String headlessPrivateKey = "";
        if (envHeadlessPrivateKey != null && !envHeadlessPrivateKey.isBlank()) {
            info("Using TEBEX_HEADLESS_PRIVATE_KEY environment variable");
            headlessPrivateKey = envHeadlessPrivateKey;
        } else if (configHeadlessPrivateKey != null && !configHeadlessPrivateKey.isBlank()) {
            headlessPrivateKey = configHeadlessPrivateKey;
        }

        configuredHeadlessPrivateKey = headlessPrivateKey;
        headlessApi.setCredentials("", configuredHeadlessPrivateKey);
        info("Loading Tebex webstore...");
        this.refreshServerInfo(true); // will set server to null if failed
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
            if (System.currentTimeMillis() >= nextCheckQueue && getTebexServerInfo() != null) {
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
            this.getCommandRegistry().registerCommand(new BuyCommand(buyName));
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

            // if growing too large on exceptions (API failure), dump events
            if (pluginEvents.size() > 10) {
                pluginEvents.clear();
            }
        }
    }

    public void refreshServerInfo() {
        refreshServerInfo(false);
    }

    public void refreshServerInfo(boolean allowThumbnailDownload) {
        try {
            String serverInfoRaw = pluginApi.getServerInformationRaw();
            ServerInformation serverInfo = PluginApi.GSON.fromJson(serverInfoRaw, ServerInformation.class);
            this.tebexServerInfo = serverInfo;
            if (!loggedInformationPayload) {
                loggedInformationPayload = true;
                info("Plugin API /information response: " + serverInfoRaw);
            }

            String informationPublicToken = serverInfo != null && serverInfo.getAccount() != null ? serverInfo.getAccount().getPublicToken() : null;
            if (informationPublicToken != null && !informationPublicToken.isBlank()) {
                headlessApi.setCredentials(informationPublicToken, configuredHeadlessPrivateKey);
            } else {
                headlessApi.setCredentials("", configuredHeadlessPrivateKey);
            }
        } catch (Exception e) {
            error("Failed to refresh server info: " + e.getMessage(), e);
            this.tebexServerInfo = null;
            clearStoreCaches();
            return;
        }

        try {
            refreshStoreDataFromHeadlessApi(allowThumbnailDownload);
        } catch (IllegalStateException e) {
            if (!warnedMissingHeadlessToken) {
                warnedMissingHeadlessToken = true;
                warnNoLog(
                        "Headless API public token is missing from Plugin API /information. Falling back to Plugin API store listing.",
                        "Ensure Tebex Plugin API /information includes account.public_token for this server key."
                );
            }
            refreshStoreDataFromPluginApiFallback();
        } catch (Exception e) {
            warn(
                    "Failed to refresh Headless API store data: " + e.getMessage(),
                    "Falling back to Plugin API store listing for this refresh cycle."
            );
            refreshStoreDataFromPluginApiFallback();
        }
    }

    private void refreshStoreDataFromPluginApiFallback() {
        try {
            packagesCache.clear();
            pluginApi.getPackages().forEach(p -> packagesCache.put(p.getId(), p));

            var remoteCategories = pluginApi.getCategories();
            remoteCategories.sort(Comparator.comparingInt(Category::getOrder).thenComparingInt(Category::getId));
            categoriesCache.clear();
            remoteCategories.forEach(c -> categoriesCache.put(c.getId(), c));

            communityGoalsCache = new CopyOnWriteArrayList<>(pluginApi.getCommunityGoals());
            headlessWebstore = null;
            debug("Store data source=Plugin API, Packages=" + packagesCache.size() + ", Categories=" + categoriesCache.size() + ", Community Goals=" + communityGoalsCache.size());
        } catch (Exception e) {
            error("Failed to refresh fallback Plugin API store data: " + e.getMessage(), e);
            clearStoreCaches();
        }
    }

    private void refreshStoreDataFromHeadlessApi(boolean allowThumbnailDownload) throws IOException, InterruptedException {
        if (!headlessApi.hasPublicToken()) {
            throw new IllegalStateException("Headless public token is missing from Plugin API /information.");
        }

        debug("Downloading store info from Headless API...");
        Webstore webstore = headlessApi.getWebstore();
        List<HeadlessPackage> headlessPackages = headlessApi.getPackages();
        List<HeadlessCategory> headlessCategories = headlessApi.getCategoriesIncludingPackages();
        Map<Integer, String> thumbnailTexturePaths = cacheHeadlessPackageThumbnails(headlessPackages, allowThumbnailDownload);

        ConcurrentHashMap<Integer, Package> newPackages = new ConcurrentHashMap<>();
        for (HeadlessPackage headlessPackage : headlessPackages) {
            String thumbnailTexturePath = thumbnailTexturePaths.get(headlessPackage.getId());
            Package pluginPackage = toPluginPackage(headlessPackage, thumbnailTexturePath);
            newPackages.put(pluginPackage.getId(), pluginPackage);
        }

        headlessCategories.sort(Comparator.comparingInt(HeadlessCategory::getOrder).thenComparingInt(HeadlessCategory::getId));
        ConcurrentHashMap<Integer, Category> newCategories = new ConcurrentHashMap<>();
        for (HeadlessCategory headlessCategory : headlessCategories) {
            Category pluginCategory = toPluginCategory(headlessCategory, thumbnailTexturePaths);
            newCategories.put(pluginCategory.getId(), pluginCategory);
        }

        int accountId = tebexServerInfo == null ? 0 : tebexServerInfo.getAccount().getId();
        if (webstore != null && webstore.getId() > 0 && accountId > 0) {
            if (webstore.getId() != accountId) {
                if (!warnedHeadlessAccountMismatch) {
                    warnedHeadlessAccountMismatch = true;
                    warnNoLog(
                            "Headless token from Plugin API /information appears to target a different Tebex store than SecretKey.",
                            "Verify the server key linkage in Tebex so /information returns account.public_token for the same store."
                    );
                }
            } else {
                warnedHeadlessAccountMismatch = false;
            }
        }
        // Community goals are intentionally skipped for now when using Headless API.
        CopyOnWriteArrayList<CommunityGoal> newGoals = new CopyOnWriteArrayList<>();

        packagesCache.clear();
        packagesCache.putAll(newPackages);
        categoriesCache.clear();
        categoriesCache.putAll(newCategories);
        communityGoalsCache = newGoals;
        headlessWebstore = webstore;
        debug("Store data source=Headless API, Packages=" + packagesCache.size() + ", Categories=" + categoriesCache.size() + ", Community Goals=" + communityGoalsCache.size());
    }

    private synchronized void ensureRuntimeThumbnailWorkspace() throws IOException {
        cleanupLegacyRuntimeThumbnailDirectory();
        Files.createDirectories(runtimeThumbnailDirectory());
        ensurePlaceholderThumbnailExists();
    }

    @Nonnull
    private Path runtimeAssetPackRoot() {
        return getDataDirectory().resolve(THUMBNAIL_CACHE_DIRECTORY);
    }

    @Nonnull
    private Path runtimeThumbnailDirectory() {
        return runtimeAssetRelativePath(RUNTIME_THUMBNAIL_DIRECTORY);
    }

    @Nonnull
    private Path runtimeAssetRelativePath(@Nonnull String relativeDirectory) {
        Path directory = runtimeAssetPackRoot();
        for (String segment : relativeDirectory.split("/")) {
            if (!segment.isBlank()) {
                directory = directory.resolve(segment);
            }
        }
        return directory;
    }

    @Nonnull
    private Path runtimeThumbnailPath(@Nonnull String fileName) {
        return runtimeThumbnailDirectory().resolve(fileName);
    }

    @Nonnull
    private static String runtimeThumbnailTexturePath(@Nonnull String fileName) {
        return RUNTIME_THUMBNAIL_TEXTURE_PREFIX + "/" + fileName;
    }

    @Nonnull
    private static String runtimeThumbnail2xFileName(@Nonnull String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".png")) {
            return fileName + "@2x";
        }
        return fileName.substring(0, fileName.length() - 4) + "@2x.png";
    }

    private void cleanupLegacyRuntimeThumbnailDirectory() {
        for (String legacyDirectoryPath : LEGACY_RUNTIME_CACHE_DIRECTORIES) {
            Path legacyDirectory = getDataDirectory().resolve(legacyDirectoryPath);
            if (!Files.exists(legacyDirectory)) {
                continue;
            }

            try (Stream<Path> stream = Files.walk(legacyDirectory)) {
                List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
                for (Path path : paths) {
                    Files.deleteIfExists(path);
                }
                info("Removed legacy runtime thumbnail cache at " + legacyDirectory.toAbsolutePath());
            } catch (IOException e) {
                warnNoLog(
                        "Failed to remove legacy Tebex runtime thumbnail cache.",
                        "Legacy thumbnail aliases may continue to show until files are cleaned. Path: " + legacyDirectory.toAbsolutePath()
                );
                error("Failed to cleanup legacy runtime thumbnail directory " + legacyDirectory.toAbsolutePath(), e);
            }
        }
    }

    private void ensurePlaceholderThumbnailExists() throws IOException {
        Path placeholderPath = runtimeThumbnailPath(RUNTIME_THUMBNAIL_PLACEHOLDER);
        String placeholder2xFileName = runtimeThumbnail2xFileName(RUNTIME_THUMBNAIL_PLACEHOLDER);
        Path placeholder2xPath = runtimeThumbnailPath(placeholder2xFileName);
        if (Files.isRegularFile(placeholderPath) && Files.isRegularFile(placeholder2xPath)) {
            syncRuntimeThumbnailAliases(placeholderPath, RUNTIME_THUMBNAIL_PLACEHOLDER);
            syncRuntimeThumbnailAliases(placeholder2xPath, placeholder2xFileName);
            return;
        }

        BufferedImage image = new BufferedImage(RUNTIME_THUMBNAIL_SIZE, RUNTIME_THUMBNAIL_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(15, 27, 45, 255));
            graphics.fillRect(0, 0, RUNTIME_THUMBNAIL_SIZE, RUNTIME_THUMBNAIL_SIZE);
            graphics.setColor(new Color(31, 49, 74, 255));
            graphics.fillRect(6, 6, RUNTIME_THUMBNAIL_SIZE - 12, RUNTIME_THUMBNAIL_SIZE - 12);
            graphics.setColor(new Color(150, 170, 190, 255));
            graphics.drawRect(6, 6, RUNTIME_THUMBNAIL_SIZE - 13, RUNTIME_THUMBNAIL_SIZE - 13);
            graphics.drawLine(6, 6, RUNTIME_THUMBNAIL_SIZE - 7, RUNTIME_THUMBNAIL_SIZE - 7);
            graphics.drawLine(6, RUNTIME_THUMBNAIL_SIZE - 7, RUNTIME_THUMBNAIL_SIZE - 7, 6);
        } finally {
            graphics.dispose();
        }

        if (!ImageIO.write(image, "png", placeholderPath.toFile())) {
            throw new IOException("No PNG writer available for runtime placeholder thumbnail.");
        }
        BufferedImage image2x = normalizeThumbnailImage(image, RUNTIME_THUMBNAIL_SIZE_2X, RUNTIME_THUMBNAIL_SIZE_2X);
        if (!ImageIO.write(image2x, "png", placeholder2xPath.toFile())) {
            throw new IOException("No PNG writer available for runtime placeholder thumbnail @2x.");
        }
        syncRuntimeThumbnailAliases(placeholderPath, RUNTIME_THUMBNAIL_PLACEHOLDER);
        syncRuntimeThumbnailAliases(placeholder2xPath, placeholder2xFileName);
    }

    @Nonnull
    private Map<Integer, String> cacheHeadlessPackageThumbnails(
            @Nonnull List<HeadlessPackage> headlessPackages,
            boolean allowDownload
    ) {
        ConcurrentHashMap<Integer, String> texturePaths = new ConcurrentHashMap<>();
        Set<Integer> currentPackageIds = new HashSet<>();

        try {
            ensureRuntimeThumbnailWorkspace();
        } catch (Exception e) {
            error("Failed to initialize runtime thumbnail workspace", e);
            return texturePaths;
        }

        String placeholderTexture = runtimeThumbnailTexturePath(RUNTIME_THUMBNAIL_PLACEHOLDER);
        boolean skippedRuntimeDownloadLogged = false;
        for (HeadlessPackage headlessPackage : headlessPackages) {
            int packageId = headlessPackage.getId();
            currentPackageIds.add(packageId);

            String sourceImageUrl = resolveImage(headlessPackage);
            if (sourceImageUrl == null || sourceImageUrl.isBlank()) {
                texturePaths.put(packageId, placeholderTexture);
                packageThumbnailSources.remove(packageId);
                continue;
            }

            String fileName = packageId + ".png";
            String fileName2x = runtimeThumbnail2xFileName(fileName);
            Path thumbnailPath = runtimeThumbnailPath(fileName);
            Path thumbnailPath2x = runtimeThumbnailPath(fileName2x);
            if (allowDownload) {
                try {
                    cachePackageThumbnail(packageId, sourceImageUrl);
                    texturePaths.put(packageId, runtimeThumbnailTexturePath(fileName));
                    continue;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    debug("Thumbnail download interrupted for package " + packageId + ": " + e.getMessage());
                    texturePaths.put(packageId, placeholderTexture);
                    break;
                } catch (Exception e) {
                    debug("Failed to cache thumbnail for package " + packageId + ": " + e.getMessage());
                }
            } else if (!skippedRuntimeDownloadLogged) {
                skippedRuntimeDownloadLogged = true;
                debug("Skipping runtime thumbnail downloads during scheduled refresh; restart server to recache package images.");
            }

            if (Files.isRegularFile(thumbnailPath)) {
                try {
                    ensureThumbnail2xVariant(thumbnailPath, thumbnailPath2x);
                    syncRuntimeThumbnailAliases(thumbnailPath, fileName);
                    syncRuntimeThumbnailAliases(thumbnailPath2x, fileName2x);
                    texturePaths.put(packageId, runtimeThumbnailTexturePath(fileName));
                } catch (Exception e) {
                    debug("Failed to publish cached thumbnail alias for package " + packageId + ": " + e.getMessage());
                    texturePaths.put(packageId, placeholderTexture);
                }
            } else {
                texturePaths.put(packageId, placeholderTexture);
            }
        }

        packageThumbnailSources.keySet().removeIf(packageId -> !currentPackageIds.contains(packageId));
        return texturePaths;
    }

    private void cachePackageThumbnail(int packageId, @Nonnull String sourceImageUrl) throws IOException, InterruptedException {
        String fileName = packageId + ".png";
        String fileName2x = runtimeThumbnail2xFileName(fileName);
        Path outputPath = runtimeThumbnailPath(fileName);
        Path outputPath2x = runtimeThumbnailPath(fileName2x);
        String trimmedSource = sourceImageUrl.trim();
        String cachedSource = packageThumbnailSources.get(packageId);
        if (trimmedSource.equals(cachedSource) && Files.isRegularFile(outputPath) && Files.isRegularFile(outputPath2x)) {
            syncRuntimeThumbnailAliases(outputPath, fileName);
            syncRuntimeThumbnailAliases(outputPath2x, fileName2x);
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(trimmedSource))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "image/*")
                .header("User-Agent", getHttpProvider().getUserAgent())
                .GET()
                .build();

        HttpResponse<byte[]> response = thumbnailHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Thumbnail request failed with status " + response.statusCode() + " for package " + packageId);
        }

        BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(response.body()));
        if (sourceImage == null) {
            throw new IOException("Unsupported image format for package " + packageId);
        }

        BufferedImage thumbnail = normalizeThumbnailImage(sourceImage, RUNTIME_THUMBNAIL_SIZE, RUNTIME_THUMBNAIL_SIZE);
        BufferedImage thumbnail2x = normalizeThumbnailImage(sourceImage, RUNTIME_THUMBNAIL_SIZE_2X, RUNTIME_THUMBNAIL_SIZE_2X);
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (!ImageIO.write(thumbnail, "png", outputPath.toFile())) {
            throw new IOException("No PNG writer available while caching package thumbnail " + packageId);
        }
        if (!ImageIO.write(thumbnail2x, "png", outputPath2x.toFile())) {
            throw new IOException("No PNG writer available while caching package thumbnail @2x " + packageId);
        }

        syncRuntimeThumbnailAliases(outputPath, fileName);
        syncRuntimeThumbnailAliases(outputPath2x, fileName2x);
        packageThumbnailSources.put(packageId, trimmedSource);
    }

    private void ensureThumbnail2xVariant(@Nonnull Path sourcePath, @Nonnull Path sourcePath2x) throws IOException {
        if (!Files.isRegularFile(sourcePath) || Files.isRegularFile(sourcePath2x)) {
            return;
        }

        BufferedImage base = ImageIO.read(sourcePath.toFile());
        if (base == null) {
            return;
        }

        BufferedImage scaled = normalizeThumbnailImage(base, RUNTIME_THUMBNAIL_SIZE_2X, RUNTIME_THUMBNAIL_SIZE_2X);
        if (!ImageIO.write(scaled, "png", sourcePath2x.toFile())) {
            throw new IOException("No PNG writer available while generating cached thumbnail @2x variant.");
        }
    }

    private void syncRuntimeThumbnailAliases(@Nonnull Path sourcePath, @Nonnull String fileName) throws IOException {
        if (!Files.isRegularFile(sourcePath)) {
            return;
        }

        for (String aliasDirectory : RUNTIME_THUMBNAIL_COMPATIBILITY_ALIAS_DIRECTORIES) {
            Path aliasPath = runtimeAssetRelativePath(aliasDirectory).resolve(fileName);
            Path parent = aliasPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(sourcePath, aliasPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Nonnull
    public synchronized JarRewriteTestResult rewriteOwnJarForTest() {
        Path jarPath = resolveOwnJarPath();
        if (jarPath == null) {
            return new JarRewriteTestResult(
                    false,
                    "Plugin is not running from a .jar file, so self-rewrite test is not available in this environment."
            );
        }

        try {
            ensureRuntimeThumbnailWorkspace();
        } catch (Exception e) {
            error("Failed to initialize runtime thumbnail workspace before jar rewrite test", e);
            return new JarRewriteTestResult(false, "Failed to prepare runtime thumbnail workspace: " + e.getMessage());
        }

        LinkedHashSet<Path> thumbnailFiles = new LinkedHashSet<>();
        try (Stream<Path> stream = Files.list(runtimeThumbnailDirectory())) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName() != null)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted()
                    .forEach(thumbnailFiles::add);
        } catch (IOException e) {
            error("Failed to list runtime thumbnails for jar rewrite test", e);
            return new JarRewriteTestResult(false, "Failed to read runtime thumbnail files: " + e.getMessage());
        }

        if (thumbnailFiles.isEmpty()) {
            return new JarRewriteTestResult(false, "No runtime thumbnails found to inject into the jar.");
        }

        Path tempJarPath = jarPath.resolveSibling(jarPath.getFileName() + ".rewrite.tmp");
        Path backupJarPath = jarPath.resolveSibling(jarPath.getFileName() + ".rewrite.bak");

        try {
            Files.deleteIfExists(tempJarPath);
        } catch (IOException ignored) {
            // Best-effort cleanup before we start.
        }

        try (
                ZipInputStream input = new ZipInputStream(Files.newInputStream(jarPath));
                ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(tempJarPath))
        ) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');
                if (isRuntimeThumbnailJarEntry(entryName)) {
                    continue;
                }
                copyZipEntry(input, output, entry);
            }

            for (Path thumbnailFile : thumbnailFiles) {
                String fileName = thumbnailFile.getFileName().toString();
                byte[] bytes = Files.readAllBytes(thumbnailFile);
                writeZipEntry(output, "Common/UI/Custom/Pages/Assets/TebexStoreThumbnails/" + fileName, bytes);
                writeZipEntry(output, "UI/Custom/Pages/Assets/TebexStoreThumbnails/" + fileName, bytes);
                writeZipEntry(output, "Common/TebexStoreThumbnails/" + fileName, bytes);
                writeZipEntry(output, "TebexStoreThumbnails/" + fileName, bytes);
            }
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tempJarPath);
            } catch (IOException ignored) {
                // No-op
            }
            error("Failed to build rewritten jar candidate at " + tempJarPath.toAbsolutePath(), e);
            return new JarRewriteTestResult(false, "Failed while rebuilding jar contents: " + e.getMessage());
        }

        try {
            Files.copy(jarPath, backupJarPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tempJarPath);
            } catch (IOException ignored) {
                // No-op
            }
            error("Failed to create backup jar before rewrite test at " + backupJarPath.toAbsolutePath(), e);
            return new JarRewriteTestResult(false, "Failed to create backup jar before rewrite: " + e.getMessage());
        }

        try {
            try {
                Files.move(tempJarPath, jarPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempJarPath, jarPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            Path stagedJarPath = jarPath.resolveSibling(jarPath.getFileName() + ".rewrite.ready.jar");
            try {
                Files.move(tempJarPath, stagedJarPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception stageException) {
                try {
                    Files.deleteIfExists(tempJarPath);
                } catch (IOException ignored) {
                    // No-op
                }
                error("Failed to stage rewritten plugin jar at " + stagedJarPath.toAbsolutePath(), stageException);
                error("Failed to replace running plugin jar at " + jarPath.toAbsolutePath(), e);
                return new JarRewriteTestResult(
                        false,
                        "Jar replacement failed (likely file lock) and staging also failed. Backup at: " + backupJarPath.toAbsolutePath()
                );
            }

            error("Failed to replace running plugin jar at " + jarPath.toAbsolutePath(), e);
            warnNoLog(
                    "Running plugin jar is locked; staged patched jar for next restart.",
                    "Disable current jar and promote staged jar after shutdown: current=" + jarPath.getFileName()
                            + ", staged=" + stagedJarPath.getFileName()
            );
            return new JarRewriteTestResult(
                    true,
                    "Jar is locked while running. Staged patched jar: " + stagedJarPath.toAbsolutePath()
                            + ". After stopping the server, rename current jar to .disabled and rename staged jar to " + jarPath.getFileName() + "."
            );
        }

        info("Jar rewrite test succeeded. Updated " + thumbnailFiles.size() + " runtime thumbnail(s) in " + jarPath.toAbsolutePath());
        warnNoLog(
                "Plugin jar was rewritten successfully for test purposes.",
                "A full server restart is required before the updated jar resources can be used."
        );
        return new JarRewriteTestResult(
                true,
                "Jar rewrite succeeded. Restart the server to test resources from inside the rewritten jar."
        );
    }

    @Nullable
    private static Path resolveOwnJarPath() {
        try {
            URI location = TebexPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(location);
            if (Files.isDirectory(path)) {
                return null;
            }
            String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!fileName.endsWith(".jar")) {
                return null;
            }
            return path;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isRuntimeThumbnailJarEntry(@Nonnull String entryName) {
        return entryName.startsWith("Common/UI/Custom/Pages/Assets/TebexStoreThumbnails/")
                || entryName.startsWith("UI/Custom/Pages/Assets/TebexStoreThumbnails/")
                || entryName.startsWith("Common/TebexStoreThumbnails/")
                || entryName.startsWith("TebexStoreThumbnails/");
    }

    private static void copyZipEntry(
            @Nonnull ZipInputStream input,
            @Nonnull ZipOutputStream output,
            @Nonnull ZipEntry source
    ) throws IOException {
        ZipEntry target = new ZipEntry(source.getName());
        if (source.getTime() > 0) {
            target.setTime(source.getTime());
        }
        if (source.getComment() != null) {
            target.setComment(source.getComment());
        }
        output.putNextEntry(target);

        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        output.closeEntry();
    }

    private static void writeZipEntry(
            @Nonnull ZipOutputStream output,
            @Nonnull String entryName,
            @Nonnull byte[] bytes
    ) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(System.currentTimeMillis());
        output.putNextEntry(entry);
        output.write(bytes);
        output.closeEntry();
    }

    @Nonnull
    private static BufferedImage normalizeThumbnailImage(@Nonnull BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage output = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(new Color(15, 27, 45, 255));
            graphics.fillRect(0, 0, targetWidth, targetHeight);

            int sourceWidth = Math.max(1, source.getWidth());
            int sourceHeight = Math.max(1, source.getHeight());
            double scale = Math.min((double) targetWidth / sourceWidth, (double) targetHeight / sourceHeight);

            int drawWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
            int drawHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
            int offsetX = (targetWidth - drawWidth) / 2;
            int offsetY = (targetHeight - drawHeight) / 2;
            graphics.drawImage(source, offsetX, offsetY, drawWidth, drawHeight, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private static Category toPluginCategory(
            @Nonnull HeadlessCategory headlessCategory,
            @Nonnull Map<Integer, String> thumbnailTexturePaths
    ) {
        List<CategoryPackage> pluginPackages = new ArrayList<>();
        List<HeadlessPackage> headlessPackages = headlessCategory.getPackages();
        if (headlessPackages != null) {
            for (int i = 0; i < headlessPackages.size(); i++) {
                HeadlessPackage headlessPackage = headlessPackages.get(i);
                pluginPackages.add(
                        toPluginCategoryPackage(
                                headlessPackage,
                                i,
                                thumbnailTexturePaths.get(headlessPackage.getId())
                        )
                );
            }
        }

        return new Category(
                headlessCategory.getId(),
                headlessCategory.getOrder(),
                safeText(headlessCategory.getName()),
                null,
                false,
                pluginPackages
        );
    }

    private static CategoryPackage toPluginCategoryPackage(
            @Nonnull HeadlessPackage headlessPackage,
            int order,
            @Nullable String thumbnailTexturePath
    ) {
        double discount = headlessPackage.getDiscount();
        CategoryPackage.Sale sale = new CategoryPackage.Sale(discount > 0, discount);
        return new CategoryPackage(
                headlessPackage.getId(),
                order,
                safeText(headlessPackage.getName()),
                resolvePrice(headlessPackage),
                safeText(headlessPackage.getDescription()),
                chooseImage(thumbnailTexturePath, resolveImage(headlessPackage)),
                null,
                sale
        );
    }

    private static Package toPluginPackage(
            @Nonnull HeadlessPackage headlessPackage,
            @Nullable String thumbnailTexturePath
    ) {
        HeadlessPackage.Category sourceCategory = headlessPackage.getCategory();
        Package.Category pluginCategory = sourceCategory == null
                ? new Package.Category(0, "")
                : new Package.Category(sourceCategory.getId(), safeText(sourceCategory.getName()));

        String description = safeText(headlessPackage.getDescription());
        return new Package(
                headlessPackage.getId(),
                safeText(headlessPackage.getName()),
                description,
                description,
                chooseImage(thumbnailTexturePath, resolveImage(headlessPackage)),
                resolvePrice(headlessPackage),
                0,
                null,
                safeText(headlessPackage.getType()),
                pluginCategory,
                0,
                null,
                0,
                null,
                List.of(),
                List.of(),
                false,
                false,
                true,
                null,
                false,
                headlessPackage.isDisableQuantity(),
                false,
                false,
                false,
                false,
                false
        );
    }

    @Nonnull
    private static List<CommunityGoal> toPluginCommunityGoals(@Nonnull List<SidebarModule> sidebarModules, int accountId) {
        List<CommunityGoal> goals = new ArrayList<>();
        for (SidebarModule module : sidebarModules) {
            if (module == null || !"community_goal".equalsIgnoreCase(module.getType()) || module.getData() == null) {
                continue;
            }

            String name = getJsonString(module.getData(), "header", "Community Goal");
            double target = getJsonDouble(module.getData(), "target", 0d);
            double percentage = getJsonDouble(module.getData(), "percentage", 0d);
            double totalPayments = getJsonDouble(module.getData(), "total_payments", Double.NaN);
            double current = !Double.isNaN(totalPayments)
                    ? totalPayments
                    : (target > 0 ? (percentage / 100d) * target : percentage);
            int timesAchieved = (int) Math.round(getJsonDouble(module.getData(), "times_achieved", 0d));
            CommunityGoal.Status status = percentage >= 100d ? CommunityGoal.Status.COMPLETED : CommunityGoal.Status.ACTIVE;

            goals.add(new CommunityGoal(
                    module.getId(),
                    module.getStartTime(),
                    module.getEndTime(),
                    accountId,
                    name,
                    "Imported from Headless sidebar module",
                    null,
                    target,
                    current,
                    0,
                    null,
                    timesAchieved,
                    status,
                    0
            ));
        }
        return goals;
    }

    private static double resolvePrice(@Nonnull HeadlessPackage headlessPackage) {
        if (headlessPackage.getTotalPrice() > 0d) {
            return headlessPackage.getTotalPrice();
        }
        return headlessPackage.getBasePrice();
    }

    @Nullable
    private static String resolveImage(@Nonnull HeadlessPackage headlessPackage) {
        if (headlessPackage.getImage() != null && !headlessPackage.getImage().isBlank()) {
            return headlessPackage.getImage();
        }
        if (headlessPackage.getMedia() != null) {
            for (HeadlessPackage.PackageMedia media : headlessPackage.getMedia()) {
                if (media != null && media.getUrl() != null && !media.getUrl().isBlank()) {
                    return media.getUrl();
                }
            }
        }
        return null;
    }

    @Nullable
    private static String chooseImage(@Nullable String preferredImage, @Nullable String fallbackImage) {
        if (preferredImage != null && !preferredImage.isBlank()) {
            return preferredImage;
        }
        if (fallbackImage != null && !fallbackImage.isBlank()) {
            return fallbackImage;
        }
        return null;
    }

    @Nonnull
    private static String safeText(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value;
    }

    private static double getJsonDouble(@Nonnull com.google.gson.JsonObject jsonObject, @Nonnull String key, double fallback) {
        if (!jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return jsonObject.get(key).getAsDouble();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Nonnull
    private static String getJsonString(@Nonnull com.google.gson.JsonObject jsonObject, @Nonnull String key, @Nonnull String fallback) {
        if (!jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            String value = jsonObject.get(key).getAsString();
            return value == null || value.isBlank() ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void clearStoreCaches() {
        packagesCache.clear();
        categoriesCache.clear();
        communityGoalsCache.clear();
        packageThumbnailSources.clear();
        headlessWebstore = null;
    }

    @Nonnull
    public String getStoreUrl() {
        if (headlessWebstore != null && headlessWebstore.getWebstoreUrl() != null && !headlessWebstore.getWebstoreUrl().isBlank()) {
            return headlessWebstore.getWebstoreUrl();
        }
        if (tebexServerInfo == null || tebexServerInfo.getAccount() == null || tebexServerInfo.getAccount().getDomain() == null) {
            return "";
        }

        String domain = tebexServerInfo.getAccount().getDomain();
        if (domain.startsWith("https://") || domain.startsWith("http://")) {
            return domain;
        }
        return "https://" + domain;
    }

    @Nonnull
    public String getStoreName() {
        if (headlessWebstore != null && headlessWebstore.getName() != null && !headlessWebstore.getName().isBlank()) {
            return headlessWebstore.getName();
        }
        if (tebexServerInfo != null && tebexServerInfo.getAccount() != null && tebexServerInfo.getAccount().getName() != null) {
            return tebexServerInfo.getAccount().getName();
        }
        return "Tebex Store";
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

        ServerAuthManager authManager = ServerAuthManager.getInstance();
        ProfileServiceClient profileService = authManager.getProfileServiceClient();
        var authed = authManager.getSessionToken() != null;
        if (!authed) {
            warnNoLog("server is not authenticated with hytale (no session token), Tebex cannot look up offline player uuids", "Use the /auth commands to see authentication options");
        }

        for (QueuedOfflineCommand offlineCommand : offlineCommands.getCommands()) {
            // check we haven't already completed this command
            if (completedCommands.containsKey(offlineCommand.getId())) {
                continue;
            }

            var offlineUuid = offlineCommand.getPlayer().getUuid(); // will be incorrect for offline commands
            if (authed) { // look up the player's uuid on the profile service if the server is authed
                try {
                    var targetPlayerProfile = profileService.getProfileByUsername(offlineCommand.getPlayer().getName(), authManager.getSessionToken());
                    offlineUuid = targetPlayerProfile.getUuid().toString();
                } catch (Exception e) {
                    error("Failed to retrieve offline player uuid: " + offlineCommand.getPlayer().getName(), e);
                }
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

                final String finalOfflineUUID = offlineUuid;
                tasks.schedule(() -> {
                    info(String.format("Executing scheduled offline command (ID:%d) '%s' on %s...", offlineCommand.getId(), offlineCommand.getCommand(), offlineCommand.getPlayer().getName()));
                    boolean success = executeCommand(offlineCommand, offlineCommand.getPlayer(), false);
                    if (!success) {
                        warn(String.format("Scheduled offline command (ID:%d) '%s' could not be executed on %s", offlineCommand.getId(), offlineCommand.getCommand(), offlineCommand.getPlayer().getName()), "Hytale failed to execute the command. Check the command syntax.");
                        return;
                    }
                    // for scheduled commands, add immediately to completed and purge
                    completedCommands.put(offlineCommand.getId(), offlineCommand.getParsedCommand(finalOfflineUUID));
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
                var success = executeCommand(offlineCommand, offlineCommand.getPlayer(), false);
                if (!success) {
                    warn(String.format("Offline command '%s' could not be executed on %s", offlineCommand.getCommand(), offlineCommand.getPlayer().getName()), "Hytale failed to execute the command. Check the command syntax.");
                    continue; // process the next command
                }

                // successful execution, save command for deletion from the queue
                completedCommands.put(offlineCommand.getId(), offlineCommand.getParsedCommand(offlineUuid));
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

        for (QueuedPlayer tebexPlayer : commandQueueResponse.getPlayers()) {
            try {
                // make sure the player is online before we make a request to get their commands
                if (!isPlayerOnline(tebexPlayer.getName())) {
                    debug(String.format("Player %s has commands available but is not online, skipping...", tebexPlayer.getName()));
                    continue;
                }

                // player is online, so check for their online commands that are due
                var onlineCommands = pluginApi.getOnlineCommands(tebexPlayer.getId());
                for (QueuedOnlineCommand onlineCommand : onlineCommands.getCommands()) {
                    // guard against duplicate executions
                    if (completedCommands.containsKey(onlineCommand.getId())) {
                        continue;
                    }

                    // check command conditions - check inventory slots before applying the command
                    Integer requiredSlots = onlineCommand.getConditions().getRequiredSlots();
                    if (requiredSlots != null && requiredSlots > 0) {
                        if (!playerHasInventorySlotsAvailable(tebexPlayer, requiredSlots)) {
                            warn(String.format("Player " + tebexPlayer.getName() + " does not have enough inventory slots to execute command '%s'. Need: %d",
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
                                tebexPlayer.getName(),
                                onlineCommand.getConditions().getDelay()
                        ));

                        tasks.schedule(() -> {
                            info(String.format("Executing scheduled online command (ID:%d) '%s' on %s...", onlineCommand.getId(), onlineCommand.getCommand(), tebexPlayer.getName()));
                            boolean success = executeCommand(onlineCommand, tebexPlayer, true);
                            if (!success) {
                                warn(String.format("Scheduled online command (ID:%d) '%s' could not be executed on %s", onlineCommand.getId(), onlineCommand.getCommand(), tebexPlayer.getName()), "Hytale failed to execute the command. Check the command syntax.");
                                return;
                            }
                            // for scheduled commands, add immediately to completed and purge
                            completedCommands.put(onlineCommand.getId(), onlineCommand.getParsedCommand(tebexPlayer.getName(), tebexPlayer.getUuid()));
                            try {
                                pluginApi.deleteCompletedCommands(completedCommands);
                            } catch (Exception e) {
                                error("Unexpected error while flushing completed commands! This can result in duplicated deliveries!: " + e.getMessage(), e);
                            }
                        }, onlineCommand.getConditions().getDelay(), TimeUnit.SECONDS);
                    } else { // no delay, execute now
                        info(String.format("Executing online command (ID:%d) '%s' on %s...", onlineCommand.getId(), onlineCommand.getCommand(), tebexPlayer.getName()));
                        var success = executeCommand(onlineCommand, tebexPlayer, true);
                        if (!success) {
                            warn(String.format("Online command (ID: %d) '%s' could not be executed on %s", onlineCommand.getId(), onlineCommand.getCommand(), tebexPlayer.getName()), "Hytale failed to execute the command. Check the command syntax.");
                            continue;
                        }
                    }

                    // successful execution, queue the command to be deleted
                    completedCommands.put(onlineCommand.getId(), onlineCommand.getParsedCommand(tebexPlayer.getName(), tebexPlayer.getUuid()));
                }
            } catch (Exception e) {
                error("Unexpected error retrieving online commands for " + tebexPlayer.getName() + "): ", e);
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
        this.headlessWebstore = null;
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

            // Get the world from the store's external data
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();

            // Run the inventory check on the world thread and wait for the result
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            world.execute(() -> {
                try {
                    Player playerComponent = store.getComponent(ref, Player.getComponentType());
                    if (playerComponent == null) {
                        future.complete(false);
                        return;
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
                    future.complete(availableSlots >= slots);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });

            return future.get(5, TimeUnit.SECONDS); // Wait up to 5 seconds for result
        } catch (Exception e) {
            error("Error checking inventory slots for player " + player.getName(), e);
            return false;
        }
    }

    @Override
    public boolean executeCommand(ICommand command, @Nullable QueuedPlayer tebexPlayer, boolean requireOnline) {
        try {
            // If command is for a specific player, and they're online, execute on that player
            if (tebexPlayer != null && requireOnline) {
                PlayerRef hytalePlayerRef = findPlayerByName(tebexPlayer.getName());
                if (hytalePlayerRef == null) {
                    warn("Player not found: " + tebexPlayer.getName(), "Please check the username and try again.");
                    return false;
                }
                Ref<EntityStore> storeRef = hytalePlayerRef.getReference();
                if (storeRef == null || !storeRef.isValid()) {
                    warn("Player reference invalid: " + tebexPlayer.getName(), "Please check the username and try again.");
                    return false;
                }

                // Get the world from the store's external data
                Store<EntityStore> store = storeRef.getStore();
                World world = store.getExternalData().getWorld();

                // Parse the command using the player's real UUID since they are online - the Tebex UUID might be incorrect.
                var parsedOnlineCommand = command.getParsedCommand(tebexPlayer.getName(), hytalePlayerRef.getUuid().toString());

                // Execute the command on the world thread to avoid IllegalStateException
                world.execute(() -> HytaleServer.get().getCommandManager().handleCommand(ConsoleSender.INSTANCE, parsedOnlineCommand));
                return true;
            }

            // Fallback for offline commands or if player not present
            var commandSender = ConsoleSender.INSTANCE;
            ServerAuthManager authManager = ServerAuthManager.getInstance();
            ProfileServiceClient profileService = authManager.getProfileServiceClient();
            var authed = authManager.getSessionToken() != null;

            if (tebexPlayer != null) {
                //Tebex player UUIDs for Hytale may be incorrect, so look up on hytale auth service if available
                var parsedOfflineCommand = command.getParsedCommand(tebexPlayer.getName(), tebexPlayer.getUuid());
                if (authed && command.hasUuidVariables()) {
                    try {
                        var playerHytaleProfile = profileService.getProfileByUsername(tebexPlayer.getName(), authManager.getSessionToken());
                        // set commands to have the profile id from hytale
                        parsedOfflineCommand =  command.getParsedCommand(tebexPlayer.getName(), playerHytaleProfile.getUuid().toString());
                    } catch (Exception e) {
                        error("error checking offline player uuid on hytale, reported UUID might be incorrect!: " +  tebexPlayer.getName(), e);
                    } // fall through to execute the original parsed command
                }

                HytaleServer.get().getCommandManager().handleCommand(commandSender, parsedOfflineCommand);
            } else { // no player for this command, run the raw command
                HytaleServer.get().getCommandManager().handleCommand(commandSender, command.getCommand());
            }
            return true;
        } catch (Exception e) {
            error("Error executing command: " + command, e);
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
            return universe.getPlayerByUsername(username, NameMatching.EXACT_IGNORE_CASE);
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

    public static final class JarRewriteTestResult {
        private final boolean success;
        @Nonnull private final String message;

        public JarRewriteTestResult(boolean success, @Nonnull String message) {
            this.success = success;
            this.message = message;
        }

        public boolean success() {
            return success;
        }

        @Nonnull
        public String message() {
            return message;
        }
    }

    @Data
    public static class TebexConfig {
        public static final BuilderCodec<TebexPlugin.TebexConfig> CODEC;

        private @Nonnull String secretKey = "";
        private String headlessPrivateKey = "";
        private boolean buyCommandEnabled = true;
        private boolean storeBrowserEnabled = true;
        private boolean cartEnabled = true;
        private boolean debugMode = false;
        private String buyCommandMessage = "Buy packages at {url}";
        private String buyCommandName = "buy";

        static {
            CODEC =BuilderCodec.builder(TebexPlugin.TebexConfig.class, TebexPlugin.TebexConfig::new)
                    .append(new KeyedCodec<String>("SecretKey", Codec.STRING),
                            TebexConfig::setSecretKey, TebexConfig::getSecretKey).add()
                    .append(new KeyedCodec<String>("HeadlessPrivateKey", Codec.STRING),
                            TebexConfig::setHeadlessPrivateKey, TebexConfig::getHeadlessPrivateKey).add()
                    .append(new KeyedCodec<String>("BuyCommandName", Codec.STRING),
                            TebexConfig::setBuyCommandName, TebexConfig::getBuyCommandName).add()
                    .append(new KeyedCodec<Boolean>("BuyCommandEnabled", Codec.BOOLEAN),
                            TebexConfig::setBuyCommandEnabled, TebexConfig::isBuyCommandEnabled).add()
                    .append(new KeyedCodec<Boolean>("StoreBrowserEnabled", Codec.BOOLEAN),
                            TebexConfig::setStoreBrowserEnabled, TebexConfig::isStoreBrowserEnabled).add()
                    .append(new KeyedCodec<Boolean>("CartEnabled", Codec.BOOLEAN),
                            TebexConfig::setCartEnabled, TebexConfig::isCartEnabled).add()
                    .append(new KeyedCodec<Boolean>("DebugMode", Codec.BOOLEAN),
                            TebexConfig::setDebugMode, TebexConfig::isDebugMode).add()
                    .append(new KeyedCodec<String>("BuyCommandMessage", Codec.STRING),
                            TebexConfig::setBuyCommandMessage, TebexConfig::getBuyCommandMessage).add()
                    .build();

        }
    }
}
