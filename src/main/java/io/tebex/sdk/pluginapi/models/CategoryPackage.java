package io.tebex.sdk.pluginapi.models;

import lombok.Data;

@Data
public class CategoryPackage {
    private final int id;
    private final int order;
    private final String name;
    private final double price;
    private final String image;
    private final String itemId;
    private final Sale sale;

    @Data
    public static class Sale {
        private final boolean active;
        private final double discount;
    }
}
