package io.tebex.sdk.headlessapi;

import io.tebex.sdk.common.Verb;
import io.tebex.sdk.headlessapi.models.AddBasketPackageRequest;
import io.tebex.sdk.headlessapi.models.Basket;
import io.tebex.sdk.headlessapi.models.BasketResponse;
import io.tebex.sdk.headlessapi.models.CategoryResponse;
import io.tebex.sdk.headlessapi.models.CreateBasketRequest;
import io.tebex.sdk.headlessapi.models.HeadlessCategory;
import io.tebex.sdk.headlessapi.models.HeadlessPackage;
import io.tebex.sdk.headlessapi.models.PackageResponse;
import io.tebex.sdk.headlessapi.models.RemoveBasketPackageRequest;
import io.tebex.sdk.headlessapi.models.SidebarModule;
import io.tebex.sdk.headlessapi.models.SidebarModulesResponse;
import io.tebex.sdk.headlessapi.models.Webstore;
import io.tebex.sdk.headlessapi.models.WebstoreResponse;
import io.tebex.sdk.pluginapi.IPluginAdapter;
import io.tebex.sdk.pluginapi.PluginApi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal Headless API client used for store-facing Tebex data.
 */
public class HeadlessApi {
    private static final String HEADLESS_API_URL = "https://headless.tebex.io/api/";
    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong(0L);

    private final IPluginAdapter plugin;
    private final HttpClient client = HttpClient.newBuilder().build();
    private String publicToken = "";
    private String privateKey = "";

    public HeadlessApi(@Nonnull IPluginAdapter plugin) {
        this.plugin = plugin;
    }

    public void setCredentials(@Nullable String publicToken, @Nullable String privateKey) {
        this.publicToken = publicToken == null ? "" : publicToken.trim();
        this.privateKey = privateKey == null ? "" : privateKey.trim();
    }

    public boolean hasPublicToken() {
        return !publicToken.isBlank();
    }

    @Nullable
    public Webstore getWebstore() throws IOException, InterruptedException {
        WebstoreResponse response = PluginApi.GSON.fromJson(request(Verb.GET, "accounts/" + requireToken(), null), WebstoreResponse.class);
        return response == null ? null : response.getData();
    }

    @Nonnull
    public List<HeadlessPackage> getPackages() throws IOException, InterruptedException {
        PackageResponse response = PluginApi.GSON.fromJson(request(Verb.GET, "accounts/" + requireToken() + "/packages", null), PackageResponse.class);
        if (response == null || response.getData() == null) {
            return List.of();
        }
        return response.getData();
    }

    @Nonnull
    public List<HeadlessCategory> getCategoriesIncludingPackages() throws IOException, InterruptedException {
        CategoryResponse response = PluginApi.GSON.fromJson(request(Verb.GET, "accounts/" + requireToken() + "/categories?includePackages=1", null), CategoryResponse.class);
        if (response == null || response.getData() == null) {
            return List.of();
        }
        return response.getData();
    }

    @Nonnull
    public List<SidebarModule> getSidebarModules() throws IOException, InterruptedException {
        SidebarModulesResponse response = PluginApi.GSON.fromJson(request(Verb.GET, "accounts/" + requireToken() + "/sidebar", null), SidebarModulesResponse.class);
        if (response == null || response.getData() == null) {
            return List.of();
        }
        return response.getData();
    }

    @Nonnull
    public Basket createBasket(@Nullable String username, @Nullable String completeUrl, @Nullable String cancelUrl, boolean completeAutoRedirect)
            throws IOException, InterruptedException {
        CreateBasketRequest payload = new CreateBasketRequest();
        payload.setUsername(username == null ? null : username.trim());
        payload.setCompleteUrl(completeUrl == null ? null : completeUrl.trim());
        payload.setCancelUrl(cancelUrl == null ? null : cancelUrl.trim());
        payload.setCompleteAutoRedirect(completeAutoRedirect);

        BasketResponse response = PluginApi.GSON.fromJson(
                request(Verb.POST, "accounts/" + requireToken() + "/baskets", payload),
                BasketResponse.class
        );
        if (response == null || response.getData() == null) {
            return new Basket();
        }
        return response.getData();
    }

