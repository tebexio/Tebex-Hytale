package io.tebex.sdk.headlessapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class BasketPackage {
    @SerializedName("package_id")
    private int packageId;
    private int qty;
    private String type;
}
