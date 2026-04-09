package io.tebex.sdk.headlessapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class HeadlessCategory {
    private int id;
    private String name;
    private String slug;
    private String description;
    private int order;
    private String displayType;
    @SerializedName("image_url")
    private String imageUrl;
    private List<HeadlessPackage> packages;
}