    @Nonnull
    public Basket getBasket(@Nonnull String basketIdent) throws IOException, InterruptedException {
        String ident = basketIdent == null ? "" : basketIdent.trim();
        if (ident.isBlank()) {
            throw new IllegalArgumentException("basketIdent is required.");
        }
        BasketResponse response = PluginApi.GSON.fromJson(
                request(Verb.GET, "accounts/" + requireToken() + "/baskets/" + ident, null),
                BasketResponse.class
        );
        if (response == null || response.getData() == null) {
            return new Basket();
        }
        return response.getData();
    }

    @Nonnull
    public Basket addBasketPackage(@Nonnull String basketIdent, int packageId, int quantity) throws IOException, InterruptedException {
        String ident = basketIdent == null ? "" : basketIdent.trim();
        if (ident.isBlank()) {
            throw new IllegalArgumentException("basketIdent is required.");
        }
        if (packageId <= 0) {
            throw new IllegalArgumentException("packageId must be > 0.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0.");
        }

        AddBasketPackageRequest payload = new AddBasketPackageRequest(Integer.toString(packageId), quantity);
        BasketResponse response = PluginApi.GSON.fromJson(
                request(Verb.POST, "baskets/" + ident + "/packages", payload),
                BasketResponse.class
        );
        return response == null || response.getData() == null ? new Basket() : response.getData();
    }

    @Nonnull
    public Basket removeBasketPackage(@Nonnull String basketIdent, int packageId) throws IOException, InterruptedException {
        String ident = basketIdent == null ? "" : basketIdent.trim();
        if (ident.isBlank()) {
            throw new IllegalArgumentException("basketIdent is required.");
        }
        if (packageId <= 0) {
            throw new IllegalArgumentException("packageId must be > 0.");
        }

        RemoveBasketPackageRequest payload = new RemoveBasketPackageRequest(Integer.toString(packageId));
        BasketResponse response = PluginApi.GSON.fromJson(
                request(Verb.POST, "baskets/" + ident + "/packages/remove", payload),
                BasketResponse.class
        );
        return response == null || response.getData() == null ? new Basket() : response.getData();
    }

    @Nonnull
    private String request(@Nonnull Verb verb, @Nonnull String endpoint, @Nullable Object payload) throws IOException, InterruptedException {
        URI url = URI.create(HEADLESS_API_URL + endpoint);
        long requestId = REQUEST_SEQUENCE.incrementAndGet();
        String maskedUrl = maskSensitiveUrl(url.toString());
        String payloadJson = payload == null ? "" : PluginApi.GSON.toJson(payload);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(url)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", plugin.getHttpProvider().getUserAgent());

        if (!publicToken.isBlank() && !privateKey.isBlank()) {
            String auth = Base64.getEncoder().encodeToString((publicToken + ":" + privateKey).getBytes(StandardCharsets.UTF_8));
            requestBuilder.header("Authorization", "Basic " + auth);
        }

        switch (verb) {
            case GET -> requestBuilder.GET();
            case POST -> requestBuilder.POST(HttpRequest.BodyPublishers.ofString(payload == null ? "{}" : PluginApi.GSON.toJson(payload)));
            case PUT -> requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(payload == null ? "{}" : PluginApi.GSON.toJson(payload)));
            case DELETE -> {
                if (payload == null) {
                    requestBuilder.DELETE();
                } else {
                    requestBuilder.method("DELETE", HttpRequest.BodyPublishers.ofString(PluginApi.GSON.toJson(payload)));
                }
            }
        }

        if (payload == null) {
            plugin.info("[Headless API #" + requestId + "] -> " + verb + " " + maskedUrl);
        } else {
            plugin.info("[Headless API #" + requestId + "] -> " + verb + " " + maskedUrl + " payload=" + payloadJson);
        }
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        plugin.info("[Headless API #" + requestId + "] <- " + response.statusCode() + " " + maskedUrl + " response=" + response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            plugin.warn(
                    "Headless API request failed with response code " + response.statusCode() + " at " + maskedUrl,
                    "See [Headless API #" + requestId + "] logs above for full request/response payload."
            );
            throw new IOException("Headless API request failed with response code " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    @Nonnull
    private String requireToken() {
        if (publicToken.isBlank()) {
            throw new IllegalStateException("Headless public token is missing.");
        }
        return publicToken;
    }

    @Nonnull
    private static String maskSensitiveUrl(@Nonnull String url) {
        return url.replaceAll("(?<=/accounts/)[^/?]+", "<public-token>");
    }
}
