package io.tebex.sdk.pluginapi.models;

import com.google.gson.annotations.SerializedName;
import io.tebex.hytale.plugin.TebexPlugin;
import io.tebex.sdk.pluginapi.IPluginAdapter;
import io.tebex.sdk.pluginapi.PluginApi;
import lombok.Data;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;

/**
 * A PluginEvent is an indicator of a runtime event that is reported to Tebex. This encapsulates telemetry information
 *  about warnings and errors that occur during runtime.
 */
@Data
public class PluginEvent {
    private transient IPluginAdapter plugin = TebexPlugin.get();

    @SerializedName(value = "game_id")
    private String gameId = "hytale";
    @SerializedName(value = "framework_id")
    private String frameworkId = "hytale";
    @SerializedName(value = "runtime_version")
    private String runtimeVersion = Runtime.version().toString();
    @SerializedName(value = "framework_version")
    private String frameworkVersion = "latest"; //TODO
    @SerializedName(value = "plugin_version")
    private String pluginVersion = plugin.getVersion();
    @SerializedName(value = "event_message")
    private String eventMessage;
    @SerializedName(value = "event_level")
    private EnumEventLevel eventLevel;
    @SerializedName(value = "server_id")
    private String serverId;
    @SerializedName(value = "server_ip")
    private String serverIp;
    @SerializedName(value = "store_url")
    private String storeUrl;
    @SerializedName(value = "metadata")
    private String metadata;
    @SerializedName(value = "trace")
    private String trace;

    public static PluginEvent logLine(@Nullable EnumEventLevel level, String message) {
        PluginEvent event = new PluginEvent();
        event.eventLevel = level;
        event.eventMessage = message;
        return event;
    }

    public PluginEvent onStore(@Nullable ServerInformation info) {
        if (info == null) {
            return this;
        }

        this.serverId = String.valueOf(info.getServer().getId());
        this.pluginVersion = plugin.getVersion();

        if (this.metadata == null) {
            this.metadata = "";
        }
        var meta = new HashMap<String, String>();
        meta.put("server_name", info.getServer().getName());
        meta.put("store_name", info.getAccount().getName());
        meta.put("store_id", String.valueOf(info.getAccount().getId()));
        metadata = PluginApi.GSON.toJson(meta);
        return this;
    }

    public PluginEvent withTrace(Throwable t) {
        StringWriter traceWriter = new StringWriter();
        t.printStackTrace(new PrintWriter(traceWriter)); // also write to our var for reporting
        this.trace = traceWriter.toString();
        this.eventMessage = t.getMessage() == null ? t.getClass().getName() : t.getMessage();
        return this;
    }
}