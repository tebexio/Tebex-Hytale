package io.tebex.sdk.pluginapi;

import io.tebex.sdk.http.IHttpProvider;
import io.tebex.sdk.pluginapi.models.QueuedCommand;
import io.tebex.sdk.pluginapi.models.QueuedPlayer;

/** A PluginAdapter is an interface for the functions a plugin is expected to provide. This is typically implemented by
 * the game "plugin" which is loaded into the game's classpath or API, allowing access to the game packages. */
public interface IPluginAdapter {
    IHttpProvider getHttpProvider();

    boolean playerHasInventorySlotsAvailable(QueuedPlayer player, int slots);

    boolean executeCommand(QueuedCommand command);

    boolean isPlayerOnline(String username);

    boolean isDebugModeEnabled();

    String getVersion();

    void debug(String message);
    void info(String message);
    void warn(String message, String solution);
    void error(String message, Throwable throwable);
}
