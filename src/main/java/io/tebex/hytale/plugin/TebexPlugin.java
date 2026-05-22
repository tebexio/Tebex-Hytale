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
import io.tebex.hytale.plugin.qr.QrCode;
import io.tebex.hytale.plugin.qr.QrCodePngRenderer;
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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.SocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
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

public class TebexPlugin extends JavaPlugin implements IPluginAdapter {
    public static final String VERSION = "{{VERSION}}";
    private static final String THUMBNAIL_CACHE_DIRECTORY = "thumbnail-cache";
    private static final String THUMBNAIL_ASSET_PACK_DIRECTORY_NAME = "Tebex_Tebex-Hytale-Thumbnails";
    private static final String THUMBNAIL_ASSET_PACK_MANIFEST = "manifest.json";
    private static final String THUMBNAIL_ASSET_PACK_GROUP = "Tebex";
    private static final String THUMBNAIL_ASSET_PACK_NAME = "Tebex-Hytale-Thumbnails";
    private static final String RUNTIME_PAGE_DIRECTORY = "Common/UI/Custom/Pages";
    private static final String RUNTIME_THUMBNAIL_DIRECTORY = "Common/UI/Custom/Pages/Assets";
    private static final String RUNTIME_THUMBNAIL_TEXTURE_PREFIX = "Assets";
    private static final String RUNTIME_CARD_TEMPLATE_PREFIX = "TebexGeneratedStoreCard_";
    private static final String RUNTIME_CARD_WIDE_TEMPLATE_PREFIX = "TebexGeneratedStoreCardWide_";
    private static final String RUNTIME_PACKAGE_CARD_TEMPLATE_PREFIX = "TebexGeneratedPackageCard_";
    private static final String RUNTIME_PACKAGE_CARD_WIDE_TEMPLATE_PREFIX = "TebexGeneratedPackageCardWide_";
    private static final String RUNTIME_CART_CARD_TEMPLATE_PREFIX = "TebexGeneratedCartCard_";
    private static final String RUNTIME_CART_CARD_WIDE_TEMPLATE_PREFIX = "TebexGeneratedCartCardWide_";
    private static final String RUNTIME_SIDEBAR_CART_ROW_TEMPLATE_PREFIX = "TebexGeneratedSidebarCartRow_";
    private static final String RUNTIME_CHECKOUT_SUMMARY_ROW_TEMPLATE_PREFIX = "TebexGeneratedCheckoutSummaryRow_";
    private static final String RUNTIME_CHECKOUT_TEMPLATE_PREFIX = "TebexGeneratedCheckout_";
    private static final String RUNTIME_CHECKOUT_QR_PREFIX = "checkout-qr-";
    private static final List<String> LEGACY_RUNTIME_CACHE_DIRECTORIES = List.of(
            "runtime-assets",
            THUMBNAIL_CACHE_DIRECTORY + "/TebexStoreThumbnails",
            THUMBNAIL_CACHE_DIRECTORY + "/Common/TebexStoreThumbnails",
            THUMBNAIL_CACHE_DIRECTORY + "/Common/UI/Custom/Pages/Assets/TebexStoreThumbnails",
            THUMBNAIL_CACHE_DIRECTORY + "/UI"
    );
    private static final List<String> LEGACY_EXTERNAL_ASSET_PACK_DIRECTORIES = List.of(
            "Common/UI/Custom/Pages/Assets/TebexStoreThumbnails",
            "UI/Custom/Pages/Assets/TebexStoreThumbnails",
            "Common/TebexStoreThumbnails",
            "TebexStoreThumbnails"
    );
    private static final String RUNTIME_THUMBNAIL_PLACEHOLDER = "_placeholder.png";
    private static final int RUNTIME_THUMBNAIL_SIZE = 96;
    private static final int RUNTIME_THUMBNAIL_SIZE_2X = 192;
    private static final int RUNTIME_CHECKOUT_QR_TARGET_SIZE = 256;
    private static final int RUNTIME_CHECKOUT_QR_TARGET_SIZE_2X = 512;
    private static final int RUNTIME_CHECKOUT_QR_QUIET_ZONE_MODULES = 4;

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
    @Getter private CopyOnWriteArrayList<StoreSaleInfo> storeSalesCache = new CopyOnWriteArrayList<>();
    @Getter private final ConcurrentHashMap<Integer, String> categoryThumbnailTextureCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> completedCommands = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> playerIpv4Cache = new ConcurrentHashMap<>();
    private boolean warnedMissingHeadlessToken = false;
    private boolean warnedHeadlessAccountMismatch = false;
    private boolean loggedInformationPayload = false;
    private boolean loggedThumbnailAssetPackLocation = false;
    private String configuredHeadlessPrivateKey = "";
    private final ConcurrentHashMap<Integer, String> packageThumbnailSources = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> categoryThumbnailSources = new ConcurrentHashMap<>();
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

