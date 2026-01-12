package io.tebex.sdk.http;

import io.tebex.sdk.common.Verb;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public final class JdkHttpProvider implements IHttpProvider {
    private final HttpClient client = HttpClient.newBuilder().build();
    private final String userAgent;
    private final Map<String, String> additionalHeaders = new HashMap<>();

    public JdkHttpProvider(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public Map<String, String> getCustomHeaders() {
        return additionalHeaders;
    }

    public String request(Verb method, URI url, @Nullable String jsonData) throws IOException, InterruptedException {
        HttpRequest.Builder req = HttpRequest.newBuilder(url);
        var pub = jsonData == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(jsonData);
        switch (method) {
            case GET -> req.GET();
            case POST -> req.POST(pub);
            case PUT -> req.PUT(pub);
            case DELETE -> req.method("DELETE", pub);
        }
        req.header("Content-Type", "application/json");
        req.header("Accept", "application/json");
        req.header("User-Agent", getUserAgent());
        for (var customHeader : getCustomHeaders().entrySet()) {
            req.header(customHeader.getKey(), customHeader.getValue());
        }

        // raise exception on non-OK response
        var resp = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Request failed with response code " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }
}
