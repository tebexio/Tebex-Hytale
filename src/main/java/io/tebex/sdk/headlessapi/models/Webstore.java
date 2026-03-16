package io.tebex.sdk.headlessapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Webstore {
    private int id;
    private String description;
    private String name;
    @SerializedName("webstore_url")
    private String webstoreUrl;
    private String currency;
    private String lang;
    private String logo;
    @SerializedName("platform_type")
    private String platformType;
    @SerializedName("platform_type_id")
    private String platformTypeId;
    @SerializedName("created_at")
    private String createdAt;
}