    @Override
    public CompletableFuture<Void> preLoad() {
        CompletableFuture<Void> parent = super.preLoad();
        if (parent == null) {
            precreateRuntimeThumbnailAssetPackStubIfNeeded();
            return CompletableFuture.completedFuture(null);
        }
        return parent.thenRun(this::precreateRuntimeThumbnailAssetPackStubIfNeeded);
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
            PlayerRef playerRef = connection.getPlayerRef();
            String ipAddress = fallbackAnalyticsIpv4(resolvePlayerIpv4(playerRef));
            playerIpv4Cache.put(playerRef.getUuid().toString(), ipAddress);
            this.serverEvents.add(new ServerEvent(
                    playerRef.getUuid().toString(),
                    playerRef.getUsername(),
                    ipAddress, ServerEvent.EnumServerEventType.JOIN));
        });
        this.getEventRegistry().register(PlayerDisconnectEvent.class, connection -> {
            PlayerRef playerRef = connection.getPlayerRef();
            String cachedIp = playerIpv4Cache.remove(playerRef.getUuid().toString());
            String ipAddress = fallbackAnalyticsIpv4(cachedIp != null ? cachedIp : resolvePlayerIpv4(playerRef));
            this.serverEvents.add(new ServerEvent(
                    playerRef.getUuid().toString(),
                    playerRef.getUsername(),
                    ipAddress, ServerEvent.EnumServerEventType.LEAVE));
        });
    }

    @Nullable
    public String resolvePlayerIpv4(@Nullable PlayerRef playerRef) {
        if (playerRef == null) {
            return null;
        }

        try {
            var packetHandler = playerRef.getPacketHandler();
            if (packetHandler == null) {
                return null;
            }

            var channel = packetHandler.getChannel();
            if (channel == null) {
                return null;
            }

            String direct = extractIpv4(channel.remoteAddress());
            if (direct != null) {
                return direct;
            }

            var parent = channel.parent();
            if (parent != null) {
                return extractIpv4(parent.remoteAddress());
            }
        } catch (Exception e) {
            debug("Failed to resolve player IPv4 for " + playerRef.getUsername() + ": " + e.getMessage());
        }

        return null;
    }

    @Nullable
    private static String extractIpv4(@Nullable SocketAddress address) {
        if (!(address instanceof InetSocketAddress inetSocketAddress)) {
            return null;
        }

        InetAddress inetAddress = inetSocketAddress.getAddress();
        if (inetAddress instanceof Inet4Address ipv4Address) {
            return ipv4Address.getHostAddress();
        }

        if (inetAddress == null) {
            String host = inetSocketAddress.getHostString();
            if (isIpv4Literal(host)) {
                return host;
            }
            return null;
        }

        String hostAddress = inetAddress.getHostAddress();
        return isIpv4Literal(hostAddress) ? hostAddress : null;
    }

    private static boolean isIpv4Literal(@Nullable String value) {
        if (value == null || value.isBlank() || value.indexOf(':') >= 0) {
            return false;
        }
        String[] parts = value.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isBlank() || part.length() > 3) {
                return false;
            }
            try {
                int octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    @Nonnull
    private static String fallbackAnalyticsIpv4(@Nullable String ipAddress) {
        return ipAddress == null || ipAddress.isBlank() ? "127.0.0.1" : ipAddress;
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
            storeSalesCache = loadStoreSalesFromPluginApi();
            headlessWebstore = null;
            debug("Store data source=Plugin API, Packages=" + packagesCache.size() + ", Categories=" + categoriesCache.size() + ", Community Goals=" + communityGoalsCache.size() + ", Sales=" + storeSalesCache.size());
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
        List<Category> pluginListingCategories = loadPluginListingCategories();
        Map<Integer, CategoryPackage> pluginListingMetadata = buildPluginListingPackageMetadata(pluginListingCategories);
        Map<Integer, Category> pluginCategoryMetadata = buildPluginListingCategoryMetadata(pluginListingCategories);
        Map<Integer, String> thumbnailTexturePaths = cacheHeadlessPackageThumbnails(headlessPackages, allowThumbnailDownload);
        Map<Integer, String> categoryThumbnailTexturePaths = cacheHeadlessCategoryThumbnails(headlessCategories, allowThumbnailDownload);
        CopyOnWriteArrayList<StoreSaleInfo> newSales = loadStoreSalesFromPluginApi();

        ConcurrentHashMap<Integer, Package> newPackages = new ConcurrentHashMap<>();
        for (HeadlessPackage headlessPackage : headlessPackages) {
            String thumbnailTexturePath = thumbnailTexturePaths.get(headlessPackage.getId());
            Package pluginPackage = toPluginPackage(
                    headlessPackage,
                    thumbnailTexturePath,
                    pluginListingMetadata.get(headlessPackage.getId())
            );
            newPackages.put(pluginPackage.getId(), pluginPackage);
        }

        headlessCategories.sort(Comparator.comparingInt(HeadlessCategory::getOrder).thenComparingInt(HeadlessCategory::getId));
        ConcurrentHashMap<Integer, Category> newCategories = new ConcurrentHashMap<>();
        for (HeadlessCategory headlessCategory : headlessCategories) {
            Category pluginCategory = toPluginCategory(headlessCategory, thumbnailTexturePaths, pluginListingMetadata, pluginCategoryMetadata);
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
        categoryThumbnailTextureCache.clear();
        categoryThumbnailTextureCache.putAll(categoryThumbnailTexturePaths);
        communityGoalsCache = newGoals;
        storeSalesCache = newSales;
        headlessWebstore = webstore;
        debug("Store data source=Headless API, Packages=" + packagesCache.size() + ", Categories=" + categoriesCache.size() + ", Community Goals=" + communityGoalsCache.size() + ", Sales=" + storeSalesCache.size());
    }

    private synchronized void ensureRuntimeThumbnailWorkspace() throws IOException {
        cleanupLegacyRuntimeThumbnailDirectory();
        cleanupLegacyExternalAssetPackDirectories();
        boolean assetPackCreated = Files.notExists(runtimeAssetPackRoot());
        Files.createDirectories(runtimeAssetPackRoot());
        ensureRuntimeThumbnailAssetPackManifest();
        Files.createDirectories(runtimeThumbnailDirectory());
        Files.createDirectories(runtimePageDirectory());
        ensurePlaceholderThumbnailExists();
        if (!loggedThumbnailAssetPackLocation) {
            loggedThumbnailAssetPackLocation = true;
            info("Publishing Tebex thumbnail assets to external asset pack at " + runtimeAssetPackRoot().toAbsolutePath());
        }
        if (assetPackCreated) {
            warnNoLog(
                    "Created Tebex thumbnail asset pack at " + runtimeAssetPackRoot().toAbsolutePath(),
                    "Restart the server once so Hytale registers the new asset pack from the mods folder. After that, thumbnail file updates should use the same asset-pack path."
            );
        }
    }

    private void precreateRuntimeThumbnailAssetPackStubIfNeeded() {
        if (hasAnyConfiguredSecretKey()) {
            return;
        }

        try {
            boolean assetPackCreated = Files.notExists(runtimeAssetPackRoot());
            Files.createDirectories(runtimeAssetPackRoot());
            ensureRuntimeThumbnailAssetPackManifest();
            Files.createDirectories(runtimePageDirectory());
            Files.createDirectories(runtimeThumbnailDirectory());
            if (assetPackCreated) {
                info("Pre-created Tebex thumbnail asset pack stub at " + runtimeAssetPackRoot().toAbsolutePath() + " before store registration.");
            }
        } catch (Exception e) {
            error("Failed to pre-create Tebex thumbnail asset pack stub during setup", e);
        }
    }

    private boolean hasAnyConfiguredSecretKey() {
        String envSecretKey = System.getenv("TEBEX_SECRET_KEY");
        if (envSecretKey != null && !envSecretKey.isBlank()) {
            return true;
        }

        TebexConfig cfg = this.config == null ? null : this.config.get();
        String configSecretKey = cfg == null ? null : cfg.getSecretKey();
        return configSecretKey != null && !configSecretKey.isBlank();
    }

    @Nonnull
    private Path runtimeAssetPackRoot() {
        return resolveModsDirectory().resolve(THUMBNAIL_ASSET_PACK_DIRECTORY_NAME);
    }

    @Nonnull
    private Path resolveModsDirectory() {
        Path dataDirectory = getDataDirectory();
        Path parent = dataDirectory.getParent();
        if (parent != null) {
            return parent;
        }

        Path jarPath = resolveOwnJarPath();
        if (jarPath != null && jarPath.getParent() != null) {
            return jarPath.getParent();
        }

        return dataDirectory;
    }

    private void ensureRuntimeThumbnailAssetPackManifest() throws IOException {
        Path manifestPath = runtimeAssetPackRoot().resolve(THUMBNAIL_ASSET_PACK_MANIFEST);
        String manifestContents = buildRuntimeThumbnailAssetPackManifest();
        String existing = Files.isRegularFile(manifestPath)
                ? Files.readString(manifestPath, StandardCharsets.UTF_8)
                : null;
        if (manifestContents.equals(existing)) {
            return;
        }

        Files.writeString(
                manifestPath,
                manifestContents,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    @Nonnull
    private static String buildRuntimeThumbnailAssetPackManifest() {
        return "{\n"
                + "  \"Group\": \"" + THUMBNAIL_ASSET_PACK_GROUP + "\",\n"
                + "  \"Name\": \"" + THUMBNAIL_ASSET_PACK_NAME + "\",\n"
                + "  \"Version\": \"" + VERSION + "\",\n"
                + "  \"Website\": \"https://tebex.io/\",\n"
                + "  \"Description\": \"Runtime-generated Tebex store thumbnails and UI card templates.\",\n"
                + "  \"IncludesAssetPack\": false,\n"
                + "  \"ServerVersion\": \"*\"\n"
                + "}\n";
    }

    @Nonnull
    private Path runtimeThumbnailDirectory() {
        return runtimeAssetRelativePath(RUNTIME_THUMBNAIL_DIRECTORY);
    }

    @Nonnull
    private Path runtimePageDirectory() {
        return runtimeAssetRelativePath(RUNTIME_PAGE_DIRECTORY);
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
    private Path runtimeThumbnailCardTemplatePath(@Nonnull String imageFileName, boolean wide) {
        return runtimePageDirectory().resolve(runtimeThumbnailCardTemplateFileName(imageFileName, wide));
    }

    @Nonnull
    private Path runtimeThumbnailCartCardTemplatePath(@Nonnull String imageFileName, boolean wide) {
        return runtimePageDirectory().resolve(runtimeThumbnailCartCardTemplateFileName(imageFileName, wide));
    }

    @Nonnull
    private Path runtimeThumbnailPackageCardTemplatePath(@Nonnull String imageFileName, boolean wide) {
        return runtimePageDirectory().resolve(runtimeThumbnailPackageCardTemplateFileName(imageFileName, wide));
    }

    @Nonnull
    private Path runtimeThumbnailSidebarCartRowTemplatePath(@Nonnull String imageFileName) {
        return runtimePageDirectory().resolve(runtimeThumbnailSidebarCartRowTemplateFileName(imageFileName));
    }

    @Nonnull
    private Path runtimeCheckoutSummaryRowTemplatePath(@Nonnull String imageFileName) {
        return runtimePageDirectory().resolve(runtimeCheckoutSummaryRowTemplateFileName(imageFileName));
    }

    @Nonnull
    private Path runtimeCheckoutTemplatePath(@Nonnull String assetKey) {
        return runtimePageDirectory().resolve(runtimeCheckoutTemplateFileName(assetKey));
    }

    @Nonnull
    private static String runtimeThumbnailTexturePath(@Nonnull String fileName) {
        return RUNTIME_THUMBNAIL_TEXTURE_PREFIX + "/" + fileName;
    }

    @Nonnull
    public static String runtimeThumbnailCardTemplateUiPath(@Nonnull String texturePath, boolean wide) {
        String fileName = texturePath.replace('\\', '/');
        int lastSlash = fileName.lastIndexOf('/');
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }
        return "Pages/" + runtimeThumbnailCardTemplateFileName(fileName, wide);
    }

    @Nonnull
    public static String runtimeThumbnailCartCardTemplateUiPath(@Nonnull String texturePath, boolean wide) {
        String fileName = texturePath.replace('\\', '/');
        int lastSlash = fileName.lastIndexOf('/');
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }
        return "Pages/" + runtimeThumbnailCartCardTemplateFileName(fileName, wide);
    }

    @Nonnull
    public static String runtimeThumbnailPackageCardTemplateUiPath(@Nonnull String texturePath, boolean wide) {
        String fileName = texturePath.replace('\\', '/');
        int lastSlash = fileName.lastIndexOf('/');
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }
        return "Pages/" + runtimeThumbnailPackageCardTemplateFileName(fileName, wide);
    }

    @Nonnull
    public static String runtimeThumbnailSidebarCartRowTemplateUiPath(@Nonnull String texturePath) {
        String fileName = texturePath.replace('\\', '/');
        int lastSlash = fileName.lastIndexOf('/');
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }
        return "Pages/" + runtimeThumbnailSidebarCartRowTemplateFileName(fileName);
    }

    @Nonnull
    public static String runtimeCheckoutSummaryRowTemplateUiPath(@Nonnull String texturePath) {
        String fileName = texturePath.replace('\\', '/');
        int lastSlash = fileName.lastIndexOf('/');
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }
        return "Pages/" + runtimeCheckoutSummaryRowTemplateFileName(fileName);
    }

    @Nonnull
    public static String runtimeCheckoutTemplateUiPath(@Nonnull String assetKey) {
        return "Pages/" + runtimeCheckoutTemplateFileName(assetKey);
    }

    @Nonnull
    private static String runtimeThumbnail2xFileName(@Nonnull String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".png")) {
            return fileName + "@2x";
        }
        return fileName.substring(0, fileName.length() - 4) + "@2x.png";
    }

    @Nonnull
    private static String runtimeThumbnailCardTemplateFileName(@Nonnull String imageFileName, boolean wide) {
        return runtimeThumbnailTemplateFileName(imageFileName, wide ? RUNTIME_CARD_WIDE_TEMPLATE_PREFIX : RUNTIME_CARD_TEMPLATE_PREFIX);
    }

    @Nonnull
    private static String runtimeThumbnailCartCardTemplateFileName(@Nonnull String imageFileName, boolean wide) {
        return runtimeThumbnailTemplateFileName(imageFileName, wide ? RUNTIME_CART_CARD_WIDE_TEMPLATE_PREFIX : RUNTIME_CART_CARD_TEMPLATE_PREFIX);
    }

    @Nonnull
    private static String runtimeThumbnailPackageCardTemplateFileName(@Nonnull String imageFileName, boolean wide) {
        return runtimeThumbnailTemplateFileName(imageFileName, wide ? RUNTIME_PACKAGE_CARD_WIDE_TEMPLATE_PREFIX : RUNTIME_PACKAGE_CARD_TEMPLATE_PREFIX);
    }

    @Nonnull
    private static String runtimeThumbnailSidebarCartRowTemplateFileName(@Nonnull String imageFileName) {
        return runtimeThumbnailTemplateFileName(imageFileName, RUNTIME_SIDEBAR_CART_ROW_TEMPLATE_PREFIX);
    }

    @Nonnull
    private static String runtimeCheckoutSummaryRowTemplateFileName(@Nonnull String imageFileName) {
        return runtimeThumbnailTemplateFileName(imageFileName, RUNTIME_CHECKOUT_SUMMARY_ROW_TEMPLATE_PREFIX);
    }

    @Nonnull
    private static String runtimeThumbnailTemplateFileName(@Nonnull String imageFileName, @Nonnull String prefix) {
        String base = imageFileName.replace('\\', '/');
        int lastSlash = base.lastIndexOf('/');
        if (lastSlash >= 0) {
            base = base.substring(lastSlash + 1);
        }
        if (base.toLowerCase(Locale.ROOT).endsWith(".png")) {
            base = base.substring(0, base.length() - 4);
        }
        String safeBase = base.replaceAll("[^A-Za-z0-9_-]", "_");
        return prefix + safeBase + ".ui";
    }

    @Nonnull
    private static String runtimeCheckoutQrFileName(@Nonnull String assetKey) {
        return RUNTIME_CHECKOUT_QR_PREFIX + sanitizeRuntimeAssetKey(assetKey) + ".png";
    }

    @Nonnull
    private static String runtimeCheckoutTemplateFileName(@Nonnull String assetKey) {
        return RUNTIME_CHECKOUT_TEMPLATE_PREFIX + sanitizeRuntimeAssetKey(assetKey) + ".ui";
    }

    @Nonnull
    private static String sanitizeRuntimeAssetKey(@Nonnull String assetKey) {
        String normalized = assetKey.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0) {
            normalized = normalized.substring(lastSlash + 1);
        }
        String safe = normalized.replaceAll("[^A-Za-z0-9_-]", "_");
        return safe.isBlank() ? "runtime" : safe;
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

    private void cleanupLegacyExternalAssetPackDirectories() {
        for (String legacyDirectoryPath : LEGACY_EXTERNAL_ASSET_PACK_DIRECTORIES) {
            Path legacyDirectory = runtimeAssetRelativePath(legacyDirectoryPath);
            if (!Files.exists(legacyDirectory)) {
                continue;
            }

            try (Stream<Path> stream = Files.walk(legacyDirectory)) {
                List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
                for (Path path : paths) {
                    Files.deleteIfExists(path);
                }
                info("Removed legacy Tebex thumbnail asset-pack directory at " + legacyDirectory.toAbsolutePath());
            } catch (IOException e) {
                warnNoLog(
                        "Failed to remove legacy Tebex thumbnail asset-pack directory.",
                        "Legacy asset-pack paths may continue to override current thumbnails. Path: " + legacyDirectory.toAbsolutePath()
                );
                error("Failed to cleanup legacy thumbnail asset-pack directory " + legacyDirectory.toAbsolutePath(), e);
            }
        }
    }

    private void ensurePlaceholderThumbnailExists() throws IOException {
        Path placeholderPath = runtimeThumbnailPath(RUNTIME_THUMBNAIL_PLACEHOLDER);
        String placeholder2xFileName = runtimeThumbnail2xFileName(RUNTIME_THUMBNAIL_PLACEHOLDER);
        Path placeholder2xPath = runtimeThumbnailPath(placeholder2xFileName);
        if (Files.isRegularFile(placeholderPath) && Files.isRegularFile(placeholder2xPath)) {
            ensureThumbnailCardTemplates(RUNTIME_THUMBNAIL_PLACEHOLDER);
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
        ensureThumbnailCardTemplates(RUNTIME_THUMBNAIL_PLACEHOLDER);
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
                    ensureThumbnailCardTemplates(fileName);
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
            ensureThumbnailCardTemplates(fileName);
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

        ensureThumbnailCardTemplates(fileName);
        packageThumbnailSources.put(packageId, trimmedSource);
    }

    @Nonnull
    private Map<Integer, String> cacheHeadlessCategoryThumbnails(
            @Nonnull List<HeadlessCategory> headlessCategories,
            boolean allowDownload
    ) {
        ConcurrentHashMap<Integer, String> texturePaths = new ConcurrentHashMap<>();
        Set<Integer> currentCategoryIds = new HashSet<>();

        try {
            ensureRuntimeThumbnailWorkspace();
        } catch (Exception e) {
            error("Failed to initialize runtime thumbnail workspace", e);
            return texturePaths;
        }

        boolean skippedRuntimeDownloadLogged = false;
        for (HeadlessCategory headlessCategory : headlessCategories) {
            int categoryId = headlessCategory.getId();
            currentCategoryIds.add(categoryId);

            String sourceImageUrl = sanitizeImageValue(headlessCategory.getImageUrl());
            if (sourceImageUrl == null || sourceImageUrl.isBlank()) {
                categoryThumbnailSources.remove(categoryId);
                continue;
            }

            String fileName = categoryThumbnailFileName(categoryId);
            String fileName2x = runtimeThumbnail2xFileName(fileName);
            Path thumbnailPath = runtimeThumbnailPath(fileName);
            Path thumbnailPath2x = runtimeThumbnailPath(fileName2x);
            if (allowDownload) {
                try {
                    cacheCategoryThumbnail(categoryId, sourceImageUrl);
                    texturePaths.put(categoryId, runtimeThumbnailTexturePath(fileName));
                    continue;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    debug("Thumbnail download interrupted for category " + categoryId + ": " + e.getMessage());
                    break;
                } catch (Exception e) {
                    debug("Failed to cache thumbnail for category " + categoryId + ": " + e.getMessage());
                }
            } else if (!skippedRuntimeDownloadLogged) {
                skippedRuntimeDownloadLogged = true;
                debug("Skipping runtime thumbnail downloads during scheduled refresh; restart server to recache category images.");
            }

            if (Files.isRegularFile(thumbnailPath)) {
                try {
                    ensureThumbnail2xVariant(thumbnailPath, thumbnailPath2x);
                    ensureThumbnailCardTemplates(fileName);
                    texturePaths.put(categoryId, runtimeThumbnailTexturePath(fileName));
                } catch (Exception e) {
                    debug("Failed to publish cached thumbnail alias for category " + categoryId + ": " + e.getMessage());
                }
            }
        }

        categoryThumbnailSources.keySet().removeIf(categoryId -> !currentCategoryIds.contains(categoryId));
        return texturePaths;
    }

    private void cacheCategoryThumbnail(int categoryId, @Nonnull String sourceImageUrl) throws IOException, InterruptedException {
        String fileName = categoryThumbnailFileName(categoryId);
        String fileName2x = runtimeThumbnail2xFileName(fileName);
        Path outputPath = runtimeThumbnailPath(fileName);
        Path outputPath2x = runtimeThumbnailPath(fileName2x);
        String trimmedSource = sourceImageUrl.trim();
        String cachedSource = categoryThumbnailSources.get(categoryId);
        if (trimmedSource.equals(cachedSource) && Files.isRegularFile(outputPath) && Files.isRegularFile(outputPath2x)) {
            ensureThumbnailCardTemplates(fileName);
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
            throw new IOException("Thumbnail request failed with status " + response.statusCode() + " for category " + categoryId);
        }

        BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(response.body()));
        if (sourceImage == null) {
            throw new IOException("Unsupported image format for category " + categoryId);
        }

        BufferedImage thumbnail = normalizeThumbnailImage(sourceImage, RUNTIME_THUMBNAIL_SIZE, RUNTIME_THUMBNAIL_SIZE);
        BufferedImage thumbnail2x = normalizeThumbnailImage(sourceImage, RUNTIME_THUMBNAIL_SIZE_2X, RUNTIME_THUMBNAIL_SIZE_2X);
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (!ImageIO.write(thumbnail, "png", outputPath.toFile())) {
            throw new IOException("No PNG writer available while caching category thumbnail " + categoryId);
        }
        if (!ImageIO.write(thumbnail2x, "png", outputPath2x.toFile())) {
            throw new IOException("No PNG writer available while caching category thumbnail @2x " + categoryId);
        }

        ensureThumbnailCardTemplates(fileName);
        categoryThumbnailSources.put(categoryId, trimmedSource);
    }

    @Nonnull
    private static String categoryThumbnailFileName(int categoryId) {
        return "category-" + categoryId + ".png";
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

    private void ensureThumbnailCardTemplates(@Nonnull String imageFileName) throws IOException {
        Files.createDirectories(runtimePageDirectory());
        Files.writeString(
                runtimeThumbnailCardTemplatePath(imageFileName, false),
                buildRuntimeThumbnailCardTemplate(imageFileName, false)
        );
        Files.writeString(
                runtimeThumbnailCardTemplatePath(imageFileName, true),
                buildRuntimeThumbnailCardTemplate(imageFileName, true)
        );
        Files.writeString(
                runtimeThumbnailPackageCardTemplatePath(imageFileName, false),
                buildRuntimePackageCardTemplate(imageFileName, false)
        );
        Files.writeString(
                runtimeThumbnailPackageCardTemplatePath(imageFileName, true),
                buildRuntimePackageCardTemplate(imageFileName, true)
        );
        Files.writeString(
                runtimeThumbnailCartCardTemplatePath(imageFileName, false),
                buildRuntimeCartThumbnailCardTemplate(imageFileName, false)
        );
        Files.writeString(
                runtimeThumbnailCartCardTemplatePath(imageFileName, true),
                buildRuntimeCartThumbnailCardTemplate(imageFileName, true)
        );
        Files.writeString(
                runtimeThumbnailSidebarCartRowTemplatePath(imageFileName),
                buildRuntimeSidebarCartRowTemplate(imageFileName)
        );
        Files.writeString(
                runtimeCheckoutSummaryRowTemplatePath(imageFileName),
                buildRuntimeCheckoutSummaryRowTemplate(imageFileName)
        );
    }

    @Nonnull
    public synchronized CheckoutPreviewAsset generateCheckoutPreviewAsset(
            @Nonnull String assetKey,
            @Nonnull String checkoutUrl
    ) throws IOException {
        ensureRuntimeThumbnailWorkspace();

        String safeKey = sanitizeRuntimeAssetKey(assetKey);
        String fileName = runtimeCheckoutQrFileName(safeKey);
        String fileName2x = runtimeThumbnail2xFileName(fileName);
        Path outputPath = runtimeThumbnailPath(fileName);
        Path outputPath2x = runtimeThumbnailPath(fileName2x);
        Path templatePath = runtimeCheckoutTemplatePath(safeKey);
        QrCode qrCode = QrCode.encodeText(checkoutUrl, QrCode.ErrorCorrectionLevel.MEDIUM);

        writeCheckoutQrPng(qrCode, outputPath, RUNTIME_CHECKOUT_QR_TARGET_SIZE);
        writeCheckoutQrPng(qrCode, outputPath2x, RUNTIME_CHECKOUT_QR_TARGET_SIZE_2X);
        Files.writeString(
                templatePath,
                buildRuntimeCheckoutPreviewTemplate(fileName),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
        return new CheckoutPreviewAsset(runtimeThumbnailTexturePath(fileName), runtimeCheckoutTemplateUiPath(safeKey));
    }

    private static void writeCheckoutQrPng(
            @Nonnull QrCode qrCode,
            @Nonnull Path outputPath,
            int targetTextureSize
    ) throws IOException {
        int qrWithQuietZone = qrCode.getSize() + (RUNTIME_CHECKOUT_QR_QUIET_ZONE_MODULES * 2);
        int modulePixels = Math.max(1, targetTextureSize / Math.max(1, qrWithQuietZone));
        BufferedImage image = QrCodePngRenderer.render(qrCode, modulePixels, RUNTIME_CHECKOUT_QR_QUIET_ZONE_MODULES);
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!ImageIO.write(image, "png", outputPath.toFile())) {
            throw new IOException("No PNG writer available while writing checkout QR preview.");
        }
    }

    @Nonnull
    private static String buildRuntimeThumbnailCardTemplate(@Nonnull String imageFileName, boolean wide) {
        String texturePath = runtimeThumbnailTexturePath(imageFileName).replace("\\", "/");
        String anchor = wide
                ? "(Width: 996, Height: 176, Bottom: 16)"
                : "(Width: 490, Height: 176, Bottom: 16, Right: 14)";

        return "$C = \"../Common.ui\";\n"
                + "\n"
                + "Button #Card {\n"
                + "  Anchor: " + anchor + ";\n"
                + "  Padding: (Full: 14);\n"
                + "  LayoutMode: Left;\n"
                + "  Background: #10253a(0.96);\n"
                + "  Style: (\n"
                + "    Hovered: (Background: #173753(0.98)),\n"
                + "    Pressed: (Background: #1f4567(0.98))\n"
                + "  );\n"
                + "\n"
                + "  Group #PackageThumbnailFrame {\n"
                + "    Anchor: (Width: 92, Height: 92, Right: 14);\n"
                + "    Background: #08111b(0.88);\n"
                + "    Padding: (Full: 3);\n"
                + "\n"
                + "    Group #PackageThumbnail {\n"
                + "      Anchor: (Full: 0);\n"
                + "      Background: (TexturePath: \"" + texturePath + "\");\n"
                + "    }\n"
                + "  }\n"
                + "\n"
                + "  Group {\n"
                + "    LayoutMode: Top;\n"
                + "    FlexWeight: 1;\n"
                + "\n"
                + "    Label #SubcommandName {\n"
                + "      Style: (\n"
                + "        FontSize: 22,\n"
                + "        RenderBold: true,\n"
                + "        TextColor: $C.@ColorDefault\n"
                + "      );\n"
                + "      Anchor: (Bottom: 8);\n"
                + "    }\n"
                + "\n"
                + "    Label #SubcommandUsage {\n"
                + "      Style: (\n"
                + "        FontSize: 15,\n"
                + "        TextColor: $C.@ColorDefaultLabel,\n"
                + "        Wrap: true\n"
                + "      );\n"
                + "      Anchor: (Bottom: 8);\n"
                + "    }\n"
                + "\n"
                + "    Label #SubcommandDescription {\n"
                + "      Style: (\n"
                + "        FontSize: 15,\n"
                + "        TextColor: $C.@ColorBlueAccent,\n"
                + "        RenderBold: true\n"
                + "      );\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
    }

    @Nonnull
    private static String buildRuntimePackageCardTemplate(@Nonnull String imageFileName, boolean wide) {
        String texturePath = runtimeThumbnailTexturePath(imageFileName).replace("\\", "/");
        String anchor = wide
                ? "(Width: 996, Height: 224, Bottom: 16)"
                : "(Width: 490, Height: 224, Bottom: 16, Right: 14)";

        return "$C = \"../Common.ui\";\n"
                + "\n"
                + "Group #Card {\n"
                + "  Anchor: " + anchor + ";\n"
                + "  Padding: (Full: 14);\n"
                + "  LayoutMode: Top;\n"
                + "  Background: #10253a(0.96);\n"
                + "\n"
                + "  Group {\n"
                + "    Anchor: (Height: 140, Bottom: 12);\n"
                + "    LayoutMode: Left;\n"
                + "\n"
                + "    Group #PackageThumbnailFrame {\n"
                + "      Anchor: (Width: 92, Height: 92, Right: 14);\n"
                + "      Background: #08111b(0.88);\n"
                + "      Padding: (Full: 3);\n"
                + "\n"
                + "      Group #PackageThumbnail {\n"
                + "        Anchor: (Full: 0);\n"
                + "        Background: (TexturePath: \"" + texturePath + "\");\n"
                + "      }\n"
                + "    }\n"
                + "\n"
                + "    Group {\n"
                + "      LayoutMode: Top;\n"
                + "      FlexWeight: 1;\n"
                + "\n"
                + "      Label #SubcommandName {\n"
                + "        Style: (\n"
                + "          FontSize: 22,\n"
                + "          RenderBold: true,\n"
                + "          TextColor: $C.@ColorDefault\n"
                + "        );\n"
                + "        Anchor: (Bottom: 8);\n"
                + "      }\n"
                + "\n"
                + "      Label #SubcommandUsage {\n"
                + "        Style: (\n"
                + "          FontSize: 18,\n"
                + "          TextColor: $C.@ColorGoldHighlight,\n"
                + "          RenderBold: true\n"
                + "        );\n"
                + "        Anchor: (Bottom: 8);\n"
                + "      }\n"
                + "\n"
                + "      Label #SubcommandDescription {\n"
                + "        Style: (\n"
                + "          FontSize: 14,\n"
                + "          TextColor: $C.@ColorDefaultLabel,\n"
                + "          Wrap: true\n"
                + "        );\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "\n"
                + "  $C.@SecondaryTextButton #AddButton {\n"
                + "    @Anchor = (Height: 40);\n"
                + "    @Text = \"Add To Cart\";\n"
                + "  }\n"
                + "}\n";
    }

    @Nonnull
    private static String buildRuntimeCartThumbnailCardTemplate(@Nonnull String imageFileName, boolean wide) {
        String texturePath = runtimeThumbnailTexturePath(imageFileName).replace("\\", "/");
        String anchor = wide
                ? "(Width: 996, Height: 228, Bottom: 16)"
                : "(Width: 490, Height: 228, Bottom: 16, Right: 14)";

        return "$C = \"../Common.ui\";\n"
                + "\n"
                + "Group #Card {\n"
                + "  Anchor: " + anchor + ";\n"
                + "  LayoutMode: Top;\n"
                + "\n"
                + "  Group {\n"
                + "    Anchor: (Height: 166, Bottom: 10);\n"
                + "    Padding: (Full: 14);\n"
                + "    LayoutMode: Left;\n"
                + "    Background: #10253a(0.96);\n"
                + "\n"
                + "    Group #PackageThumbnailFrame {\n"
                + "      Anchor: (Width: 92, Height: 92, Right: 14);\n"
                + "      Background: #08111b(0.88);\n"
                + "      Padding: (Full: 3);\n"
                + "\n"
                + "      Group #PackageThumbnail {\n"
                + "        Anchor: (Full: 0);\n"
                + "        Background: (TexturePath: \"" + texturePath + "\");\n"
                + "      }\n"
                + "    }\n"
                + "\n"
                + "    Group {\n"
                + "      LayoutMode: Top;\n"
                + "      FlexWeight: 1;\n"
                + "\n"
                + "      Label #SubcommandName {\n"
                + "        Style: (\n"
                + "          FontSize: 22,\n"
                + "          RenderBold: true,\n"
                + "          TextColor: $C.@ColorDefault\n"
                + "        );\n"
                + "        Anchor: (Bottom: 8);\n"
                + "      }\n"
                + "\n"
                + "      Label #SubcommandUsage {\n"
                + "        Style: (\n"
                + "          FontSize: 15,\n"
                + "          TextColor: $C.@ColorBlueAccent,\n"
                + "          RenderBold: true\n"
                + "        );\n"
                + "        Anchor: (Bottom: 8);\n"
                + "      }\n"
                + "\n"
                + "      Label #SubcommandDescription {\n"
                + "        Style: (\n"
                + "          FontSize: 14,\n"
                + "          TextColor: $C.@ColorDefaultLabel,\n"
                + "          Wrap: true\n"
                + "        );\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "\n"
                + "  Group {\n"
                + "    Anchor: (Height: 40);\n"
                + "    LayoutMode: Left;\n"
                + "\n"
                + "    $C.@SecondaryTextButton #DecrementButton {\n"
                + "      @Anchor = (Width: 68, Height: 40, Right: 8);\n"
                + "      @Text = \"-\";\n"
                + "    }\n"
                + "\n"
                + "    Group #QuantityBadge {\n"
                + "      Anchor: (Width: 84, Height: 40, Right: 8);\n"
                + "      LayoutMode: Center;\n"
                + "      Padding: (Top: 4);\n"
                + "      Background: #08111b(0.82);\n"
                + "\n"
                + "      Label #QuantityLabel {\n"
                + "        Anchor: (Full: 0);\n"
                + "        Style: (\n"
                + "          FontSize: 15,\n"
                + "          RenderBold: true,\n"
                + "          TextColor: $C.@ColorDefault,\n"
                + "          HorizontalAlignment: Center\n"
                + "        );\n"
                + "        Text: \"1\";\n"
                + "      }\n"
                + "    }\n"
                + "\n"
                + "    $C.@SecondaryTextButton #IncrementButton {\n"
                + "      @Anchor = (Width: 68, Height: 40, Right: 12);\n"
                + "      @Text = \"+\";\n"
                + "    }\n"
                + "\n"
                + "    $C.@TextButton #RemoveButton {\n"
                + "      @Anchor = (Height: 40);\n"
                + "      @Text = \"Remove\";\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
    }

    @Nonnull
    private static String buildRuntimeSidebarCartRowTemplate(@Nonnull String imageFileName) {
        String texturePath = runtimeThumbnailTexturePath(imageFileName).replace("\\", "/");
        return "$C = \"../Common.ui\";\n"
                + "\n"
                + "Group #Row {\n"
                + "  Anchor: (Height: 56, Bottom: 10);\n"
                + "  LayoutMode: Left;\n"
                + "  Padding: (Left: 10, Top: 11, Right: 10, Bottom: 11);\n"
                + "  Background: #08111b(0.82);\n"
                + "\n"
                + "  Group {\n"
                + "    Anchor: (Width: 32, Height: 34, Right: 10);\n"
                + "    LayoutMode: Center;\n"
                + "\n"
                + "    Group #PackageThumbnailFrame {\n"
                + "      Anchor: (Width: 32, Height: 32);\n"
                + "      Background: #173753(0.92);\n"
                + "      Padding: (Full: 2);\n"
                + "\n"
                + "      Group #PackageThumbnail {\n"
                + "        Anchor: (Full: 0);\n"
                + "        Background: (TexturePath: \"" + texturePath + "\");\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "\n"
                + "  Group {\n"
                + "    FlexWeight: 1;\n"
                + "    Anchor: (Height: 34, Right: 8);\n"
                + "    LayoutMode: Center;\n"
                + "    Padding: (Top: 10);\n"
                + "\n"
                + "    Label #PackageName {\n"
                + "      Style: (\n"
                + "        FontSize: 14,\n"
                + "        RenderBold: true,\n"
                + "        TextColor: $C.@ColorDefault\n"
                + "      );\n"
                + "      Text: \"Package\";\n"
                + "    }\n"
                + "  }\n"
                + "\n"
                + "  $C.@SecondaryTextButton #DecrementButton {\n"
                + "    @Anchor = (Width: 34, Height: 34, Right: 6);\n"
                + "    @Text = \"-\";\n"
                + "  }\n"
                + "\n"
                + "  Group #QuantityBadge {\n"
                + "    Anchor: (Width: 40, Height: 34, Right: 6);\n"
                + "    LayoutMode: Center;\n"
                + "    Padding: (Top: 10);\n"
                + "    Background: #111b28(0.95);\n"
                + "\n"
                + "    Label #QuantityLabel {\n"
                + "      Anchor: (Full: 0);\n"
                + "      Style: (\n"
                + "        FontSize: 14,\n"
                + "        RenderBold: true,\n"
                + "        TextColor: $C.@ColorDefault,\n"
                + "        HorizontalAlignment: Center\n"
                + "      );\n"
                + "      Text: \"1\";\n"
                + "    }\n"
                + "  }\n"
                + "\n"
                + "  $C.@SecondaryTextButton #IncrementButton {\n"
                + "    @Anchor = (Width: 34, Height: 34, Right: 8);\n"
                + "    @Text = \"+\";\n"
                + "  }\n"
                + "\n"
                + "  Group {\n"
                + "    Anchor: (Width: 92, Height: 34);\n"
                + "    LayoutMode: Center;\n"
                + "    Padding: (Top: 10);\n"
                + "\n"
                + "    Label #PriceLabel {\n"
                + "      Style: (\n"
                + "        FontSize: 15,\n"
                + "        RenderBold: true,\n"
                + "        TextColor: $C.@ColorGoldHighlight\n"
                + "      );\n"
                + "      Text: \"0.00 USD\";\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
    }

    @Nonnull
    private static String buildRuntimeCheckoutSummaryRowTemplate(@Nonnull String imageFileName) {
        String texturePath = runtimeThumbnailTexturePath(imageFileName).replace("\\", "/");
        return "$C = \"../Common.ui\";\n"
                + "\n"
                + "Group #Row {\n"
                + "  Anchor: (Height: 64, Bottom: 12);\n"
                + "  LayoutMode: Left;\n"
                + "  Padding: (Full: 14);\n"
                + "  Background: #08111b(0.82);\n"
                + "\n"
                + "  Group #PackageThumbnailFrame {\n"
                + "    Anchor: (Width: 28, Height: 28, Right: 12);\n"
                + "    Background: #173753(0.92);\n"
                + "    Padding: (Full: 2);\n"
                + "\n"
                + "    Group #PackageThumbnail {\n"
                + "      Anchor: (Full: 0);\n"
                + "      Background: (TexturePath: \"" + texturePath + "\");\n"
                + "    }\n"
                + "  }\n"
                + "\n"
                + "  Group {\n"
                + "    FlexWeight: 1;\n"
                + "    Anchor: (Height: 36, Right: 8);\n"
                + "    LayoutMode: Center;\n"
                + "    Padding: (Top: 8);\n"
                + "\n"
                + "    Label #PackageName {\n"
                + "      Style: (\n"
                + "        FontSize: 16,\n"
                + "        RenderBold: true,\n"
                + "        TextColor: $C.@ColorDefault\n"
                + "      );\n"
                + "      Text: \"Package\";\n"
                + "    }\n"
                + "  }\n"
                + "\n"
                + "  Group {\n"
                + "    Anchor: (Width: 110, Height: 36);\n"
                + "    LayoutMode: Center;\n"
                + "    Padding: (Top: 8);\n"
                + "\n"
                + "    Label #PriceLabel {\n"
                + "      Style: (\n"
                + "        FontSize: 16,\n"
                + "        RenderBold: true,\n"
                + "        TextColor: $C.@ColorGoldHighlight\n"
                + "      );\n"
                + "      Text: \"0.00 USD\";\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
    }

    @Nonnull
    private static String buildRuntimeCheckoutPreviewTemplate(@Nonnull String imageFileName) {
        String texturePath = runtimeThumbnailTexturePath(imageFileName).replace("\\", "/");
        return "$C = \"../Common.ui\";\n"
                + "\n"
                + "Group #Card {\n"
                + "  Anchor: (Width: 404, Height: 262, Bottom: 14);\n"
                + "  LayoutMode: Top;\n"
                + "  Background: #10253a(0.96);\n"
                + "\n"
                + "  Group {\n"
                + "    Anchor: (Height: 44, Bottom: 14);\n"
                + "    LayoutMode: Center;\n"
                + "    Background: #314867(0.88);\n"
                + "\n"
                + "    Label #SubcommandName {\n"
                + "      Style: (\n"
                + "        ...$C.@TitleStyle,\n"
                + "        FontSize: 18,\n"
                + "        TextColor: $C.@ColorDefault,\n"
                + "        HorizontalAlignment: Center,\n"
                + "        RenderBold: true\n"
                + "      );\n"
                + "    }\n"
                + "  }\n"
                + "\n"
                + "  Group {\n"
                + "    LayoutMode: Top;\n"
                + "    Padding: (Left: 20, Top: 8, Right: 20, Bottom: 20);\n"
                + "\n"
                + "    Label #SubcommandUsage {\n"
                + "      Style: (\n"
                + "        FontSize: 15,\n"
                + "        TextColor: $C.@ColorDefaultLabel,\n"
                + "        Wrap: true\n"
                + "      );\n"
                + "      Anchor: (Bottom: 12);\n"
                + "    }\n"
                + "\n"
                + "    Group {\n"
                + "      Anchor: (Width: 364, Height: 148, Bottom: 0);\n"
                + "      LayoutMode: Center;\n"
                + "\n"
                + "      Group #CheckoutQrFrame {\n"
                + "        Anchor: (Width: 148, Height: 148);\n"
                + "        Background: #f4f7fb;\n"
                + "        Padding: (Full: 8);\n"
                + "\n"
                + "        Group #CheckoutQrImage {\n"
                + "          Anchor: (Full: 0);\n"
                + "          Background: (TexturePath: \"" + texturePath + "\");\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "\n"
                + "    Label #SubcommandDescription {\n"
                + "      Style: (\n"
                + "        FontSize: 14,\n"
                + "        TextColor: $C.@ColorDefaultLabel,\n"
                + "        Wrap: true\n"
                + "      );\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
    }

    @Nonnull
    public synchronized AssetPackRebuildResult rebuildThumbnailAssetPack() {
        AssetPackRebuildResult refreshFailure = refreshStoreDataForThumbnailRebuild();
        if (refreshFailure != null) {
            return refreshFailure;
        }

        try {
            ensureRuntimeThumbnailWorkspace();
        } catch (Exception e) {
            error("Failed to initialize runtime thumbnail workspace before external asset-pack rebuild", e);
            return AssetPackRebuildResult.failure(
                    "Failed to prepare the Tebex thumbnail asset pack.",
                    describeFailure(e),
                    "Check that the server mods directory is writable, then run /tebex rebuild again."
            );
        }

        LinkedHashSet<Path> runtimeAssetFiles = new LinkedHashSet<>();
        try (Stream<Path> stream = Files.list(runtimeThumbnailDirectory())) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName() != null)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted()
                    .forEach(runtimeAssetFiles::add);
        } catch (IOException e) {
            error("Failed to list runtime thumbnails for external asset pack rebuild", e);
            return AssetPackRebuildResult.failure(
                    "Failed to read cached package thumbnails.",
                    describeFailure(e),
                    "Open the store at least once so the server can cache package images, then run /tebex rebuild again."
            );
        }

        try (Stream<Path> stream = Files.list(runtimePageDirectory())) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName() != null)
                    .filter(path -> isGeneratedRuntimeCardTemplateFileName(path.getFileName().toString()))
                    .sorted()
                    .forEach(runtimeAssetFiles::add);
        } catch (IOException e) {
            error("Failed to list runtime page templates for external asset pack rebuild", e);
            return AssetPackRebuildResult.failure(
                    "Failed to read generated thumbnail card templates.",
                    describeFailure(e),
                    "Check that the server mods directory is writable, then run /tebex rebuild again."
            );
        }

        if (runtimeAssetFiles.isEmpty()) {
            return AssetPackRebuildResult.failure(
                    "No cached store thumbnail assets were found.",
                    "The external Tebex thumbnail asset pack does not contain any generated PNG or thumbnail card template files yet.",
                    "Open /buy first so package images are cached, then run /tebex rebuild again."
            );
        }

        info("Thumbnail asset pack rebuild succeeded. Published " + runtimeAssetFiles.size() + " runtime asset(s) to " + runtimeAssetPackRoot().toAbsolutePath());
        return AssetPackRebuildResult.success(
                "Tebex thumbnail asset pack is ready.",
                "Refreshed store data and published " + runtimeAssetFiles.size() + " runtime asset(s) to " + runtimeAssetPackRoot().toAbsolutePath() + ".",
                "If this asset pack was created for the first time on this server, restart once so Hytale registers it from the mods folder."
        );
    }

    @Nullable
    private AssetPackRebuildResult refreshStoreDataForThumbnailRebuild() {
        refreshServerInfo(true);

        if (tebexServerInfo == null) {
            return AssetPackRebuildResult.failure(
                    "Failed to refresh Plugin API /information before rebuilding thumbnails.",
                    "The plugin could not refresh its Tebex server information, so the rebuild cannot determine which store assets to download.",
                    "Check your Tebex secret key and the server logs, then run /tebex rebuild again."
            );
        }

        if (!headlessApi.hasPublicToken()) {
            return AssetPackRebuildResult.failure(
                    "Cannot rebuild thumbnails because the Headless public token is missing.",
                    "Plugin API /information did not provide account.public_token, so Tebex cannot re-download uploaded package images.",
                    "Fix the Tebex store linkage so /information returns account.public_token, then run /tebex rebuild again."
            );
        }

        if (headlessWebstore == null) {
            return AssetPackRebuildResult.failure(
                    "Cannot rebuild thumbnails because the Headless store refresh failed.",
                    "The plugin fell back to Plugin API store data, which does not include uploaded package images for thumbnail re-downloads.",
                    "Check the Headless API warnings in the server log, then run /tebex rebuild again."
            );
        }

        return null;
    }

    @Nonnull
    private static String describeFailure(@Nullable Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }

        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
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

    private static boolean isGeneratedRuntimeThumbnailFileName(@Nonnull String fileName) {
        String normalized = fileName.trim();
        return normalized.equals(RUNTIME_THUMBNAIL_PLACEHOLDER)
                || normalized.equals(runtimeThumbnail2xFileName(RUNTIME_THUMBNAIL_PLACEHOLDER))
                || normalized.matches("\\d+(?:@2x)?\\.png");
    }

    private static boolean isGeneratedRuntimeCardTemplateFileName(@Nonnull String fileName) {
        String normalized = fileName.trim();
        return normalized.endsWith(".ui")
                && (normalized.startsWith(RUNTIME_CARD_TEMPLATE_PREFIX)
                || normalized.startsWith(RUNTIME_CARD_WIDE_TEMPLATE_PREFIX)
                || normalized.startsWith(RUNTIME_PACKAGE_CARD_TEMPLATE_PREFIX)
                || normalized.startsWith(RUNTIME_PACKAGE_CARD_WIDE_TEMPLATE_PREFIX)
                || normalized.startsWith(RUNTIME_CART_CARD_TEMPLATE_PREFIX)
                || normalized.startsWith(RUNTIME_CART_CARD_WIDE_TEMPLATE_PREFIX)
                || normalized.startsWith(RUNTIME_SIDEBAR_CART_ROW_TEMPLATE_PREFIX)
                || normalized.startsWith(RUNTIME_CHECKOUT_SUMMARY_ROW_TEMPLATE_PREFIX)
                || normalized.startsWith(RUNTIME_CHECKOUT_TEMPLATE_PREFIX));
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

    @Nonnull
    private List<Category> loadPluginListingCategories() {
        try {
            return pluginApi.getCategories();
        } catch (Exception e) {
            debug("Failed to load Plugin API listing metadata for Headless supplementation: " + e.getMessage());
            return List.of();
        }
    }

    @Nonnull
    private Map<Integer, CategoryPackage> buildPluginListingPackageMetadata(@Nonnull List<Category> listingCategories) {
        ConcurrentHashMap<Integer, CategoryPackage> metadata = new ConcurrentHashMap<>();
        for (Category category : listingCategories) {
            if (category == null || category.getPackages() == null) {
                continue;
            }
            for (CategoryPackage pack : category.getPackages()) {
                if (pack != null) {
                    metadata.put(pack.getId(), pack);
                }
            }
        }
        return metadata;
    }

    @Nonnull
    private Map<Integer, Category> buildPluginListingCategoryMetadata(@Nonnull List<Category> listingCategories) {
        ConcurrentHashMap<Integer, Category> metadata = new ConcurrentHashMap<>();
        for (Category category : listingCategories) {
            if (category != null) {
                metadata.put(category.getId(), category);
            }
        }
        return metadata;
    }

    @Nonnull
    private CopyOnWriteArrayList<StoreSaleInfo> loadStoreSalesFromPluginApi() {
        try {
            List<com.google.gson.JsonObject> rawSales = pluginApi.getSales();
            return new CopyOnWriteArrayList<>(toStoreSales(rawSales == null ? List.of() : rawSales));
        } catch (Exception e) {
            debug("Failed to load Tebex sales metadata: " + e.getMessage());
            return new CopyOnWriteArrayList<>();
        }
    }

    @Nonnull
    private List<StoreSaleInfo> toStoreSales(@Nullable List<com.google.gson.JsonObject> salesPayload) {
        List<StoreSaleInfo> sales = new ArrayList<>();
        if (salesPayload == null) {
            return sales;
        }

        for (com.google.gson.JsonObject sale : salesPayload) {
            if (sale == null) {
                continue;
            }

            int id = (int) Math.round(getFirstJsonDouble(sale, 0d, "id"));
            String name = getFirstJsonString(sale, "Sale", "name", "sale_name", "title", "header");
            String scope = getFirstJsonString(sale, "", "scope", "sale_scope", "type");

            com.google.gson.JsonObject discount = getJsonObject(sale, "discount");
            String discountType = discount == null
                    ? getFirstJsonString(sale, "", "discount_type", "type")
                    : getFirstJsonString(discount, "", "type", "discount_type");
            double percentage = discount == null
                    ? getFirstJsonDouble(sale, 0d, "percentage", "percent")
                    : getFirstJsonDouble(discount, 0d, "percentage", "percent");
            double amount = discount == null
                    ? getFirstJsonDouble(sale, 0d, "amount", "value", "discount")
                    : getFirstJsonDouble(discount, 0d, "amount", "value", "discount");

            com.google.gson.JsonObject effective = getJsonObject(sale, "effective");
            String eligibility = getFirstJsonString(
                    effective == null ? sale : effective,
                    "",
                    "customer_eligibility",
                    "eligibility",
                    "requirements",
                    "customer_requirements"
            );
            String startAt = getFirstJsonString(
                    sale,
                    getFirstJsonString(effective, "", "start", "starts_at", "start_at", "start_time"),
                    "start",
                    "starts_at",
                    "start_at",
                    "start_time"
            );
            String endAt = getFirstJsonString(
                    sale,
                    getFirstJsonString(effective, "", "end", "expire", "expires_at", "expire_at", "end_time"),
                    "end",
                    "expire",
                    "expires_at",
                    "expire_at",
                    "end_time"
            );

            double minimumBasket = getFirstJsonDouble(
                    effective == null ? sale : effective,
                    Double.NaN,
                    "minimum_basket",
                    "minimum_basket_value",
                    "basket_minimum",
                    "basket_requirement"
            );
            boolean active = resolveStoreSaleActive(sale, startAt, endAt);

            sales.add(new StoreSaleInfo(
                    id,
                    name,
                    formatStoreSaleDiscount(discountType, percentage, amount),
                    scope,
                    sanitizeSaleField(eligibility),
                    formatStoreSaleWindow(startAt, endAt),
                    Double.isNaN(minimumBasket) || minimumBasket <= 0d
                            ? ""
                            : formatCurrency(minimumBasket) + " minimum basket",
                    active
            ));
        }

        sales.sort(Comparator
                .comparing(StoreSaleInfo::active).reversed()
                .thenComparing(StoreSaleInfo::name, String.CASE_INSENSITIVE_ORDER));
        return sales;
    }

    private boolean resolveStoreSaleActive(
            @Nonnull com.google.gson.JsonObject sale,
            @Nullable String startAt,
            @Nullable String endAt
    ) {
        Boolean explicitActive = getFirstJsonBoolean(sale, "active", "enabled");
        Boolean disabled = getFirstJsonBoolean(sale, "disabled");
        if (disabled != null && disabled) {
            return false;
        }
        if (explicitActive != null) {
            return explicitActive;
        }

        Instant now = Instant.now();
        Instant start = parseIsoInstant(startAt);
        Instant end = parseIsoInstant(endAt);
        if (start != null && now.isBefore(start)) {
            return false;
        }
        if (end != null && now.isAfter(end)) {
            return false;
        }
        return true;
    }

    @Nonnull
    private String formatStoreSaleDiscount(@Nullable String discountType, double percentage, double amount) {
        String normalizedType = discountType == null ? "" : discountType.trim().toLowerCase(Locale.ROOT);
        if ("percentage".equals(normalizedType) || percentage > 0d) {
            double value = percentage > 0d ? percentage : amount;
            return trimTrailingZeros(value) + "% off";
        }
        if ("amount".equals(normalizedType) || amount > 0d) {
            return formatCurrency(amount) + " off";
        }
        return "Sale active";
    }

    @Nonnull
    private static String formatStoreSaleWindow(@Nullable String startAt, @Nullable String endAt) {
        String start = formatIsoInstant(startAt);
        String end = formatIsoInstant(endAt);
        if (!start.isBlank() && !end.isBlank()) {
            return "Active " + start + " to " + end;
        }
        if (!start.isBlank()) {
            return "Starts " + start;
        }
        if (!end.isBlank()) {
            return "Ends " + end;
        }
        return "";
    }

    @Nonnull
    private String formatCurrency(double amount) {
        String currencyCode = tebexServerInfo != null
                && tebexServerInfo.getAccount() != null
                && tebexServerInfo.getAccount().getCurrency() != null
                ? safeText(tebexServerInfo.getAccount().getCurrency().getIso4217())
                : "";
        if (currencyCode.isBlank() && headlessWebstore != null && headlessWebstore.getCurrency() != null) {
            currencyCode = safeText(headlessWebstore.getCurrency());
        }
        if (currencyCode.isBlank()) {
            currencyCode = "USD";
        }
        return String.format(Locale.US, "%.2f %s", amount, currencyCode.toUpperCase(Locale.ROOT));
    }

    @Nonnull
    private static String sanitizeSaleField(@Nullable String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    @Nullable
    private static Instant parseIsoInstant(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
        }
        try {
            return java.time.OffsetDateTime.parse(value).toInstant();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nonnull
    private static String formatIsoInstant(@Nullable String value) {
        Instant instant = parseIsoInstant(value);
        if (instant == null) {
            return "";
        }
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(java.time.ZoneOffset.UTC)
                .format(instant);
    }

    @Nonnull
    private static String trimTrailingZeros(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) Math.round(value));
        }
        return String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static Category toPluginCategory(
            @Nonnull HeadlessCategory headlessCategory,
            @Nonnull Map<Integer, String> thumbnailTexturePaths,
            @Nonnull Map<Integer, CategoryPackage> pluginListingMetadata,
            @Nonnull Map<Integer, Category> pluginCategoryMetadata
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
                                thumbnailTexturePaths.get(headlessPackage.getId()),
                                pluginListingMetadata.get(headlessPackage.getId())
                        )
                );
            }
        }

        Category pluginListingCategory = pluginCategoryMetadata.get(headlessCategory.getId());

        return new Category(
                headlessCategory.getId(),
                headlessCategory.getOrder(),
                safeText(headlessCategory.getName()),
                pluginListingCategory == null ? null : pluginListingCategory.getGuiItem(),
                false,
                pluginPackages
        );
    }

    private static CategoryPackage toPluginCategoryPackage(
            @Nonnull HeadlessPackage headlessPackage,
            int order,
            @Nullable String thumbnailTexturePath,
            @Nullable CategoryPackage pluginListingMetadata
    ) {
        double discount = headlessPackage.getDiscount();
        if ((discount <= 0d) && pluginListingMetadata != null && pluginListingMetadata.getSale() != null) {
            discount = pluginListingMetadata.getSale().getDiscount();
        }
        CategoryPackage.Sale sale = new CategoryPackage.Sale(discount > 0, discount);
        return new CategoryPackage(
                headlessPackage.getId(),
                order,
                safeText(headlessPackage.getName()),
                resolvePrice(headlessPackage),
                safeText(headlessPackage.getDescription()),
                chooseImage(thumbnailTexturePath, chooseImage(resolveImage(headlessPackage), pluginListingMetadata == null ? null : pluginListingMetadata.getImage())),
                pluginListingMetadata == null ? null : pluginListingMetadata.getItemId(),
                sale
        );
    }

    private static Package toPluginPackage(
            @Nonnull HeadlessPackage headlessPackage,
            @Nullable String thumbnailTexturePath,
            @Nullable CategoryPackage pluginListingMetadata
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
                chooseImage(thumbnailTexturePath, chooseImage(resolveImage(headlessPackage), pluginListingMetadata == null ? null : pluginListingMetadata.getImage())),
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
                pluginListingMetadata == null ? null : pluginListingMetadata.getItemId(),
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
        if (headlessPackage.getBasePrice() > 0d) {
            return headlessPackage.getBasePrice();
        }
        return headlessPackage.getTotalPrice();
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
        String preferred = sanitizeImageValue(preferredImage);
        String fallback = sanitizeImageValue(fallbackImage);
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }

    @Nullable
    private static String sanitizeImageValue(@Nullable String image) {
        if (image == null) {
            return null;
        }
        String normalized = image.trim();
        if (normalized.isBlank()
                || "false".equalsIgnoreCase(normalized)
                || "null".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    @Nullable
    private static com.google.gson.JsonObject getJsonObject(
            @Nullable com.google.gson.JsonObject jsonObject,
            @Nonnull String key
    ) {
        if (jsonObject == null || !jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
            return null;
        }
        try {
            return jsonObject.getAsJsonObject(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nonnull
    private static String getFirstJsonString(
            @Nullable com.google.gson.JsonObject jsonObject,
            @Nonnull String fallback,
            @Nonnull String... keys
    ) {
        if (jsonObject == null) {
            return fallback;
        }
        for (String key : keys) {
            String value = getJsonString(jsonObject, key, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private static double getFirstJsonDouble(
            @Nullable com.google.gson.JsonObject jsonObject,
            double fallback,
            @Nonnull String... keys
    ) {
        if (jsonObject == null) {
            return fallback;
        }
        for (String key : keys) {
            double value = getJsonDouble(jsonObject, key, Double.NaN);
            if (!Double.isNaN(value)) {
                return value;
            }
        }
        return fallback;
    }

    @Nullable
    private static Boolean getFirstJsonBoolean(
            @Nullable com.google.gson.JsonObject jsonObject,
            @Nonnull String... keys
    ) {
        if (jsonObject == null) {
            return null;
        }
        for (String key : keys) {
            if (!jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
                continue;
            }
            try {
                return jsonObject.get(key).getAsBoolean();
            } catch (Exception ignored) {
            }
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
        storeSalesCache.clear();
        categoryThumbnailTextureCache.clear();
        packageThumbnailSources.clear();
        categoryThumbnailSources.clear();
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

    @Nonnull
    public String getStoreDescription() {
        if (headlessWebstore != null && headlessWebstore.getDescription() != null && !headlessWebstore.getDescription().isBlank()) {
            return headlessWebstore.getDescription();
        }
        return "";
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
        this.storeSalesCache.clear();
        this.categoryThumbnailTextureCache.clear();
        this.categoryThumbnailSources.clear();
        this.packageThumbnailSources.clear();
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

                    ItemContainer inventory = playerComponent.getInventory().getCombinedBackpackStorageHotbar();
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

    public record StoreSaleInfo(
            int id,
            @Nonnull String name,
            @Nonnull String discountText,
            @Nonnull String scope,
            @Nonnull String eligibility,
            @Nonnull String effectiveWindow,
            @Nonnull String minimumBasket,
            boolean active
    ) {
    }

    public static final class AssetPackRebuildResult {
        private final boolean success;
        @Nonnull private final String summary;
        @Nullable private final String detail;
        @Nullable private final String nextStep;

        private AssetPackRebuildResult(
                boolean success,
                @Nonnull String summary,
                @Nullable String detail,
                @Nullable String nextStep
        ) {
            this.success = success;
            this.summary = summary;
            this.detail = detail;
            this.nextStep = nextStep;
        }

        @Nonnull
        public static AssetPackRebuildResult success(
                @Nonnull String summary,
                @Nullable String detail,
                @Nullable String nextStep
        ) {
            return new AssetPackRebuildResult(true, summary, detail, nextStep);
        }

        @Nonnull
        public static AssetPackRebuildResult failure(
                @Nonnull String summary,
                @Nullable String detail,
                @Nullable String nextStep
        ) {
            return new AssetPackRebuildResult(false, summary, detail, nextStep);
        }

        public boolean success() {
            return success;
        }

        @Nonnull
        public String summary() {
            return summary;
        }

        @Nullable
        public String detail() {
            return detail;
        }

        @Nullable
        public String nextStep() {
            return nextStep;
        }
    }

    @Data
    public static final class CheckoutPreviewAsset {
        private final String texturePath;
        private final String templateUiPath;
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

