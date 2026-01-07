package io.tebex.sdk.pluginapi.models.requests;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CheckoutRequest {
    private String username;
    private int packageId;
}
