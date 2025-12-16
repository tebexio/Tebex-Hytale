package io.tebex.sdk.pluginapi.models;

import lombok.Data;

import java.util.Date;

@Data
public class CheckoutUrl {
    private final String url;
    private final Date expires;
}
