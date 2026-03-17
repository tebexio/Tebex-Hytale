package io.tebex.hytale.plugin.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.tebex.hytale.plugin.TebexPlugin;
import io.tebex.sdk.headlessapi.models.Basket;
import io.tebex.sdk.pluginapi.models.Category;
import io.tebex.sdk.pluginapi.models.CategoryPackage;
import io.tebex.sdk.pluginapi.models.Package;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class BuyGui {
    private static final BuyGui INSTANCE = new BuyGui();
    private final ConcurrentHashMap<String, CartSession> cartSessions = new ConcurrentHashMap<>();

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

    @Nonnull
    private CartSession getOrCreateCartSession(@Nonnull PlayerRef playerRef) {
        String key = playerRef.getUuid().toString();
        return cartSessions.computeIfAbsent(key, ignored -> new CartSession());
    }

    private static boolean isCartEnabled() {
        TebexPlugin.TebexConfig cfg = TebexPlugin.get().getConfig().get();
        return cfg == null || cfg.isCartEnabled();
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
        private static final String PAGE_TITLE_SLOT = "#PageTitleSlot";
        private static final String HEADER_TITLE_SLOT = "#HeaderTitleSlot";
        private static final String HEADER_SUBTITLE_SLOT = "#HeaderSubtitleSlot";
        private static final String FOOTER_LEFT_SLOT = "#FooterLeftButtonSlot";
        private static final String FOOTER_RIGHT_SLOT = "#FooterRightButtonSlot";
        private static final String DETAIL_CARD_SLOT = "#DetailCardSlot";
        private static final String DETAIL_PRIMARY_SLOT = "#DetailPrimaryButtonSlot";
        private static final String DETAIL_SECONDARY_SLOT = "#DetailSecondaryButtonSlot";
        private static final String CARD_ROW_TEMPLATE = "Pages/TebexStoreCardRow.ui";
        private static final String CARD_TEMPLATE = "Pages/TebexStoreCard.ui";
        private static final String CARD_TEMPLATE_WIDE = "Pages/TebexStoreCardWide.ui";
        private static final String CARD_ICON_TEMPLATE = "Pages/TebexStoreCardIcon.ui";
        private static final String CARD_ICON_TEMPLATE_WIDE = "Pages/TebexStoreCardIconWide.ui";
        private static final String CART_CARD_TEMPLATE = "Pages/TebexCartCard.ui";
        private static final String CART_CARD_TEMPLATE_WIDE = "Pages/TebexCartCardWide.ui";
        private static final String CART_CARD_ICON_TEMPLATE = "Pages/TebexCartCardIcon.ui";
        private static final String CART_CARD_ICON_TEMPLATE_WIDE = "Pages/TebexCartCardIconWide.ui";
        private static final String DETAIL_CARD_TEMPLATE = "Pages/TebexDetailCard.ui";
        private static final String DETAIL_CARD_CHECKOUT_TEMPLATE = "Pages/TebexDetailCardCheckout.ui";
        private static final String PAGE_TITLE_TEMPLATE = "Pages/TebexPageTitle.ui";
        private static final String TITLE_TEMPLATE = "Pages/TebexTitleLine.ui";
        private static final String SUBTITLE_TEMPLATE = "Pages/TebexSubtitleLine.ui";
        private static final String PRIMARY_BUTTON_TEMPLATE = "Pages/TebexPrimaryButton.ui";
        private static final String SECONDARY_BUTTON_TEMPLATE = "Pages/TebexSecondaryButton.ui";
        private static final String PAGE_THUMBNAIL_PREFIX = "Assets/";
        private static final String UI_THUMBNAIL_PREFIX = "UI/Custom/Pages/Assets/";
        private static final String COMMON_UI_THUMBNAIL_PREFIX = "Common/UI/Custom/Pages/Assets/";

        private static final int CARDS_PER_ROW = 2;
        private static final int PAGE_SIZE = 6;
        private static final int CART_PAGE_SIZE = 4;

        private static final String ACTION_OPEN_CATEGORY = "open_category";
        private static final String ACTION_SELECT_PACKAGE = "select_package";
        private static final String ACTION_ADD_TO_CART = "add_to_cart";
        private static final String ACTION_BUY_NOW = "buy_now";
        private static final String ACTION_OPEN_CART = "open_cart";
        private static final String ACTION_SELECT_CART_ITEM = "select_cart_item";
        private static final String ACTION_REMOVE_CART_ITEM = "remove_cart_item";
        private static final String ACTION_INCREMENT_CART_ITEM = "increment_cart_item";
        private static final String ACTION_DECREMENT_CART_ITEM = "decrement_cart_item";
        private static final String ACTION_CLEAR_CART = "clear_cart";
        private static final String ACTION_CHECKOUT_CART = "checkout_cart";
        private static final String ACTION_BACK = "back";
        private static final String ACTION_PREV = "prev";
        private static final String ACTION_NEXT = "next";
        private static final String ACTION_CLOSE = "close";

        private enum Mode {
            CATEGORIES,
            PACKAGES,
            CART
        }

        private Mode mode = Mode.CATEGORIES;
        @Nullable private Category selectedCategory;
        private int selectedPackageId = -1;
        private int page = 0;
        private final Set<Integer> debugLoggedPackageIds = new HashSet<>();
        private final CartSession cartSession;

        private TebexStorePage(@Nonnull PlayerRef playerRef) {
            super(playerRef, CustomPageLifetime.CanDismiss, TebexStoreEventData.CODEC);
            this.cartSession = BuyGui.getInstance().getOrCreateCartSession(playerRef);
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
            commands.clear(PAGE_TITLE_SLOT);
            commands.clear(HEADER_TITLE_SLOT);
            commands.clear(HEADER_SUBTITLE_SLOT);
            commands.clear(FOOTER_LEFT_SLOT);
            commands.clear(FOOTER_RIGHT_SLOT);
            commands.clear(DETAIL_CARD_SLOT);
            commands.clear(DETAIL_PRIMARY_SLOT);
            commands.clear(DETAIL_SECONDARY_SLOT);

            appendPageTitle(commands);

            List<CardEntry> cards = new ArrayList<>();
            FooterConfig footer = switch (mode) {
                case CATEGORIES -> buildCategoryCards(commands, cards);
                case PACKAGES -> buildPackageCards(commands, cards);
                case CART -> buildCartCards(commands, cards);
            };

            renderCards(commands, events, cards);
            renderFooter(commands, events, footer);
            renderDetails(commands, events);
        }

        private FooterConfig buildCategoryCards(@Nonnull UICommandBuilder commands, @Nonnull List<CardEntry> cards) {
            List<Category> categories = getVisibleCategories();
            int totalPages = Math.max(1, (categories.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            page = clamp(page, 0, totalPages - 1);

            appendStaticText(commands, HEADER_TITLE_SLOT, "Categories", true);
            appendStaticText(commands, HEADER_SUBTITLE_SLOT, "Browse the available store categories.", false);

            int from = page * PAGE_SIZE;
            int to = Math.min(categories.size(), from + PAGE_SIZE);
            for (int i = from; i < to; i++) {
                Category category = categories.get(i);
                int packageCount = getCategoryPackages(category).size();
                cards.add(CardEntry.button(
                        ACTION_OPEN_CATEGORY,
                        Integer.toString(category.getId()),
                        category.getName(),
                        uiText(packageCount + " package" + (packageCount == 1 ? "" : "s"), ""),
                        category.isOnlySubcategories() ? "Browse subcategories and featured offers." : "Browse packages, pricing, and featured offers.",
                        resolveCategoryThumbnailTexture(category),
                        resolveCategoryItemId(category)
                ));
            }

            if (cards.isEmpty()) {
                cards.add(CardEntry.info("No categories available."));
            }

            ButtonEntry left;
            ButtonEntry right;
            if (totalPages <= 1) {
                left = isCartEnabled() && !cartSession.isEmpty()
                        ? new ButtonEntry(ACTION_OPEN_CART, "", "View Cart (" + cartSession.getTotalItems() + ")")
                        : null;
                right = new ButtonEntry(ACTION_CLOSE, "", "Close");
            } else if (page <= 0) {
                left = isCartEnabled() && !cartSession.isEmpty()
                        ? new ButtonEntry(ACTION_OPEN_CART, "", "View Cart (" + cartSession.getTotalItems() + ")")
                        : new ButtonEntry(ACTION_CLOSE, "", "Close");
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
            String subtitle = "Select a package to view details.";
            if (isCartEnabled()) {
                subtitle = subtitle + " Cart: " + cartSession.getTotalItems() + " item" + (cartSession.getTotalItems() == 1 ? "" : "s") + ".";
            }
            appendStaticText(commands, HEADER_SUBTITLE_SLOT, subtitle, false);

            int from = page * PAGE_SIZE;
            int to = Math.min(packages.size(), from + PAGE_SIZE);
            for (int i = from; i < to; i++) {
                CategoryPackage pack = packages.get(i);
                debugPackageDescriptionFields("grid", pack);
                cards.add(CardEntry.button(
                        ACTION_SELECT_PACKAGE,
                        Integer.toString(pack.getId()),
                        pack.getName(),
                        buildPackagePriceMessage(pack),
                        summarizeDescription(resolvePackageDescription(pack)),
                        resolveCardThumbnailTexture(pack),
                        resolvePackageItemId(pack)
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

        private void appendPageTitle(@Nonnull UICommandBuilder commands) {
            TebexPlugin plugin = TebexPlugin.get();
            String storeName = sanitizeUiText(plugin.getStoreName());
            String storeUrl = sanitizeUiText(plugin.getStoreUrl());
            String title = storeName.isBlank() ? "Tebex Store" : storeName;
            if (!storeUrl.isBlank()) {
                title = title + " (" + storeUrl + ")";
            }
            commands.append(PAGE_TITLE_SLOT, PAGE_TITLE_TEMPLATE);
            commands.set(PAGE_TITLE_SLOT + "[0].TextSpans", uiText(title, "Tebex Store"));
        }

        private FooterConfig buildCartCards(@Nonnull UICommandBuilder commands, @Nonnull List<CardEntry> cards) {
            List<CartEntry> entries = getCartEntries();
            int totalPages = Math.max(1, (entries.size() + CART_PAGE_SIZE - 1) / CART_PAGE_SIZE);
            page = clamp(page, 0, totalPages - 1);

            appendStaticText(commands, HEADER_TITLE_SLOT, "Cart", true);
            appendStaticText(commands, HEADER_SUBTITLE_SLOT, buildCartSubtitle(entries), false);

            int from = page * CART_PAGE_SIZE;
            int to = Math.min(entries.size(), from + CART_PAGE_SIZE);
            for (int i = from; i < to; i++) {
                CartEntry entry = entries.get(i);
                cards.add(CardEntry.button(
                        ACTION_SELECT_CART_ITEM,
                        Integer.toString(entry.pack().getId()),
                        entry.pack().getName(),
                        buildCartEntryUsageMessage(entry),
                        summarizeDescription(resolvePackageDescription(entry.pack())),
                        resolveCardThumbnailTexture(entry.pack()),
                        resolvePackageItemId(entry.pack())
                ));
            }

            if (cards.isEmpty()) {
                cards.add(CardEntry.info("Your cart is empty."));
            }

            ButtonEntry left = page > 0
                    ? new ButtonEntry(ACTION_PREV, "", "Previous")
                    : new ButtonEntry(ACTION_BACK, "", "Continue Shopping");
            ButtonEntry right;
            if (page < totalPages - 1) {
                right = new ButtonEntry(ACTION_NEXT, "", "Next");
            } else if (entries.isEmpty()) {
                right = new ButtonEntry(ACTION_CLOSE, "", "Close");
            } else {
                right = new ButtonEntry(ACTION_CHECKOUT_CART, "", "Create Checkout Link");
            }
            return new FooterConfig(left, right);
        }

        private void renderCards(
                @Nonnull UICommandBuilder commands,
                @Nonnull UIEventBuilder events,
                @Nonnull List<CardEntry> cards
        ) {
            if (mode == Mode.CART) {
                renderCartCards(commands, events, cards);
                return;
            }

            renderStoreCards(commands, events, cards);
        }

        private void renderStoreCards(
                @Nonnull UICommandBuilder commands,
                @Nonnull UIEventBuilder events,
                @Nonnull List<CardEntry> cards
        ) {
            boolean singleColumn = cards.size() <= 1;
            int cardsPerRow = singleColumn ? 1 : CARDS_PER_ROW;
            for (int i = 0; i < cards.size(); i++) {
                int rowIndex = i / cardsPerRow;
                int colIndex = i % cardsPerRow;
                if (colIndex == 0) {
                    commands.append(GRID_ROOT, CARD_ROW_TEMPLATE);
                }

                CardEntry card = cards.get(i);
                String template = resolveCardTemplate(singleColumn, card.thumbnailTexturePath, card.itemId);
                commands.append(gridRowSelector(rowIndex), template);
                commands.set(cardNameSelector(rowIndex, colIndex), uiText(card.title, "Item"));
                commands.set(cardUsageSelector(rowIndex, colIndex), card.usage);
                commands.set(cardDescriptionSelector(rowIndex, colIndex), uiText(card.description, ""));
                if (isIconTemplate(template) && card.itemId != null && !card.itemId.isBlank()) {
                    commands.set(cardItemIdSelector(rowIndex, colIndex), card.itemId);
                    commands.set(cardItemQuantitySelector(rowIndex, colIndex), 1);
                }

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

        private void renderCartCards(
                @Nonnull UICommandBuilder commands,
                @Nonnull UIEventBuilder events,
                @Nonnull List<CardEntry> cards
        ) {
            boolean singleColumn = cards.size() <= 1;
            int cardsPerRow = singleColumn ? 1 : CARDS_PER_ROW;
            for (int i = 0; i < cards.size(); i++) {
                int rowIndex = i / cardsPerRow;
                int colIndex = i % cardsPerRow;
                if (colIndex == 0) {
                    commands.append(GRID_ROOT, CARD_ROW_TEMPLATE);
                }

                CardEntry card = cards.get(i);
                if (!card.interactive) {
                    String infoTemplate = singleColumn ? CARD_TEMPLATE_WIDE : CARD_TEMPLATE;
                    commands.append(gridRowSelector(rowIndex), infoTemplate);
                    commands.set(cardNameSelector(rowIndex, colIndex), uiText(card.title, "Item"));
                    commands.set(cardUsageSelector(rowIndex, colIndex), card.usage);
                    commands.set(cardDescriptionSelector(rowIndex, colIndex), uiText(card.description, ""));
                    continue;
                }

                String template = resolveCartCardTemplate(singleColumn, card.thumbnailTexturePath, card.itemId);
                commands.append(gridRowSelector(rowIndex), template);
                commands.set(cardNameSelector(rowIndex, colIndex), uiText(card.title, "Item"));
                commands.set(cardUsageSelector(rowIndex, colIndex), card.usage);
                commands.set(cardDescriptionSelector(rowIndex, colIndex), uiText(card.description, ""));
                if (isCartIconTemplate(template) && card.itemId != null && !card.itemId.isBlank()) {
                    commands.set(cardItemIdSelector(rowIndex, colIndex), card.itemId);
                    commands.set(cardItemQuantitySelector(rowIndex, colIndex), 1);
                }

                CartEntry entry = findCartEntry(parseId(card.value));
                if (entry != null) {
                    commands.set(cartQuantitySelector(rowIndex, colIndex), uiText(Integer.toString(entry.quantity()), "1"));
                }

                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        cartRemoveButtonSelector(rowIndex, colIndex),
                        EventData.of(TebexStoreEventData.KEY_ACTION, ACTION_REMOVE_CART_ITEM)
                                .append(TebexStoreEventData.KEY_VALUE, card.value)
                );
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        cartIncrementButtonSelector(rowIndex, colIndex),
                        EventData.of(TebexStoreEventData.KEY_ACTION, ACTION_INCREMENT_CART_ITEM)
                                .append(TebexStoreEventData.KEY_VALUE, card.value)
                );
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        cartDecrementButtonSelector(rowIndex, colIndex),
                        EventData.of(TebexStoreEventData.KEY_ACTION, ACTION_DECREMENT_CART_ITEM)
                                .append(TebexStoreEventData.KEY_VALUE, card.value)
                );
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
            if (mode == Mode.CATEGORIES) {
                setDetailCard(
                        commands,
                        "Store Overview",
                        uiText(buildStoreOverviewUsage(), ""),
                        buildStoreOverviewDescription()
                );
                if (isCartEnabled() && !cartSession.isEmpty()) {
                    appendButton(
                            commands,
                            events,
                            DETAIL_SECONDARY_SLOT,
                            new ButtonEntry(ACTION_OPEN_CART, "", "View Cart (" + cartSession.getTotalItems() + ")"),
                            false
                    );
                }
                return;
            }

            if (mode == Mode.CART) {
                renderCartDetails(commands, events);
                return;
            }

            if (selectedCategory == null) {
                mode = Mode.CATEGORIES;
                page = 0;
                setDetailCard(
                        commands,
                        "Store Overview",
                        uiText(buildStoreOverviewUsage(), ""),
                        buildStoreOverviewDescription()
                );
                return;
            }

            CategoryPackage selectedPack = findPackage(selectedPackageId);
            if (selectedPack == null) {
                setDetailCard(
                        commands,
                        "Select a package",
                        uiText(selectedCategory.getName(), ""),
                        "Click any package card to load detailed information before purchase."
                );
                if (isCartEnabled()) {
                    appendButton(
                            commands,
                            events,
                            DETAIL_SECONDARY_SLOT,
                            new ButtonEntry(ACTION_OPEN_CART, "", "View Cart (" + cartSession.getTotalItems() + ")"),
                            false
                    );
                }
                return;
            }

            debugPackageDescriptionFields("detail", selectedPack);
            setDetailCard(
                    commands,
                    selectedPack.getName(),
                    buildPackagePriceMessage(selectedPack),
                    buildPackageDetailDescription(selectedPack)
            );

            if (isCartEnabled()) {
                appendButton(
                        commands,
                        events,
                        DETAIL_PRIMARY_SLOT,
                        new ButtonEntry(ACTION_ADD_TO_CART, Integer.toString(selectedPack.getId()), "Add To Cart"),
                        true
                );
                appendButton(
                        commands,
                        events,
                        DETAIL_SECONDARY_SLOT,
                        new ButtonEntry(ACTION_OPEN_CART, "", "View Cart (" + cartSession.getTotalItems() + ")"),
                        false
                );
            } else {
                appendButton(
                        commands,
                        events,
                        DETAIL_PRIMARY_SLOT,
                        new ButtonEntry(ACTION_BUY_NOW, Integer.toString(selectedPack.getId()), "Buy Now"),
                        true
                );
            }
        }

        private void renderCartDetails(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events) {
            List<CartEntry> entries = getCartEntries();
            if (entries.isEmpty()) {
                setDetailCard(
                        commands,
                        "Cart Empty",
                        uiText("No packages added", ""),
                        "Add packages from the store, then checkout to generate your checkout link."
                );
                if (selectedCategory != null) {
                    appendButton(
                            commands,
                            events,
                            DETAIL_SECONDARY_SLOT,
                            new ButtonEntry(ACTION_BACK, "", "Back To Store"),
                            false
                    );
                }
                return;
            }

            setDetailCard(
                    commands,
                    "Cart Summary",
                    buildCartSidebarUsage(entries),
                    buildCartSidebarDescription(entries)
            );
            appendButton(
                    commands,
                    events,
                    DETAIL_PRIMARY_SLOT,
                    new ButtonEntry(ACTION_CLEAR_CART, "", "Clear Cart"),
                    false
            );
        }

        private void setDetailCard(
                @Nonnull UICommandBuilder commands,
                @Nonnull String title,
                @Nonnull Message usage,
                @Nonnull String description
        ) {
            commands.append(DETAIL_CARD_SLOT, DETAIL_CARD_TEMPLATE);
            commands.set(detailCardNameSelector(), uiText(title, "Details"));
            commands.set(detailCardUsageSelector(), usage);
            commands.set(detailCardDescriptionSelector(), uiText(description, ""));
        }

        private void setCheckoutDetailCard(
                @Nonnull UICommandBuilder commands,
                @Nonnull String title,
                @Nonnull Message usage,
                @Nonnull String description
        ) {
            commands.append(DETAIL_CARD_SLOT, DETAIL_CARD_CHECKOUT_TEMPLATE);
            commands.set(detailCardNameSelector(), uiText(title, "Details"));
            commands.set(detailCardUsageSelector(), usage);
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
                case ACTION_ADD_TO_CART -> {
                    int packageId = parseId(value);
                    if (packageId <= 0) {
                        packageId = selectedPackageId;
                    }
                    CategoryPackage pack = findPackage(packageId);
                    if (pack == null) {
                        sendPlayerMessage(ref, store, Message.raw("Select a package first."));
                        return;
                    }
                    addToCartAsync(ref, store, pack);
                }
                case ACTION_BUY_NOW -> {
                    int packageId = parseId(value);
                    if (packageId <= 0) {
                        packageId = selectedPackageId;
                    }
                    CategoryPackage pack = findPackage(packageId);
                    if (pack == null) {
                        sendPlayerMessage(ref, store, Message.raw("Select a package first."));
                        return;
                    }
                    sendPlayerMessage(ref, store, Message.raw("Creating checkout for '" + pack.getName() + "'..."));
                    close();
                    HashMap<Integer, Integer> singleItem = new HashMap<>();
                    singleItem.put(pack.getId(), 1);
                    createCheckoutFromCartAsync(ref, store, singleItem, false);
                }
                case ACTION_OPEN_CART -> {
                    mode = Mode.CART;
                    page = 0;
                    rebuild();
                }
                case ACTION_SELECT_CART_ITEM -> {
                    int packageId = parseId(value);
                    if (findCartEntry(packageId) == null) {
                        return;
                    }
                    selectedPackageId = packageId;
                    rebuild();
                }
                case ACTION_REMOVE_CART_ITEM -> {
                    int packageId = parseId(value);
                    if (packageId <= 0) {
                        packageId = selectedPackageId;
                    }
                    if (packageId <= 0) {
                        sendPlayerMessage(ref, store, Message.raw("Select a cart package first."));
                        return;
                    }
                    removeFromCartAsync(ref, store, packageId);
                }
                case ACTION_INCREMENT_CART_ITEM -> {
                    int packageId = parseId(value);
                    if (packageId <= 0) {
                        sendPlayerMessage(ref, store, Message.raw("Select a cart package first."));
                        return;
                    }
                    changeCartQuantityAsync(ref, store, packageId, 1);
                }
                case ACTION_DECREMENT_CART_ITEM -> {
                    int packageId = parseId(value);
                    if (packageId <= 0) {
                        sendPlayerMessage(ref, store, Message.raw("Select a cart package first."));
                        return;
                    }
                    changeCartQuantityAsync(ref, store, packageId, -1);
                }
                case ACTION_CLEAR_CART -> {
                    if (cartSession.isEmpty()) {
                        sendPlayerMessage(ref, store, Message.raw("Your cart is already empty."));
                        return;
                    }
                    clearCartAsync(ref, store);
                }
                case ACTION_CHECKOUT_CART -> {
                    if (cartSession.isEmpty()) {
                        sendPlayerMessage(ref, store, Message.raw("Your cart is empty."));
                        return;
                    }
                    sendPlayerMessage(ref, store, Message.raw("Preparing your checkout link..."));
                    createCheckoutFromCartAsync(ref, store, cartSession.snapshotQuantities(), true);
                }
                case ACTION_BACK -> {
                    if (mode == Mode.CART && selectedCategory != null) {
                        mode = Mode.PACKAGES;
                    } else {
                        mode = Mode.CATEGORIES;
                        selectedCategory = null;
                    }
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

        private void addToCartAsync(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull Store<EntityStore> store,
                @Nonnull CategoryPackage pack
        ) {
            TebexPlugin plugin = TebexPlugin.get();
            CompletableFuture.runAsync(() -> {
                try {
                    String basketIdent = ensureCartBasketIdent(plugin);
                    plugin.getHeadlessApi().addBasketPackage(basketIdent, pack.getId(), 1);
                    Basket basket = plugin.getHeadlessApi().getBasket(basketIdent);
                    syncCartSessionFromBasket(basket);
                    World world = store.getExternalData().getWorld();
                    world.execute(() -> {
                        sendPlayerMessage(ref, store, Message.raw("Added '" + pack.getName() + "' to cart."));
                        rebuild();
                    });
                } catch (Exception e) {
                    if (isBasketAccountMismatchError(e)) {
                        plugin.warnNoLog(
                                "Headless basket/account mismatch detected while adding to cart.",
                                "The public token from /information may point to a different store than SecretKey, or this player has a stale basket session."
                        );
                        cartSession.setBasketIdent("");
                        try {
                            String basketIdent = ensureCartBasketIdent(plugin);
                            plugin.getHeadlessApi().addBasketPackage(basketIdent, pack.getId(), 1);
                            Basket basket = plugin.getHeadlessApi().getBasket(basketIdent);
                            syncCartSessionFromBasket(basket);
                            World world = store.getExternalData().getWorld();
                            world.execute(() -> {
                                sendPlayerMessage(ref, store, Message.raw("Cart session refreshed. Added '" + pack.getName() + "' to cart."));
                                rebuild();
                            });
                            return;
                        } catch (Exception retryException) {
                            plugin.error("Failed to add package " + pack.getId() + " to cart after refreshing basket session", retryException);
                            World world = store.getExternalData().getWorld();
                            world.execute(() -> sendPlayerMessage(
                                    ref,
                                    store,
                                    Message.raw("Cart add failed: Headless token from /information does not match the store connected by SecretKey.")
                            ));
                            return;
                        }
                    }

                    plugin.error("Failed to add package " + pack.getId() + " to cart", e);
                    String message = e.getMessage() == null || e.getMessage().isBlank()
                            ? e.getClass().getSimpleName()
                            : e.getMessage();
                    World world = store.getExternalData().getWorld();
                    world.execute(() -> sendPlayerMessage(ref, store, Message.raw("Failed to add to cart: " + message)));
                }
            });
        }

        private void changeCartQuantityAsync(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull Store<EntityStore> store,
                int packageId,
                int delta
        ) {
            CartEntry entry = findCartEntry(packageId);
            if (entry == null) {
                sendPlayerMessage(ref, store, Message.raw("That cart item is no longer available."));
                rebuild();
                return;
            }

            if (delta == 0) {
                return;
            }

            Package full = TebexPlugin.get().getPackagesCache().get(packageId);
            if (delta > 0 && full != null && full.isDisableQuantity()) {
                sendPlayerMessage(ref, store, Message.raw("This package only allows a quantity of 1."));
                return;
            }
            if (delta < 0 && entry.quantity() <= 1) {
                sendPlayerMessage(ref, store, Message.raw("Quantity is already 1. Use Remove to delete the item."));
                return;
            }

            TebexPlugin plugin = TebexPlugin.get();
            int targetQuantity = entry.quantity() + delta;
            CompletableFuture.runAsync(() -> {
                try {
                    String basketIdent = ensureCartBasketIdent(plugin);
                    Basket basket;
                    if (delta > 0) {
                        plugin.getHeadlessApi().addBasketPackage(basketIdent, packageId, delta);
                        basket = plugin.getHeadlessApi().getBasket(basketIdent);
                    } else {
                        plugin.getHeadlessApi().removeBasketPackage(basketIdent, packageId);
                        if (targetQuantity > 0) {
                            plugin.getHeadlessApi().addBasketPackage(basketIdent, packageId, targetQuantity);
                        }
                        basket = plugin.getHeadlessApi().getBasket(basketIdent);
                    }

                    syncCartSessionFromBasket(basket);
                    World world = store.getExternalData().getWorld();
                    world.execute(this::rebuild);
                } catch (Exception e) {
                    plugin.error("Failed to update cart quantity for package " + packageId, e);
                    String message = e.getMessage() == null || e.getMessage().isBlank()
                            ? e.getClass().getSimpleName()
                            : e.getMessage();
                    World world = store.getExternalData().getWorld();
                    world.execute(() -> sendPlayerMessage(ref, store, Message.raw("Failed to update quantity: " + message)));
                }
            });
        }

        private void removeFromCartAsync(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull Store<EntityStore> store,
                int packageId
        ) {
            TebexPlugin plugin = TebexPlugin.get();
            CompletableFuture.runAsync(() -> {
                try {
                    String basketIdent = cartSession.getBasketIdent();
                    if (!basketIdent.isBlank()) {
                        plugin.getHeadlessApi().removeBasketPackage(basketIdent, packageId);
                        Basket basket = plugin.getHeadlessApi().getBasket(basketIdent);
                        syncCartSessionFromBasket(basket);
                    }
                    cartSession.remove(packageId);
                    World world = store.getExternalData().getWorld();
                    world.execute(() -> {
                        sendPlayerMessage(ref, store, Message.raw("Removed package from cart."));
                        rebuild();
                    });
                } catch (Exception e) {
                    plugin.error("Failed to remove package " + packageId + " from cart", e);
                    String message = e.getMessage() == null || e.getMessage().isBlank()
                            ? e.getClass().getSimpleName()
                            : e.getMessage();
                    World world = store.getExternalData().getWorld();
                    world.execute(() -> sendPlayerMessage(ref, store, Message.raw("Failed to remove from cart: " + message)));
                }
            });
        }

        private void clearCartAsync(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull Store<EntityStore> store
        ) {
            TebexPlugin plugin = TebexPlugin.get();
            Map<Integer, Integer> snapshot = cartSession.snapshotQuantities();
            CompletableFuture.runAsync(() -> {
                try {
                    String basketIdent = cartSession.getBasketIdent();
                    Basket latestBasket = null;
                    if (!basketIdent.isBlank()) {
                        for (int packageId : snapshot.keySet()) {
                            latestBasket = plugin.getHeadlessApi().removeBasketPackage(basketIdent, packageId);
                        }
                        if (latestBasket != null) {
                            syncCartSessionFromBasket(latestBasket);
                        }
                    }
                    cartSession.replaceQuantities(Map.of());
                    cartSession.setTotals(0d);
                    World world = store.getExternalData().getWorld();
                    world.execute(() -> {
                        selectedPackageId = -1;
                        sendPlayerMessage(ref, store, Message.raw("Cart cleared."));
                        rebuild();
                    });
                } catch (Exception e) {
                    plugin.error("Failed to clear cart", e);
                    String message = e.getMessage() == null || e.getMessage().isBlank()
                            ? e.getClass().getSimpleName()
                            : e.getMessage();
                    World world = store.getExternalData().getWorld();
                    world.execute(() -> sendPlayerMessage(ref, store, Message.raw("Failed to clear cart: " + message)));
                }
            });
        }

        private void createCheckoutFromCartAsync(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull Store<EntityStore> store,
                @Nonnull Map<Integer, Integer> packageQuantities,
                boolean pollForCompletion
        ) {
            TebexPlugin plugin = TebexPlugin.get();
            CompletableFuture.runAsync(() -> {
                try {
                    Basket basket = plugin.getHeadlessApi().createBasket(playerRef.getUsername(), null, null, false);
                    if (basket.getIdent() == null || basket.getIdent().isBlank()) {
                        throw new IOException("Headless basket did not return a basket identifier.");
                    }

                    String basketIdent = basket.getIdent();
                    for (Map.Entry<Integer, Integer> entry : packageQuantities.entrySet()) {
                        int packageId = entry.getKey();
                        int quantity = Math.max(1, entry.getValue());
                        plugin.getHeadlessApi().addBasketPackage(basketIdent, packageId, quantity);
                    }

                    basket = plugin.getHeadlessApi().getBasket(basketIdent);
                    if (pollForCompletion) {
                        cartSession.setBasketIdent(basketIdent);
                    }
                    syncCartSessionFromBasket(basket);

                    String checkoutUrl = basket.getLinks() == null ? null : basket.getLinks().getCheckout();
                    if (checkoutUrl == null || checkoutUrl.isBlank()) {
                        throw new IOException("Headless basket did not return a checkout URL.");
                    }

                    World world = store.getExternalData().getWorld();
                    world.execute(() -> {
                        if (!ref.isValid()) {
                            return;
                        }
                        Player player = store.getComponent(ref, Player.getComponentType());
                        if (player != null) {
                            cartSession.setCheckoutPreview(checkoutUrl);
                            player.sendMessage(Message.raw("Checkout ready. Click here to open Tebex checkout.").link(checkoutUrl));
                            player.sendMessage(Message.raw(checkoutUrl).link(checkoutUrl));
                            close();
                        }
                    });

                    if (pollForCompletion) {
                        pollForPaymentCompletionAsync(ref, store, basketIdent);
                    }
                } catch (Exception e) {
                    plugin.error("Failed to create cart checkout URL", e);
                    String message = e.getMessage() == null || e.getMessage().isBlank()
                            ? e.getClass().getSimpleName()
                            : e.getMessage();
                    World world = store.getExternalData().getWorld();
                    world.execute(() -> sendPlayerMessage(ref, store, Message.raw("Failed to create checkout URL: " + message)));
                }
            });
        }

        private void pollForPaymentCompletionAsync(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull Store<EntityStore> store,
                @Nonnull String basketIdent
        ) {
            TebexPlugin plugin = TebexPlugin.get();
            CompletableFuture.runAsync(() -> {
                long expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10);
                while (System.currentTimeMillis() < expiresAt) {
                    try {
                        Basket basket = plugin.getHeadlessApi().getBasket(basketIdent);
                        syncCartSessionFromBasket(basket);

                        boolean complete = basket.isComplete();
                        if (!complete && basket.getLinks() != null && basket.getLinks().getPayment() != null && !basket.getLinks().getPayment().isBlank()) {
                            complete = true;
                        }

                        if (complete) {
                            plugin.setNextCheckQueue(System.currentTimeMillis());
                            World world = store.getExternalData().getWorld();
                            world.execute(() -> sendPlayerMessage(ref, store, Message.raw("Payment completed. Checking Tebex queue now...")));
                            return;
                        }
                    } catch (Exception e) {
                        plugin.debug("Basket payment poll failed for " + basketIdent + ": " + e.getMessage());
                    }

                    try {
                        Thread.sleep(2000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                World world = store.getExternalData().getWorld();
                world.execute(() -> sendPlayerMessage(ref, store, Message.raw("Payment status is still pending. Normal queue checks will continue.")));
            });
        }

        @Nonnull
        private String ensureCartBasketIdent(@Nonnull TebexPlugin plugin) throws IOException, InterruptedException {
            String basketIdent = cartSession.getBasketIdent();
            if (!basketIdent.isBlank()) {
                return basketIdent;
            }

            Basket basket = plugin.getHeadlessApi().createBasket(playerRef.getUsername(), null, null, false);
            if (basket.getIdent() == null || basket.getIdent().isBlank()) {
                throw new IOException("Headless basket did not return a basket identifier.");
            }

            cartSession.setBasketIdent(basket.getIdent());
            cartSession.setTotals(basket.getTotalPrice());
            return basket.getIdent();
        }

        private void updateCartSessionFromBasket(@Nullable Basket basket) {
            if (basket == null) {
                return;
            }
            if (basket.getIdent() != null && !basket.getIdent().isBlank()) {
                cartSession.setBasketIdent(basket.getIdent());
            }
            cartSession.setTotals(basket.getTotalPrice());
        }

        private void syncCartSessionFromBasket(@Nullable Basket basket) {
            if (basket == null) {
                return;
            }
            updateCartSessionFromBasket(basket);
            cartSession.replaceQuantities(snapshotBasketQuantities(basket));
        }

        @Nonnull
        private static Map<Integer, Integer> snapshotBasketQuantities(@Nonnull Basket basket) {
            Map<Integer, Integer> snapshot = new HashMap<>();
            if (basket.getPackages() == null) {
                return snapshot;
            }
            basket.getPackages().forEach(pack -> {
                if (pack == null || pack.getPackageId() <= 0 || pack.getQty() <= 0) {
                    return;
                }
                snapshot.put(pack.getPackageId(), pack.getQty());
            });
            return snapshot;
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

        @Nullable
        private CartEntry findCartEntry(int packageId) {
            if (packageId <= 0) {
                return null;
            }
            for (CartEntry entry : getCartEntries()) {
                if (entry.pack().getId() == packageId) {
                    return entry;
                }
            }
            return null;
        }

        @Nonnull
        private List<CartEntry> getCartEntries() {
            Map<Integer, Integer> snapshot = cartSession.snapshotQuantities();
            List<CartEntry> entries = new ArrayList<>();
            for (Map.Entry<Integer, Integer> item : snapshot.entrySet()) {
                int packageId = item.getKey();
                int quantity = item.getValue();
                if (quantity <= 0) {
                    continue;
                }

                Package pack = TebexPlugin.get().getPackagesCache().get(packageId);
                if (pack == null || pack.isDisabled()) {
                    continue;
                }

                CategoryPackage categoryPack = new CategoryPackage(
                        pack.getId(),
                        0,
                        pack.getName(),
                        pack.getPrice(),
                        pack.getDescription(),
                        pack.getImage(),
                        pack.getItemId(),
                        null
                );
                CategoryPackage cachePack = findPackageInCaches(packageId);
                if (cachePack != null) {
                    categoryPack = cachePack;
                }
                entries.add(new CartEntry(categoryPack, quantity, pack.getPrice() * quantity));
            }
            entries.sort(Comparator.comparing(value -> value.pack().getName(), String.CASE_INSENSITIVE_ORDER));
            return entries;
        }

        @Nullable
        private CategoryPackage findPackageInCaches(int packageId) {
            if (packageId <= 0) {
                return null;
            }
            for (Category category : getVisibleCategories()) {
                for (CategoryPackage pack : getCategoryPackages(category)) {
                    if (pack.getId() == packageId) {
                        return pack;
                    }
                }
            }
            return null;
        }

        @Nonnull
        private String buildStoreOverviewUsage() {
            int categoryCount = getVisibleCategories().size();
            return categoryCount + " categories available";
        }

        @Nonnull
        private String buildStoreOverviewDescription() {
            TebexPlugin plugin = TebexPlugin.get();
            StringBuilder builder = new StringBuilder();
            String storeUrl = plugin.getStoreUrl();
            if (!storeUrl.isBlank()) {
                builder.append("Webstore: ").append(storeUrl).append(". ");
            }
            builder.append("Browse categories to explore packages, pricing, and media before purchase. ");
            if (isCartEnabled()) {
                builder.append("Add items to your cart, then generate a checkout link in chat when you are ready. ");
            } else {
                builder.append("Cart is disabled for this store, so Buy Now creates a direct checkout link. ");
            }
            return builder.toString();
        }

        @Nonnull
        private String buildCartSubtitle(@Nonnull List<CartEntry> entries) {
            if (entries.isEmpty()) {
                return "No packages added";
            }

            int itemCount = 0;
            double subtotal = 0d;
            for (CartEntry entry : entries) {
                itemCount += entry.quantity();
                subtotal += entry.subtotal();
            }

            double total = cartSession.getLastKnownTotalPrice();
            if (total <= 0d) {
                total = subtotal;
            }
            return itemCount + " item" + (itemCount == 1 ? "" : "s") + " in cart - Total " + formatMoney(total);
        }

        @Nonnull
        private Message buildCartSidebarUsage(@Nonnull List<CartEntry> entries) {
            int itemCount = 0;
            double subtotal = 0d;
            for (CartEntry entry : entries) {
                itemCount += entry.quantity();
                subtotal += entry.subtotal();
            }

            double total = cartSession.getLastKnownTotalPrice();
            if (total <= 0d) {
                total = subtotal;
            }

            Message summary = Message.raw(itemCount + " item" + (itemCount == 1 ? "" : "s") + " total");
            summary.insert(Message.raw(" "));
            summary.insert(plainPriceMessage(total));
            return summary;
        }

        @Nonnull
        private String buildCartSidebarDescription(@Nonnull List<CartEntry> entries) {
            StringBuilder builder = new StringBuilder();
            builder.append("Items in your basket:");
            for (CartEntry entry : entries) {
                builder.append("\n- ")
                        .append(entry.quantity())
                        .append("x ")
                        .append(sanitizeUiText(entry.pack().getName()))
                        .append("  ")
                        .append(formatMoney(entry.subtotal()));
            }
            builder.append("\n\nUse the controls on each card to adjust quantity or remove an item. Create a checkout link when your basket looks right.");
            return builder.toString();
        }

        @Nonnull
        private static String buildPackageDetailDescription(@Nonnull CategoryPackage pack) {
            return resolvePackageDescription(pack);
        }

        @Nullable
        private static String resolvePackageItemId(@Nonnull CategoryPackage pack) {
            String itemRef = normalizeItemReference(pack.getItemId());
            if (itemRef != null) {
                return itemRef;
            }
            Package full = TebexPlugin.get().getPackagesCache().get(pack.getId());
            return normalizeItemReference(full == null ? null : full.getItemId());
        }

        @Nullable
        private static String normalizeItemReference(@Nullable String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.trim();
            if (normalized.isBlank()
                    || "false".equalsIgnoreCase(normalized)
                    || "null".equalsIgnoreCase(normalized)) {
                return null;
            }
            return normalized;
        }

        @Nullable
        private static String resolveCardThumbnailTexture(@Nonnull CategoryPackage pack) {
            String image = pack.getImage();
            if ((image == null || image.isBlank())) {
                Package full = TebexPlugin.get().getPackagesCache().get(pack.getId());
                image = full == null ? null : full.getImage();
            }

            String normalized = normalizeTexturePath(image);
            String canonical = resolveExistingTexturePath(normalized);
            if (isRenderableUiTexturePath(canonical)) {
                return toUiTexturePath(canonical);
            }

            String assumedPath = resolveAssumedPackageThumbnailTexture(pack.getId());
            if (assumedPath != null) {
                return assumedPath;
            }
            return null;
        }

        @Nullable
        private static String resolveCategoryThumbnailTexture(@Nonnull Category category) {
            String texturePath = TebexPlugin.get().getCategoryThumbnailTextureCache().get(category.getId());
            String normalized = normalizeTexturePath(texturePath);
            String canonical = resolveExistingTexturePath(normalized);
            if (isRenderableUiTexturePath(canonical)) {
                return toUiTexturePath(canonical);
            }
            return null;
        }

        @Nullable
        private static String resolveCategoryItemId(@Nonnull Category category) {
            return normalizeItemReference(category.getGuiItem());
        }

        @Nonnull
        private static String resolveCardTemplate(boolean singleColumn, @Nullable String texturePath, @Nullable String itemId) {
            String generatedTemplate = resolveGeneratedCardTemplate(singleColumn, texturePath);
            if (generatedTemplate != null) {
                return generatedTemplate;
            }
            if (normalizeItemReference(itemId) != null) {
                return singleColumn ? CARD_ICON_TEMPLATE_WIDE : CARD_ICON_TEMPLATE;
            }
            return singleColumn ? CARD_TEMPLATE_WIDE : CARD_TEMPLATE;
        }

        @Nonnull
        private static String resolveCartCardTemplate(boolean singleColumn, @Nullable String texturePath, @Nullable String itemId) {
            String generatedTemplate = resolveGeneratedCartCardTemplate(singleColumn, texturePath);
            if (generatedTemplate != null) {
                return generatedTemplate;
            }
            if (normalizeItemReference(itemId) != null) {
                return singleColumn ? CART_CARD_ICON_TEMPLATE_WIDE : CART_CARD_ICON_TEMPLATE;
            }
            return singleColumn ? CART_CARD_TEMPLATE_WIDE : CART_CARD_TEMPLATE;
        }

        private static boolean isIconTemplate(@Nullable String templatePath) {
            return CARD_ICON_TEMPLATE.equals(templatePath) || CARD_ICON_TEMPLATE_WIDE.equals(templatePath);
        }

        private static boolean isCartIconTemplate(@Nullable String templatePath) {
            return CART_CARD_ICON_TEMPLATE.equals(templatePath) || CART_CARD_ICON_TEMPLATE_WIDE.equals(templatePath);
        }

        @Nullable
        private static String resolveGeneratedCardTemplate(boolean singleColumn, @Nullable String texturePath) {
            if (!isRenderableUiTexturePath(texturePath)) {
                return null;
            }

            String templatePath = TebexPlugin.runtimeThumbnailCardTemplateUiPath(texturePath, singleColumn);
            if (hasUiPageAsset(templatePath)) {
                return templatePath;
            }
            return null;
        }

        @Nullable
        private static String resolveGeneratedCartCardTemplate(boolean singleColumn, @Nullable String texturePath) {
            if (!isRenderableUiTexturePath(texturePath)) {
                return null;
            }

            String templatePath = TebexPlugin.runtimeThumbnailCartCardTemplateUiPath(texturePath, singleColumn);
            if (hasUiPageAsset(templatePath)) {
                return templatePath;
            }
            return null;
        }

        @Nullable
        private static String resolveExistingTexturePath(@Nullable String normalized) {
            if (normalized == null || normalized.isBlank()) {
                return normalized;
            }
            if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
                return normalized;
            }

            LinkedHashSet<String> candidates = new LinkedHashSet<>();
            if (normalized.startsWith(PAGE_THUMBNAIL_PREFIX)) {
                String name = normalized.substring(PAGE_THUMBNAIL_PREFIX.length());
                candidates.add(UI_THUMBNAIL_PREFIX + name);
                candidates.add(COMMON_UI_THUMBNAIL_PREFIX + name);
            } else if (normalized.startsWith(COMMON_UI_THUMBNAIL_PREFIX)) {
                String name = normalized.substring(COMMON_UI_THUMBNAIL_PREFIX.length());
                candidates.add(UI_THUMBNAIL_PREFIX + name);
                candidates.add(normalized);
            } else if (normalized.startsWith(UI_THUMBNAIL_PREFIX)) {
                candidates.add(normalized);
                candidates.add(COMMON_UI_THUMBNAIL_PREFIX + normalized.substring(UI_THUMBNAIL_PREFIX.length()));
            } else {
                candidates.add(normalized);
                if (normalized.startsWith("Common/")) {
                    candidates.add(normalized.substring("Common/".length()));
                } else {
                    candidates.add("Common/" + normalized);
                }
            }

            for (String candidate : candidates) {
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }
                if (CommonAssetRegistry.hasCommonAsset(candidate)) {
                    return candidate;
                }
            }

            return null;
        }

        @Nullable
        private static String normalizeTexturePath(@Nullable String value) {
            if (value == null) {
                return null;
            }

            String normalized = value.trim().replace('\\', '/');
            if (normalized.isBlank()) {
                return null;
            }
            if ("false".equalsIgnoreCase(normalized) || "null".equalsIgnoreCase(normalized)) {
                return null;
            }

            if (normalized.startsWith(UI_THUMBNAIL_PREFIX)) {
                return normalized;
            }
            if (normalized.startsWith(PAGE_THUMBNAIL_PREFIX)) {
                return UI_THUMBNAIL_PREFIX + normalized.substring(PAGE_THUMBNAIL_PREFIX.length());
            }
            if (normalized.startsWith(COMMON_UI_THUMBNAIL_PREFIX)) {
                return UI_THUMBNAIL_PREFIX + normalized.substring(COMMON_UI_THUMBNAIL_PREFIX.length());
            }
            if (normalized.startsWith("Tebex/StoreThumbnails/")) {
                return UI_THUMBNAIL_PREFIX + normalized.substring("Tebex/StoreThumbnails/".length());
            }
            return normalized;
        }

        private static boolean isRenderableUiTexturePath(@Nullable String value) {
            return value != null
                    && !value.isBlank()
                    && !value.startsWith("http://")
                    && !value.startsWith("https://")
                    && (value.startsWith("Assets/")
                    || value.startsWith("Common/")
                    || value.startsWith("Tebex/")
                    || value.startsWith("UI/"));
        }

        @Nullable
        private static String toUiTexturePath(@Nullable String canonicalPath) {
            if (canonicalPath == null || canonicalPath.isBlank()) {
                return canonicalPath;
            }

            String normalized = canonicalPath.replace('\\', '/');
            if (normalized.startsWith(PAGE_THUMBNAIL_PREFIX)) {
                return normalized;
            }
            if (normalized.startsWith(COMMON_UI_THUMBNAIL_PREFIX)) {
                return PAGE_THUMBNAIL_PREFIX + normalized.substring(COMMON_UI_THUMBNAIL_PREFIX.length());
            }
            if (normalized.startsWith(UI_THUMBNAIL_PREFIX)) {
                return PAGE_THUMBNAIL_PREFIX + normalized.substring(UI_THUMBNAIL_PREFIX.length());
            }
            return normalized;
        }

        @Nullable
        private static String resolveAssumedPackageThumbnailTexture(int packageId) {
            String fileName = packageId + ".png";
            String canonical = resolveExistingTexturePath(UI_THUMBNAIL_PREFIX + fileName);
            if (isRenderableUiTexturePath(canonical)) {
                return toUiTexturePath(canonical);
            }
            return null;
        }

        private static boolean hasUiPageAsset(@Nullable String pagePath) {
            if (pagePath == null || pagePath.isBlank()) {
                return false;
            }

            String normalized = pagePath.trim().replace('\\', '/');
            LinkedHashSet<String> candidates = new LinkedHashSet<>();
            if (normalized.startsWith("Pages/")) {
                String relative = normalized.substring("Pages/".length());
                candidates.add("UI/Custom/Pages/" + relative);
                candidates.add("Common/UI/Custom/Pages/" + relative);
            } else if (normalized.startsWith("UI/Custom/Pages/")) {
                candidates.add(normalized);
                candidates.add("Common/" + normalized);
            } else if (normalized.startsWith("Common/UI/Custom/Pages/")) {
                candidates.add(normalized);
                candidates.add(normalized.substring("Common/".length()));
            } else {
                candidates.add("UI/Custom/Pages/" + normalized);
                candidates.add("Common/UI/Custom/Pages/" + normalized);
            }

            for (String candidate : candidates) {
                if (CommonAssetRegistry.hasCommonAsset(candidate)) {
                    return true;
                }
            }
            return false;
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
            if (description.length() <= 168) {
                return description;
            }
            return description.substring(0, 165).trim() + "...";
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
            String resolvedThumbnail = resolveCardThumbnailTexture(pack);
            plugin.debug(
                    "[BUY-UI] " + context
                            + " packId=" + pack.getId()
                            + " name='" + sanitizeUiText(pack.getName()) + "'"
                            + " category.description=" + debugField(pack.getDescription())
                            + " category.image=" + debugField(pack.getImage())
                            + " full.description=" + debugField(full == null ? null : full.getDescription())
                            + " full.description_html=" + debugField(full == null ? null : full.getDescriptionHtml())
                            + " full.image=" + debugField(full == null ? null : full.getImage())
                            + " full.item_id=" + debugField(full == null ? null : full.getItemId())
                            + " resolved.thumbnail=" + debugField(resolvedThumbnail)
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

        private static boolean isBasketAccountMismatchError(@Nullable Throwable throwable) {
            Throwable current = throwable;
            while (current != null) {
                String message = current.getMessage();
                if (message != null && !message.isBlank()) {
                    String lower = message.toLowerCase(Locale.ROOT);
                    if (lower.contains("does not belong to the same account as the basket")
                            || (lower.contains("invalid package") && lower.contains("basket"))) {
                        return true;
                    }
                }
                current = current.getCause();
            }
            return false;
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

        private static String cardItemIdSelector(int rowIndex, int colIndex) {
            return cardSelector(rowIndex, colIndex) + " #ItemIcon.ItemId";
        }

        private static String cardItemQuantitySelector(int rowIndex, int colIndex) {
            return cardSelector(rowIndex, colIndex) + " #ItemIcon.Quantity";
        }

        private static String cartQuantitySelector(int rowIndex, int colIndex) {
            return cardSelector(rowIndex, colIndex) + " #QuantityLabel.TextSpans";
        }

        private static String cartDecrementButtonSelector(int rowIndex, int colIndex) {
            return cardSelector(rowIndex, colIndex) + " #DecrementButton";
        }

        private static String cartIncrementButtonSelector(int rowIndex, int colIndex) {
            return cardSelector(rowIndex, colIndex) + " #IncrementButton";
        }

        private static String cartRemoveButtonSelector(int rowIndex, int colIndex) {
            return cardSelector(rowIndex, colIndex) + " #RemoveButton";
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

        @Nonnull
        private static Message buildPackagePriceMessage(@Nonnull CategoryPackage pack) {
            double current = Math.max(0d, pack.getPrice());
            double original = resolveOriginalPrice(pack);
            if (original > current) {
                return buildSalePriceMessage(current, original);
            }
            return plainPriceMessage(current);
        }

        @Nonnull
        private static Message buildCartEntryUsageMessage(@Nonnull CartEntry entry) {
            double currentSubtotal = Math.max(0d, entry.subtotal());
            double originalSubtotal = resolveOriginalPrice(entry.pack()) * entry.quantity();
            Message prefix = Message.raw("Qty " + entry.quantity());
            prefix.insert(Message.raw(" "));
            if (originalSubtotal > currentSubtotal) {
                return prefix.insert(buildSalePriceMessage(currentSubtotal, originalSubtotal));
            }
            return prefix.insert(plainPriceMessage(currentSubtotal));
        }

        private static double resolveOriginalPrice(@Nonnull CategoryPackage pack) {
            CategoryPackage.Sale sale = pack.getSale();
            if (sale == null || !sale.isActive() || sale.getDiscount() <= 0d) {
                return pack.getPrice();
            }
            return pack.getPrice() + sale.getDiscount();
        }

        @Nonnull
        private static String formatMoney(double amount) {
            TebexPlugin plugin = TebexPlugin.get();
            String symbol = "$";
            if (plugin.getTebexServerInfo() != null
                    && plugin.getTebexServerInfo().getAccount() != null
                    && plugin.getTebexServerInfo().getAccount().getCurrency() != null) {
                String configured = plugin.getTebexServerInfo().getAccount().getCurrency().getSymbol();
                if (configured != null && !configured.isBlank()) {
                    symbol = configured;
                }
            }
            return symbol + String.format(Locale.US, "%.2f", amount);
        }

        @Nonnull
        private static Message plainPriceMessage(double amount) {
            return Message.raw(formatMoney(amount)).color("#8fe36c").bold(true);
        }

        @Nonnull
        private static Message buildSalePriceMessage(double current, double original) {
            Message message = plainPriceMessage(current);
            message.insert(Message.raw("  "));
            message.insert(Message.raw(formatMoney(original)).color("#6d7c91"));
            return message;
        }

        private record CardEntry(
                String action,
                String value,
                String title,
                Message usage,
                String description,
                @Nullable String thumbnailTexturePath,
                @Nullable String itemId,
                boolean interactive
        ) {
            private static CardEntry button(
                    String action,
                    String value,
                    String title,
                    Message usage,
                    String description,
                    @Nullable String thumbnailTexturePath,
                    @Nullable String itemId
            ) {
                return new CardEntry(action, value, title, usage, description, thumbnailTexturePath, itemId, true);
            }

            private static CardEntry info(String title) {
                return new CardEntry("", "", title, Message.empty(), "", null, null, false);
            }
        }

        private record CartEntry(CategoryPackage pack, int quantity, double subtotal) {
        }

        private record ButtonEntry(String action, String value, String label) {
        }

        private record FooterConfig(@Nullable ButtonEntry left, @Nullable ButtonEntry right) {
        }
    }

    private static final class CartSession {
        private final ConcurrentHashMap<Integer, Integer> packageQuantities = new ConcurrentHashMap<>();
        private String basketIdent = "";
        private double lastKnownTotalPrice = 0d;
        private String checkoutUrl = "";

        @Nonnull
        synchronized Map<Integer, Integer> snapshotQuantities() {
            return new HashMap<>(packageQuantities);
        }

        synchronized void increment(int packageId, int amount) {
            if (packageId <= 0 || amount <= 0) {
                return;
            }
            int current = packageQuantities.getOrDefault(packageId, 0);
            packageQuantities.put(packageId, current + amount);
            clearCheckoutPreview();
        }

        synchronized void remove(int packageId) {
            packageQuantities.remove(packageId);
            clearCheckoutPreview();
        }

        synchronized void replaceQuantities(@Nonnull Map<Integer, Integer> quantities) {
            packageQuantities.clear();
            packageQuantities.putAll(quantities);
            clearCheckoutPreview();
        }

        synchronized boolean isEmpty() {
            return packageQuantities.isEmpty();
        }

        synchronized int getTotalItems() {
            int total = 0;
            for (int quantity : packageQuantities.values()) {
                if (quantity > 0) {
                    total += quantity;
                }
            }
            return total;
        }

        @Nonnull
        synchronized String getBasketIdent() {
            return basketIdent;
        }

        synchronized void setBasketIdent(@Nullable String basketIdent) {
            this.basketIdent = basketIdent == null ? "" : basketIdent;
        }

        synchronized void setTotals(double totalPrice) {
            this.lastKnownTotalPrice = Math.max(0d, totalPrice);
        }

        synchronized double getLastKnownTotalPrice() {
            return lastKnownTotalPrice;
        }

        synchronized void setCheckoutPreview(@Nullable String checkoutUrl) {
            this.checkoutUrl = checkoutUrl == null ? "" : checkoutUrl;
        }

        synchronized void clearCheckoutPreview() {
            this.checkoutUrl = "";
        }

        synchronized boolean hasCheckoutPreview() {
            return !checkoutUrl.isBlank();
        }

        @Nonnull
        synchronized String getCheckoutUrl() {
            return checkoutUrl;
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
