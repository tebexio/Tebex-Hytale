package io.tebex.sdk.pluginapi.models;

import com.google.gson.JsonObject;
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

    public boolean hasSale() {
        return sale != null && sale.isActive();
    }

    @Data
    public static class Sale {
        private final boolean active;
        private final double discount;
    }

    public static CategoryPackage fromJsonObject(JsonObject jsonObject) {
        JsonObject sale = jsonObject.getAsJsonObject("sale");

        return new CategoryPackage(
                jsonObject.get("id").getAsInt(),
                jsonObject.get("order").getAsInt(),
                jsonObject.get("name").getAsString(),
                jsonObject.get("price").getAsDouble(),
                jsonObject.get("image").getAsString(),
                jsonObject.get("gui_item").getAsString(),
                new Sale(sale.get("active").getAsBoolean(), sale.get("discount").getAsDouble())
        );
    }
}
