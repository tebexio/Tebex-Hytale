package io.tebex.hytale.plugin.ui;

import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.tebex.hytale.plugin.TebexPlugin;

import org.bson.BsonDocument;

import javax.annotation.Nonnull;

public class BuyWindow extends ContainerWindow {
    @Nonnull
    private final JsonObject windowData = new JsonObject();

    public BuyWindow(ItemContainer container) {
        super(container);
        this.windowData.addProperty("id", "Tebex Store");
        this.windowData.addProperty("name", "Tebex Store");
    }

    @Nonnull
    public JsonObject getData() {
        return this.windowData;
    }

    public boolean onOpen0() {
        PlayerRef playerRef = this.getPlayerRef();
        Ref<EntityStore> ref = playerRef.getReference();
        Store<EntityStore> store = ref.getStore();
        World world = ((EntityStore)store.getExternalData()).getWorld();
        this.invalidate();
        TebexPlugin.get().debug("BuyWindow opened!");
        return true;
    }

    public void onClose0() {
        TebexPlugin.get().debug("BuyWindow closed!");
    }

    public void handleAction(@Nonnull Ref ref, @Nonnull Store store, @Nonnull String key, @Nonnull BsonDocument data) {
        //FIXME no actions are sent when clicking an item, I expected to be able to see what item was clicked
        TebexPlugin.get().debug("BuyWindow action: " + key);
        TebexPlugin.get().debug("BuyWindow data: " + data.toJson());
    }
}
