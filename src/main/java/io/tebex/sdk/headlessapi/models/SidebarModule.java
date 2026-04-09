package io.tebex.sdk.headlessapi.models;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class SidebarModule {
    private int id;
    private String type;
    @SerializedName("start_time")
    private String startTime;
    @SerializedName("end_time")
    private String endTime;
    private JsonObject data;
}
