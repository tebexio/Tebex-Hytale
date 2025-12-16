package io.tebex.sdk.pluginapi.models;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import io.tebex.sdk.pluginapi.IPluginAdapter;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

/**
 * A PluginEvent is an indicator of a runtime event that is reported to Tebex. This encapsulates telemetry information
 *  about warnings and errors that occur during runtime.
 */
public class PluginEvent {
    @SerializedName(value = "game_id")
    private String gameId;
    @SerializedName(value = "framework_id")
    private String frameworkId;
    @SerializedName(value = "runtime_version")
    private String runtimeVersion;
    @SerializedName(value = "framework_version")
    private String frameworkVersion;
    @SerializedName(value = "plugin_version")
    private String pluginVersion;
    @SerializedName(value = "store_id")
    private String storeId;
    @SerializedName(value = "store_name")
    private String storeName;
    @SerializedName(value = "server_id")
    private String serverId;
    @SerializedName(value = "event_message")
    private String eventMessage;
    @SerializedName(value = "event_level")
    private EnumEventLevel eventLevel;
    @SerializedName(value = "metadata")
    private Map<String, String> metadata;
    @SerializedName(value = "trace")
    private String trace;

    private transient IPluginAdapter plugin;
    public PluginEvent(IPluginAdapter plugin, EnumEventLevel level, String message) {
        this.plugin = plugin;
        //FIXME
//        PlatformTelemetry tel = platform.getTelemetry();
//
//        this.gameId = "Minecraft";                                  // always Minecraft
//        this.frameworkId = tel.getServerSoftware();                 // name of the platform software, Bukkit, Spigot, etc.
//        this.runtimeVersion = "Java " + tel.getJavaVersion();       // version of Java
//        this.frameworkVersion = tel.getServerVersion();             // version of Bukkit, Spigot, etc.
//        this.pluginVersion = platform.getPluginVersion();
//        this.eventLevel = level;
//        this.eventMessage = message;
//        this.trace = "";
//
//        if (platform.isSetup()) {
//            this.serverId = String.valueOf(platform.getStoreServer().getId());
//            this.storeId = String.valueOf(platform.getStore().getId());
//        }
    }

    public PluginEvent onServer(@Nullable ServerInformation server) {
        if (server == null) {
            return this;
        }

        this.serverId = String.valueOf(server.getServer().getId());
        this.storeId = String.valueOf(server.getStore().getId());
        this.storeName = server.getStore().getName();
        this.pluginVersion = plugin.getVersion();
        return this;
    }

    public PluginEvent withTrace(Throwable t) {
        StringWriter traceWriter = new StringWriter();
        t.printStackTrace(); // show trace in the console whenever one is provided
        t.printStackTrace(new PrintWriter(traceWriter)); // also write to our var for reporting
        this.trace = traceWriter.toString();
        this.eventMessage = t.getMessage();
        return this;
    }

    public String toJsonString() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}