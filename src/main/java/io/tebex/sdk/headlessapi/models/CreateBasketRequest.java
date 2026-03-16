package io.tebex.sdk.headlessapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class CreateBasketRequest {
    @SerializedName("complete_url")
    private String completeUrl;
    @SerializedName("cancel_url")
    private String cancelUrl;
    @SerializedName("complete_auto_redirect")
    private boolean completeAutoRedirect;
    private String username;
    @SerializedName("username_id")
    private Long usernameId;
    private Object custom;
}
