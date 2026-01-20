package io.tebex.sdk.pluginapi;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import io.tebex.sdk.common.Verb;
import io.tebex.sdk.common.gson.BooleanTypeAdapter;
import io.tebex.sdk.http.IHttpProvider;
import io.tebex.sdk.pluginapi.models.*;
import io.tebex.sdk.pluginapi.models.Package;
import io.tebex.sdk.pluginapi.models.requests.CheckoutRequest;
import io.tebex.sdk.pluginapi.models.requests.DeleteCommandsRequest;
import io.tebex.sdk.pluginapi.models.responses.CommandQueueResponse;
import io.tebex.sdk.pluginapi.models.responses.OfflineCommandsResponse;
import io.tebex.sdk.pluginapi.models.responses.OnlineCommandsResponse;
import lombok.Getter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Methods for interacting with the Tebex Plugin API */
public class PluginApi {
    /** Base url for plugin api requests */
    private static final String PLUGIN_API_URL = "https://plugin.tebex.io/";
    private static final String PLUGIN_LOGS_URL = "https://plugin-logs.tebex.io/";

    /** GSON formatter to generate JSON */
    public static final Gson GSON = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(Boolean.class, new BooleanTypeAdapter())
            .registerTypeAdapter(boolean.class, new BooleanTypeAdapter())
            .create();

    /** Interface for telling a plugin what to do */
    private final IPluginAdapter plugin;

    public PluginApi(IPluginAdapter plugin) {
        this.plugin = plugin;
    }

    public void setSecretKey(@Nonnull String secretKey) {
        this.plugin.getHttpProvider().setCustomHeader("X-Tebex-Secret", secretKey);
    }

    /** @return GET /information, the {@link ServerInformation} about the linked store. */
    public ServerInformation getServerInformation() throws IOException, InterruptedException {
        return GSON.fromJson(httpApi(Verb.GET, "information"), ServerInformation.class);
    }

    public List<Category> getCategories() throws IOException, InterruptedException {
        return GSON.fromJson(httpApi(Verb.GET, "listing"), Responses.CategoriesResponse.class).getCategories();
    }

    public List<Package> getPackages() throws IOException, InterruptedException {
        return GSON.fromJson(httpApi(Verb.GET, "packages"), new TypeToken<List<Package>>() {}.getType());
    }

    public List<CommunityGoal> getCommunityGoals() throws IOException, InterruptedException {
        return GSON.fromJson(httpApi(Verb.GET, "community_goals"), new TypeToken<List<CommunityGoal>>() {}.getType());
    }

    public CommandQueueResponse getCommandQueue() throws IOException, InterruptedException {
        return GSON.fromJson(httpApi(Verb.GET, "queue"), CommandQueueResponse.class);
    }

    public OnlineCommandsResponse getOnlineCommands(int playerId) throws IOException, InterruptedException {
        return GSON.fromJson(httpApi(Verb.GET, "queue/online-commands/" + playerId), OnlineCommandsResponse.class);
    }

    public OfflineCommandsResponse getOfflineCommands() throws IOException, InterruptedException {
        return GSON.fromJson(httpApi(Verb.GET, "queue/offline-commands"), OfflineCommandsResponse.class);
    }

    @Nonnull
    public CheckoutUrl checkout(CheckoutRequest req) throws IOException, InterruptedException {
        return GSON.fromJson(httpApi(Verb.POST, "checkout", req), CheckoutUrl.class);
    }

    public void deleteCompletedCommands(ConcurrentHashMap<Integer, QueuedCommand> completedCommands) throws IOException, InterruptedException {
        // build payload, {"ids": [1,2,3,4,...]} array of int command ids to delete as a snapshot of the current state of the map
        Integer[] idsSnapshot = completedCommands.keySet().toArray(new Integer[0]);
        if (idsSnapshot.length == 0) return;

        int[] completedIds = new int[idsSnapshot.length];
        for (int i = 0; i < idsSnapshot.length; i++) {
            completedIds[i] = idsSnapshot[i];
        }

        var payload = new DeleteCommandsRequest(completedIds);
        httpApi(Verb.DELETE, "queue", payload); // response would be blank, 204 no content

        plugin.debug("Deleted " + completedIds.length + " completed commands.");

        // remove only what we actually sent
        for (Integer id : idsSnapshot) {
            completedCommands.remove(id);
        }
    }

    /**
     * Submit plugin events (logs) to the plugin logs system
     * @param events List of plugin events to submit
     */
    public void submitPluginEvents(List<PluginEvent> events) throws IOException, InterruptedException {
        if (events.isEmpty()) {
            return;
        }
        httpLogs(Verb.POST, "events", events);
        plugin.debug("Submitted " + events.size() + " plugin events.");
    }

    /**
     * Submit server events (join/leave) to the analytics system
     * @param events List of server events to submit
     */
    public void submitServerEvents(List<ServerEvent> events) throws IOException, InterruptedException {
        if (events.isEmpty()) {
            return;
        }
        httpApi(Verb.POST, "events", events);
        plugin.debug("Submitted " + events.size() + " server events.");
    }

    /**
     * Helper for plugin API requests. See {@link #httpApi(Verb, String, Object)}
     */
    private String httpApi(Verb verb, String endpoint) throws IOException, InterruptedException {
        return httpApi(verb, endpoint, null);
    }

    /**
     * Helper to send an http request to the plugin API.
     *
     * @param verb     GET, POST, PUT, DELETE
     * @param endpoint Endpoint to reach. Leading slashes will be removed
     * @param data     Optional data that will be serialized to JSON
     * @return The response as a JSON string.
     *
     * @throws IOException          If an I/O error occurs during the request.
     * @throws InterruptedException If the request is interrupted or canceled.
     */
    private String httpApi(Verb verb, String endpoint, @Nullable Object data) throws IOException, InterruptedException {
        return request(verb, PLUGIN_API_URL, endpoint, data);
    }

    private void httpLogs(Verb verb, String endpoint, @Nullable Object data) throws IOException, InterruptedException {
        request(verb, PLUGIN_LOGS_URL, endpoint, data);
    }

    private String request(Verb verb, String base, String endpoint, @Nullable Object data) throws IOException, InterruptedException {
        var url = IHttpProvider.formatEndpoint(base, endpoint);
        var resp = "";
        if (data != null) {
            plugin.debug("-> " + verb + " " + url + " " + GSON.toJson(data));
            resp = plugin.getHttpProvider().request(verb, url, GSON.toJson(data));
        } else {
            plugin.debug("-> " + verb + " " + url);
            resp = plugin.getHttpProvider().request(verb, url, null);
        }

        plugin.debug("<- " + resp);
        return resp;
    }

    private static class Responses {
        public static class CategoriesResponse {
            @SerializedName("categories")
            @Getter public List<Category> categories;
        }
    }
}