package io.tebex.sdk.pluginapi.models;

import lombok.Data;

import java.util.Date;

@Data
public class CheckoutUrl {
    private final String url;
    private final Date expires;
    // The Plugin API /checkout endpoint returns the created basket's ident.
    // Baskets cannot be created through the Headless API for this game, so this
    // ident is what subsequent Headless calls (get/add/remove) operate on.
    private final String ident;
}
