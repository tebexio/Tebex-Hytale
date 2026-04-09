package io.tebex.sdk.headlessapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class BasketPackage {
    @SerializedName(value = "package_id", alternate = {"id"})
    private int packageId;
    private int qty;
    @SerializedName("in_basket")
    private InBasket inBasket;
    private String type;

    public int getQty() {
        if (qty > 0) {
            return qty;
        }
        return inBasket == null ? 0 : inBasket.getQuantity();
    }

    @Data
    public static class InBasket {
        private int quantity;
    }
}
