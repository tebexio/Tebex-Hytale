package io.tebex.sdk.pluginapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import javax.annotation.Nullable;

@Data
public class QueuedOnlineCommand implements ICommand {
    private final int id;
    private final String command;
    @Nullable private final Integer payment;
    @SerializedName("package")
    @Nullable private final Integer packageId;
    private final CommandConditions conditions;
}
