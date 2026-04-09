package io.tebex.sdk.headlessapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class HeadlessPackage {
    private int id;
    private String name;
    private String description;
    private String image;
    private String type;
    private Category category;
    @SerializedName("base_price")
    private double basePrice;
    @SerializedName("sales_tax")
    private double salesTax;
    @SerializedName("total_price")
    private double totalPrice;
    private String currency;
    @SerializedName("prorate_price")
    private Double proratePrice;
    private double discount;
    @SerializedName("disable_quantity")
    private boolean disableQuantity;
    @SerializedName("disable_gifting")
    private boolean disableGifting;
    @SerializedName("expiration_date")
    private String expirationDate;
    private List<PackageMedia> media;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("updated_at")
    private String updatedAt;

    @Data
    public static class Category {
        private int id;
        private String name;
    }

    @Data
    public static class PackageMedia {
        private String type;
        private String name;
        private String url;
        private boolean featured;
    }
}
