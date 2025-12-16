package io.tebex.sdk.http;

import io.tebex.sdk.common.Verb;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

public interface IHttpProvider {
    static URI formatEndpoint(String baseUrl, String endpoint) {
        return URI.create(baseUrl.concat(endpoint.substring(endpoint.startsWith("/") ? 1 : 0)));
    }

    default String request(Verb method, URI url) throws IOException, InterruptedException {
        return request(method, url, null);
    }

    Map<String, String> getCustomHeaders();

    default void setCustomHeader(String key, String value) {
        getCustomHeaders().put(key, value);
    }

    String request(Verb method, URI url, @Nullable String jsonData) throws IOException, InterruptedException;

    String getUserAgent();
}
