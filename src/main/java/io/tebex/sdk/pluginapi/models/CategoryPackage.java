package io.tebex.sdk.pluginapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class CategoryPackage {
    private final int id;
    private final int order;
    private final String name;
    private final double price;
    private final String description;
    private final String image;
    @SerializedName(value = "gui_item", alternate = {"item_id"})
    private final String itemId;
    private final Sale sale;

    @Data
    public static class Sale {
        private final boolean active;
        private final double discount;
    }
}
