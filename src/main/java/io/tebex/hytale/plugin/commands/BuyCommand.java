package io.tebex.hytale.plugin.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.PlayerCommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.DelegateItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.tebex.hytale.plugin.TebexPlugin;
import io.tebex.hytale.plugin.ui.BuyWindow;

import javax.annotation.Nonnull;

public class BuyCommand extends PlayerCommandBase {

    public BuyCommand(String name) {
        super(name, "Opens the buy menu.");
    }

    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        assert player != null;

        var categories = TebexPlugin.get().getCategoriesCache();
        var storeContainer = new SimpleItemContainer((short)categories.size());
        for (var category : categories) {
            //FIXME duplicate gui items are automatically stacked
            storeContainer.addItemStack(new ItemStack(category.getGuiItem(), 1), false, false, false);
        }

        Store<EntityStore> targetStore = ref.getStore();
        World targetWorld = ((EntityStore)targetStore.getExternalData()).getWorld();
        targetWorld.execute(() -> {
            CombinedItemContainer targetInventory = new CombinedItemContainer(
                    storeContainer
            );
            ItemContainer targetItemContainer = targetInventory;

            // make read-only, we will handle click events to open category menus
            DelegateItemContainer<CombinedItemContainer> delegateItemContainer = new DelegateItemContainer(targetInventory);
            delegateItemContainer.setGlobalFilter(FilterType.DENY_ALL);
            targetItemContainer = delegateItemContainer;

            player.getPageManager().setPageWithWindows(ref, store, Page.Inventory, true, new Window[]{new BuyWindow(targetItemContainer)});
        });
    }
}