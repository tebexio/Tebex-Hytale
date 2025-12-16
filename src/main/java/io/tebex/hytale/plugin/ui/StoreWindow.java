package io.tebex.hytale.plugin.ui;

import com.google.gson.JsonObject;
import com.hypixel.hytale.protocol.WindowType;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;

public class StoreWindow extends Window {
    public StoreWindow(WindowType windowType) {
        super(windowType);
    }

    @Override
    public JsonObject getData() {
        return null;
    }

    @Override
    protected boolean onOpen0() {
        return false;
    }

    @Override
    protected void onClose0() {

    }
}
