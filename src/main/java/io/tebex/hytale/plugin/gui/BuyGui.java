package io.tebex.hytale.plugin.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.tebex.hytale.plugin.TebexPlugin;
import io.tebex.sdk.pluginapi.models.Category;
import io.tebex.sdk.pluginapi.models.CategoryPackage;
import io.tebex.sdk.pluginapi.models.Package;
import io.tebex.sdk.pluginapi.models.requests.CheckoutRequest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class BuyGui {
    private static final BuyGui INSTANCE = new BuyGui();

    private BuyGui() {
    }

    public static BuyGui getInstance() {
        return INSTANCE;
    }

    public boolean open(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return false;
        }

        if (getVisibleCategories().isEmpty()) {
            return false;
        }

        player.getPageManager().openCustomPage(ref, store, new TebexStorePage(playerRef));
        if (TebexPlugin.get().isDebugModeEnabled()) {
            player.sendMessage(Message.raw("Buy UI debug is enabled. Check server logs for [BUY-UI] description and image field dumps."));
        }
        return true;
    }

    private static List<Category> getVisibleCategories() {
        List<Category> categories = new ArrayList<>(TebexPlugin.get().getCategoriesCache().values());
        categories.sort(Comparator.comparingInt(Category::getOrder).thenComparingInt(Category::getId));
        categories.removeIf(category -> getCategoryPackages(category).isEmpty());
        return categories;
    }

    private static List<CategoryPackage> getCategoryPackages(@Nonnull Category category) {
        List<CategoryPackage> directPackages = category.getPackages() == null
                ? List.of()
                : category.getPackages();
        if (!directPackages.isEmpty()) {
            List<CategoryPackage> packages = new ArrayList<>(directPackages);
            packages.sort(Comparator.comparingInt(CategoryPackage::getOrder).thenComparingInt(CategoryPackage::getId));
            return packages;
        }

        List<Package> categoryPackages = new ArrayList<>();
        for (Package pack : TebexPlugin.get().getPackagesCache().values()) {
            if (pack == null || pack.getCategory() == null || pack.isDisabled()) {
                continue;
            }
            if (pack.getCategory().getId() == category.getId()) {
                categoryPackages.add(pack);
            }
        }

        categoryPackages.sort(
                Comparator.comparing(Package::getName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(Package::getId)
        );

        List<CategoryPackage> derived = new ArrayList<>(categoryPackages.size());
        int order = 0;
        for (Package pack : categoryPackages) {
            derived.add(new CategoryPackage(
                    pack.getId(),
                    order++,
                    pack.getName(),
                    pack.getPrice(),
                    pack.getDescription(),
                    pack.getImage(),
                    pack.getItemId(),
                    null
            ));
        }
        return derived;
    }

    private static final class TebexStorePage extends InteractiveCustomUIPage<TebexStoreEventData> {
        private static final String STORE_UI = "Pages/TebexStorePage.ui";
        private static final String GRID_ROOT = "#CategoryGrid";
        private static final String HEADER_TITLE_SLOT = "#HeaderTitleSlot";
        private static final String HEADER_SUBTITLE_SLOT = "#HeaderSubtitleSlot";
        private static final String FOOTER_LEFT_SLOT = "#FooterLeftButtonSlot";
        private static final String FOOTER_RIGHT_SLOT = "#FooterRightButtonSlot";
        private static final String DETAIL_CARD_SLOT = "#DetailCardSlot";
        private static final String DETAIL_PRIMARY_SLOT = "#DetailPrimaryButtonSlot";
        private static final String DETAIL_SECONDARY_SLOT = "#DetailSecondaryButtonSlot";
        private static final String CARD_TEMPLATE = "Pages/TebexStoreCard.ui";
        private static final String CARD_TEMPLATE_WIDE = "Pages/TebexStoreCardWide.ui";
        private static final String DETAIL_CARD_TEMPLATE = "Pages/TebexDetailCard.ui";
        private static final String TITLE_TEMPLATE = "Pages/TebexTitleLine.ui";
        private static final String SUBTITLE_TEMPLATE = "Pages/TebexSubtitleLine.ui";
        private static final String PRIMARY_BUTTON_TEMPLATE = "Pages/TebexPrimaryButton.ui";
        private static final String SECONDARY_BUTTON_TEMPLATE = "Pages/TebexSecondaryButton.ui";
        private static final String CARD_ROW_INLINE = "Group { LayoutMode: Left; Anchor: (Bottom: 0); }";

        private static final int CARDS_PER_ROW = 2;
        private static final int PAGE_SIZE = 6;

        private static final String ACTION_OPEN_CATEGORY = "open_category";
        private static final String ACTION_SELECT_PACKAGE = "select_package";
        private static final String ACTION_BUY_SELECTED = "buy_selected";
        private static final String ACTION_BACK = "back";
        private static final String ACTION_PREV = "prev";
        private static final String ACTION_NEXT = "next";
        private static final String ACTION_CLOSE = "close";

        private enum Mode {
            CATEGORIES,
            PACKAGES
        }

        private Mode mode = Mode.CATEGORIES;
        @Nullable private Category selectedCategory;
        private int selectedPackageId = -1;
        private int page = 0;
        private final Set<Integer> debugLoggedPackageIds = new HashSet<>();

        private TebexStorePage(@Nonnull PlayerRef playerRef) {
            super(playerRef, CustomPageLifetime.CanDismiss, TebexStoreEventData.CODEC);
        }

        @Override
        public void build(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull UICommandBuilder commands,
                @Nonnull UIEventBuilder events,
                @Nonnull Store<EntityStore> store
        ) {
            commands.append(STORE_UI);
            commands.clear(GRID_ROOT);
            commands.clear(HEADER_TITLE_SLOT);
            commands.clear(HEADER_SUBTITLE_SLOT);
            commands.clear(FOOTER_LEFT_SLOT);
            commands.clear(FOOTER_RIGHT_SLOT);
            commands.clear(DETAIL_CARD_SLOT);
            commands.clear(DETAIL_PRIMARY_SLOT);
            commands.clear(DETAIL_SECONDARY_SLOT);

            List<CardEntry> cards = new ArrayList<>();
            FooterConfig footer = mode == Mode.CATEGORIES
                    ? buildCategoryCards(commands, cards)
                    : buildPackageCards(commands, cards);

            renderCards(commands, events, cards);
            renderFooter(commands, events, footer);
            renderDetails(commands, events);
        }

        private FooterConfig buildCategoryCards(@Nonnull UICommandBuilder commands, @Nonnull List<CardEntry> cards) {
            List<Category> categories = getVisibleCategories();
            int totalPages = Math.max(1, (categories.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            page = clamp(page, 0, totalPages - 1);

            appendStaticText(commands, HEADER_TITLE_SLOT, "Tebex Store", true);
            appendStaticText(commands, HEADER_SUBTITLE_SLOT, "Select a category.", false);

            int from = page * PAGE_SIZE;
            int to = Math.min(categories.size(), from + PAGE_SIZE);
            for (int i = from; i < to; i++) {
                Category category = categories.get(i);
                int packageCount = getCategoryPackages(category).size();
                cards.add(CardEntry.button(
                        ACTION_OPEN_CATEGORY,
                        Integer.toString(category.getId()),
                        category.getName(),
                        packageCount + " package" + (packageCount == 1 ? "" : "s"),
                        "Open category"
                ));
            }

            if (cards.isEmpty()) {
                cards.add(CardEntry.info("No categories available."));
            }

            ButtonEntry left;
            ButtonEntry right;
            if (totalPages <= 1) {
                left = null;
                right = new ButtonEntry(ACTION_CLOSE, "", "Close");
            } else if (page <= 0) {
                left = new ButtonEntry(ACTION_CLOSE, "", "Close");
                right = new ButtonEntry(ACTION_NEXT, "", "Next");
            } else if (page >= totalPages - 1) {
                left = new ButtonEntry(ACTION_PREV, "", "Previous");
                right = new ButtonEntry(ACTION_CLOSE, "", "Close");
            } else {
                left = new ButtonEntry(ACTION_PREV, "", "Previous");
                right = new ButtonEntry(ACTION_NEXT, "", "Next");
            }
            return new FooterConfig(left, right);
        }

        private FooterConfig buildPackageCards(@Nonnull UICommandBuilder commands, @Nonnull List<CardEntry> cards) {
            if (selectedCategory == null) {
                mode = Mode.CATEGORIES;
                page = 0;
                return buildCategoryCards(commands, cards);
            }

            List<CategoryPackage> packages = getCategoryPackages(selectedCategory);
            int totalPages = Math.max(1, (packages.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            page = clamp(page, 0, totalPages - 1);

            appendStaticText(commands, HEADER_TITLE_SLOT, selectedCategory.getName(), true);
            appendStaticText(commands, HEADER_SUBTITLE_SLOT, "Select a package to view details.", false);

            int from = page * PAGE_SIZE;
            int to = Math.min(packages.size(), from + PAGE_SIZE);
            for (int i = from; i < to; i++) {
                CategoryPackage pack = packages.get(i);
                String price = String.format(Locale.US, "$%.2f", pack.getPrice());
                debugPackageDescriptionFields("grid", pack);
                cards.add(CardEntry.button(
                        ACTION_SELECT_PACKAGE,
                        Integer.toString(pack.getId()),
                        pack.getName(),
                        price,
                        summarizeDescription(resolvePackageDescription(pack))
                ));
            }

            if (cards.isEmpty()) {
                cards.add(CardEntry.info("No packages in this category."));
            }

            ButtonEntry left = page > 0
                    ? new ButtonEntry(ACTION_PREV, "", "Previous")
                    : new ButtonEntry(ACTION_BACK, "", "Back");
            ButtonEntry right = page < totalPages - 1
                    ? new ButtonEntry(ACTION_NEXT, "", "Next")
                    : new ButtonEntry(ACTION_CLOSE, "", "Close");
            return new FooterConfig(left, right);
        }

        private void renderCards(
                @Nonnull UICommandBuilder commands,
                @Nonnull UIEventBuilder events,
                @Nonnull List<CardEntry> cards
        ) {
            int cardsPerRow = cards.size() <= 1 ? 1 : CARDS_PER_ROW;
            String cardTemplate = cardsPerRow == 1 ? CARD_TEMPLATE_WIDE : CARD_TEMPLATE;
            for (int i = 0; i < cards.size(); i++) {
                int rowIndex = i / cardsPerRow;
                int colIndex = i % cardsPerRow;
                if (colIndex == 0) {
                    commands.appendInline(GRID_ROOT, CARD_ROW_INLINE);
                }

                CardEntry card = cards.get(i);
                commands.append(gridRowSelector(rowIndex), cardTemplate);
                commands.set(cardNameSelector(rowIndex, colIndex), uiText(card.title, "Item"));
                commands.set(cardUsageSelector(rowIndex, colIndex), uiText(card.usage, ""));
                commands.set(cardDescriptionSelector(rowIndex, colIndex), uiText(card.description, ""));

                if (card.interactive) {
                    events.addEventBinding(
                            CustomUIEventBindingType.Activating,
                            cardSelector(rowIndex, colIndex),
                            EventData.of(TebexStoreEventData.KEY_ACTION, card.action)
                                    .append(TebexStoreEventData.KEY_VALUE, card.value)
                    );
                }
            }
        }

        private void renderFooter(
                @Nonnull UICommandBuilder commands,
                @Nonnull UIEventBuilder events,
                @Nonnull FooterConfig footer
        ) {
            if (footer.left != null) {
                appendButton(commands, events, FOOTER_LEFT_SLOT, footer.left, false);
            }
            if (footer.right != null) {
                appendButton(commands, events, FOOTER_RIGHT_SLOT, footer.right, false);
            }
        }

        private void renderDetails(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events) {
            if (mode == Mode.CATEGORIES || selectedCategory == null) {
                setDetailCard(
                        commands,
                        "Store Overview",
                        getVisibleCategories().size() + " categories available",
                        "Select a category card to browse packages. Package descriptions are shown here."
                );
                return;
            }

            CategoryPackage selectedPack = findPackage(selectedPackageId);
            if (selectedPack == null) {
                setDetailCard(
                        commands,
                        "Select a package",
                        selectedCategory.getName(),
                        "Click any package card to load detailed information before purchase."
                );
                return;
            }

            debugPackageDescriptionFields("detail", selectedPack);
            setDetailCard(
                    commands,
                    selectedPack.getName(),
                    String.format(Locale.US, "$%.2f", selectedPack.getPrice()),
                    resolvePackageDescription(selectedPack)
            );
            appendButton(
                    commands,
                    events,
                    DETAIL_PRIMARY_SLOT,
                    new ButtonEntry(ACTION_BUY_SELECTED, Integer.toString(selectedPack.getId()), "Buy Selected"),
                    true
            );
        }

        private void setDetailCard(
                @Nonnull UICommandBuilder commands,
                @Nonnull String title,
                @Nonnull String usage,
                @Nonnull String description
        ) {
            commands.append(DETAIL_CARD_SLOT, DETAIL_CARD_TEMPLATE);
            commands.set(detailCardNameSelector(), uiText(title, "Details"));
            commands.set(detailCardUsageSelector(), uiText(usage, ""));
            commands.set(detailCardDescriptionSelector(), uiText(description, ""));
        }

        private void appendStaticText(
                @Nonnull UICommandBuilder commands,
                @Nonnull String slotSelector,
                @Nonnull String text,
                boolean title
        ) {
            commands.append(slotSelector, title ? TITLE_TEMPLATE : SUBTITLE_TEMPLATE);
            commands.set(staticTextSelector(slotSelector), uiText(text, ""));
        }

        private void appendButton(
                @Nonnull UICommandBuilder commands,
                @Nonnull UIEventBuilder events,
                @Nonnull String slotSelector,
                @Nonnull ButtonEntry button,
                boolean primary
        ) {
            commands.append(slotSelector, primary ? PRIMARY_BUTTON_TEMPLATE : SECONDARY_BUTTON_TEMPLATE);
            commands.set(buttonTextSelector(slotSelector), uiText(button.label, ""));
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    buttonSelector(slotSelector),
                    EventData.of(TebexStoreEventData.KEY_ACTION, button.action)
                            .append(TebexStoreEventData.KEY_VALUE, button.value)
            );
        }

        @Override
        public void handleDataEvent(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull Store<EntityStore> store,
                @Nonnull TebexStoreEventData eventData
        ) {
            String action = eventData.getAction();
            String value = eventData.getValue();
            if (action == null || action.isBlank()) {
                return;
            }

            switch (action) {
                case ACTION_OPEN_CATEGORY -> {
                    int categoryId = parseId(value);
                    Category category = findCategory(categoryId);
                    if (category == null) {
                        return;
                    }
                    selectedCategory = category;
                    selectedPackageId = -1;
                    mode = Mode.PACKAGES;
                    page = 0;
                    rebuild();
                }
                case ACTION_SELECT_PACKAGE -> {
                    int packageId = parseId(value);
                    if (findPackage(packageId) == null) {
                        return;
                    }
                    selectedPackageId = packageId;
                    rebuild();
                }
                case ACTION_BUY_SELECTED -> {
                    int packageId = parseId(value);
                    if (packageId <= 0) {
                        packageId = selectedPackageId;
                    }
                    CategoryPackage pack = findPackage(packageId);
                    if (pack == null) {
                        sendPlayerMessage(ref, store, Message.raw("Select a package first."));
                        return;
                    }
                    sendPlayerMessage(ref, store, Message.raw("Creating checkout link for '" + pack.getName() + "'..."));
                    close();
                    createCheckoutLinkAsync(ref, store, pack);
                }
                case ACTION_BACK -> {
                    mode = Mode.CATEGORIES;
                    selectedCategory = null;
                    selectedPackageId = -1;
                    page = 0;
                    rebuild();
                }
                case ACTION_PREV -> {
                    page = Math.max(0, page - 1);
                    rebuild();
                }
                case ACTION_NEXT -> {
                    page++;
                    rebuild();
                }
                case ACTION_CLOSE -> close();
                default -> {
                }
            }
        }

        private void createCheckoutLinkAsync(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull Store<EntityStore> store,
                @Nonnull CategoryPackage pack
        ) {
            TebexPlugin plugin = TebexPlugin.get();
            CompletableFuture.runAsync(() -> {
                try {
                    CheckoutRequest request = new CheckoutRequest(playerRef.getUsername(), pack.getId());
                    var url = plugin.getPluginApi().checkout(request);
                    World world = store.getExternalData().getWorld();
                    world.execute(() -> {
                        if (!ref.isValid()) {
                            return;
                        }
                        Player player = store.getComponent(ref, Player.getComponentType());
                        if (player != null) {
                            player.sendMessage(
                                    Message.raw("Buy '" + pack.getName() + "' by clicking here: " + url.getUrl())
                                            .link(url.getUrl())
                            );
                        }
                    });
                } catch (Exception e) {
                    plugin.error("Failed to create checkout URL for package " + pack.getId(), e);
                    String message = e.getMessage() == null || e.getMessage().isBlank()
                            ? e.getClass().getSimpleName()
                            : e.getMessage();
                    World world = store.getExternalData().getWorld();
                    world.execute(() -> sendPlayerMessage(ref, store, Message.raw("Failed to create checkout URL: " + message)));
                }
            });
        }

        private void sendPlayerMessage(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull Store<EntityStore> store,
                @Nonnull Message message
        ) {
            if (!ref.isValid()) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(message);
            }
        }

        @Nullable
        private Category findCategory(int categoryId) {
            if (categoryId <= 0) {
                return null;
            }
            for (Category category : getVisibleCategories()) {
                if (category.getId() == categoryId) {
                    return category;
                }
            }
            return null;
        }

        @Nullable
        private CategoryPackage findPackage(int packageId) {
            if (packageId <= 0 || selectedCategory == null) {
                return null;
            }
            for (CategoryPackage pack : getCategoryPackages(selectedCategory)) {
                if (pack.getId() == packageId) {
                    return pack;
                }
            }
            return null;
        }

        @Nonnull
        private static String resolvePackageDescription(@Nonnull CategoryPackage pack) {
            String inline = stripMarkup(pack.getDescription());
            if (!inline.isBlank()) {
                return inline;
            }

            Package full = TebexPlugin.get().getPackagesCache().get(pack.getId());
            if (full != null) {
                String plain = stripMarkup(full.getDescription());
                if (!plain.isBlank()) {
                    return plain;
                }
                String html = stripMarkup(full.getDescriptionHtml());
                if (!html.isBlank()) {
                    return html;
                }
            }

            return "No description is available from the Tebex plugin API for this package.";
        }

        @Nonnull
        private static String summarizeDescription(@Nonnull String description) {
            if (description.isBlank()) {
                return "No description available";
            }
            if (description.length() <= 92) {
                return description;
            }
            return description.substring(0, 89).trim() + "...";
        }

        private void debugPackageDescriptionFields(@Nonnull String context, @Nonnull CategoryPackage pack) {
            TebexPlugin plugin = TebexPlugin.get();
            if (!plugin.isDebugModeEnabled()) {
                return;
            }
            if (!"detail".equals(context) && !debugLoggedPackageIds.add(pack.getId())) {
                return;
            }
            debugLoggedPackageIds.add(pack.getId());

            Package full = plugin.getPackagesCache().get(pack.getId());
            plugin.debug(
                    "[BUY-UI] " + context
                            + " packId=" + pack.getId()
                            + " name='" + sanitizeUiText(pack.getName()) + "'"
                            + " category.description=" + debugField(pack.getDescription())
                            + " category.image=" + debugField(pack.getImage())
                            + " full.description=" + debugField(full == null ? null : full.getDescription())
                            + " full.description_html=" + debugField(full == null ? null : full.getDescriptionHtml())
                            + " full.image=" + debugField(full == null ? null : full.getImage())
            );
        }

        @Nonnull
        private static String debugField(@Nullable String value) {
            if (value == null) {
                return "null";
            }
            String compact = value.replaceAll("\\s+", " ").trim();
            if (compact.isBlank()) {
                return "blank";
            }
            String sample = compact.length() > 140 ? compact.substring(0, 140) + "..." : compact;
            sample = sample.replace("\"", "'");
            return "len=" + value.length() + ",sample=\"" + sample + "\"";
        }

        @Nonnull
        private static String stripMarkup(@Nullable String raw) {
            if (raw == null || raw.isBlank()) {
                return "";
            }
            return sanitizeUiText(raw);
        }

        @Nonnull
        private static Message uiText(@Nullable String raw, @Nonnull String fallback) {
            String text = sanitizeUiText(raw);
            if (text.isBlank()) {
                text = fallback;
            }
            return Message.raw(text);
        }

        @Nonnull
        private static String sanitizeUiText(@Nullable String raw) {
            if (raw == null || raw.isBlank()) {
                return "";
            }

            String text = raw.replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">");

            text = text.replaceAll("<[^>]*>", " ");

            String allowed = ".,:;!?@#$%^&*()_+=-/\\'\"";
            StringBuilder cleaned = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (Character.isLetterOrDigit(ch) || Character.isWhitespace(ch) || allowed.indexOf(ch) >= 0) {
                    cleaned.append(ch);
                } else {
                    cleaned.append(' ');
                }
            }

            return cleaned.toString().replaceAll("\\s+", " ").trim();
        }

        private static int parseId(@Nullable String value) {
            if (value == null || value.isBlank()) {
                return -1;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }

        private static String gridRowSelector(int rowIndex) {
            return GRID_ROOT + "[" + rowIndex + "]";
        }

        private static String cardSelector(int rowIndex, int colIndex) {
            return gridRowSelector(rowIndex) + "[" + colIndex + "]";
        }

        private static String cardNameSelector(int rowIndex, int colIndex) {
            return cardSelector(rowIndex, colIndex) + " #SubcommandName.TextSpans";
        }

        private static String cardUsageSelector(int rowIndex, int colIndex) {
            return cardSelector(rowIndex, colIndex) + " #SubcommandUsage.TextSpans";
        }

        private static String cardDescriptionSelector(int rowIndex, int colIndex) {
            return cardSelector(rowIndex, colIndex) + " #SubcommandDescription.TextSpans";
        }

        private static String buttonSelector(@Nonnull String slotSelector) {
            return slotSelector + "[0]";
        }

        private static String buttonTextSelector(@Nonnull String slotSelector) {
            return slotSelector + "[0].TextSpans";
        }

        private static String staticTextSelector(@Nonnull String slotSelector) {
            return slotSelector + "[0].TextSpans";
        }

        private static String detailCardNameSelector() {
            return DETAIL_CARD_SLOT + "[0] #SubcommandName.TextSpans";
        }

        private static String detailCardUsageSelector() {
            return DETAIL_CARD_SLOT + "[0] #SubcommandUsage.TextSpans";
        }

        private static String detailCardDescriptionSelector() {
            return DETAIL_CARD_SLOT + "[0] #SubcommandDescription.TextSpans";
        }

        private static int clamp(int value, int min, int max) {
            if (value < min) {
                return min;
            }
            if (value > max) {
                return max;
            }
            return value;
        }

        private record CardEntry(
                String action,
                String value,
                String title,
                String usage,
                String description,
                boolean interactive
        ) {
            private static CardEntry button(String action, String value, String title, String usage, String description) {
                return new CardEntry(action, value, title, usage, description, true);
            }

            private static CardEntry info(String title) {
                return new CardEntry("", "", title, "", "", false);
            }
        }

        private record ButtonEntry(String action, String value, String label) {
        }

        private record FooterConfig(@Nullable ButtonEntry left, @Nullable ButtonEntry right) {
        }
    }

    public static final class TebexStoreEventData {
        public static final String KEY_ACTION = "Action";
        public static final String KEY_VALUE = "Value";
        public static final BuilderCodec<TebexStoreEventData> CODEC;

        private String action = "";
        private String value = "";

        static {
            CODEC = BuilderCodec.builder(TebexStoreEventData.class, TebexStoreEventData::new)
                    .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), TebexStoreEventData::setAction, TebexStoreEventData::getAction)
                    .add()
                    .append(new KeyedCodec<>(KEY_VALUE, Codec.STRING), TebexStoreEventData::setValue, TebexStoreEventData::getValue)
                    .add()
                    .build();
        }

        public TebexStoreEventData() {
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action == null ? "" : action;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value == null ? "" : value;
        }
    }
}
