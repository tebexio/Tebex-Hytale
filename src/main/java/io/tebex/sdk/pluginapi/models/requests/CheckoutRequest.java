package io.tebex.sdk.pluginapi.models.requests;

import lombok.Data;

@Data
public class CheckoutRequest {
    private String username;
    private int packageId;
}
