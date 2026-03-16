package io.tebex.sdk.headlessapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AddBasketPackageRequest {
    @SerializedName("package_id")
    private String packageId;
    private int quantity;
}
