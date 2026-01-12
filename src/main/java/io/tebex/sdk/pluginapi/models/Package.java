package io.tebex.sdk.pluginapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.util.List;

@Data
public class Package {
    private final int id;
    private final String name;
    private final String image;
    private final double price;
    @SerializedName("expiry_length")
    private final int expiryLength;
    @SerializedName("expiry_period")
    private final String expiryPeriod;
    private final String type;
    private final Category category;
    @SerializedName("global_limit")
    private final int globalLimit;
    @SerializedName("global_limit_period")
    private final String globalLimitPeriod;
    @SerializedName("user_limit")
    private final int userLimit;
    @SerializedName("user_limit_period")
    private final String userLimitPeriod;
    private final List<Server> servers;
    @SerializedName("required_packages")
    private final List<Integer> requiredPackages;
    @SerializedName("require_any")
    private final boolean requireAny;
    @SerializedName("create_giftcard")
    private final boolean createGiftcard;
    @SerializedName("show_util")
    private final boolean showUtil;
    private final String itemId;
    private final boolean disabled;
    @SerializedName("disable_quantity")
    private final boolean disableQuantity;
    @SerializedName("custom_price")
    private final boolean customPrice;
    @SerializedName("choose_server")
    private final boolean chooseServer;
    @SerializedName("limit_expires")
    private final boolean limitExpires;
    @SerializedName("inherit_commands")
    private final boolean inheritCommands;
    @SerializedName("variable_giftcard")
    private final boolean variableGiftcard;

    @Getter @AllArgsConstructor
    public static class Category {
        private final int id;
        private final String name;
    }

    @Getter @AllArgsConstructor
    public static class Server {
        private final int id;
        private final String name;
    }
}
