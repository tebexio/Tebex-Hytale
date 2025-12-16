package io.tebex.sdk.pluginapi.models.requests;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class DeleteCommandsRequest {
    @SerializedName("ids")
    private final int[] commandIds;
}
