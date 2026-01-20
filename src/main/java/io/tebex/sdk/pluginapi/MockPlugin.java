package io.tebex.sdk.pluginapi;

import io.tebex.sdk.http.IHttpProvider;
import io.tebex.sdk.http.JdkHttpProvider;
import io.tebex.sdk.pluginapi.models.QueuedCommand;
import io.tebex.sdk.pluginapi.models.QueuedPlayer;

public class MockPlugin implements IPluginAdapter {
    private static final JdkHttpProvider http = new JdkHttpProvider("Tebex-Plugin-Test");

    @Override
    public IHttpProvider getHttpProvider() {
        return http;
    }

    @Override
    public boolean playerHasInventorySlotsAvailable(QueuedPlayer player, int slots) {
        return true;
    }

    @Override
    public boolean executeCommand(QueuedCommand command) {
        return true;
    }

    @Override
    public boolean isPlayerOnline(String username) {
        return true;
    }

    @Override
    public boolean isDebugModeEnabled() {
        return false;
    }

    @Override
    public String getVersion() {
        return "";
    }

    @Override
    public void debug(String message) {
        System.out.println("[DEBUG] " + message);
    }

    @Override
    public void info(String message) {
        System.out.println("[INFO] " + message);
    }

    @Override
    public void warn(String message, String solution) {
        System.out.println("[WARN] " + message);
        System.out.println("-      " + solution);
    }

    @Override
    public void error(String message, Throwable throwable) {
        System.out.println("[ERROR] " + message);
        if (throwable != null) {
            System.out.println(throwable.toString());
        }
    }
}
