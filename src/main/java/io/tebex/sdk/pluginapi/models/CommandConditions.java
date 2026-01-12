package io.tebex.sdk.pluginapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class CommandConditions {
    private final Integer delay;
    @SerializedName("required_slots")
    private final Integer requiredSlots;
}
