package io.tebex.sdk.headlessapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class Basket {
    private String id;
    private String ident;
    private boolean complete;
    private String email;
    private String username;
    @SerializedName("base_price")
    private double basePrice;
    @SerializedName("sales_tax")
    private double salesTax;
    @SerializedName("total_price")
    private double totalPrice;
    private String currency;
    private List<BasketPackage> packages;
    private BasketLinks links;
}
