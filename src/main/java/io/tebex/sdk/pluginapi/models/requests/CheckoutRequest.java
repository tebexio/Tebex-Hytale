package io.tebex.sdk.pluginapi.models.requests;

import lombok.Data;

@Data
public class CheckoutRequest {
    private final String username;
    private final int packageId;
}
