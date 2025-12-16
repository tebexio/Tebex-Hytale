package io.tebex.sdk.pluginapi.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter @AllArgsConstructor
public class Package {
    private final int id;
    private final String name;
    private final String image;
    private final double price;
    private final int expiryLength;
    private final String expiryPeriod;
    private final String type;
    private final Category category;
    private final int globalLimit;
    private final String globalLimitPeriod;
    private final int userLimit;
    private final String userLimitPeriod;
    private final List<Server> servers;
    private final List<Integer> requiredPackages;
    private final boolean requireAny;
    private final boolean createGiftcard;
    private final boolean showUtil;
    private final String itemId;
    private final boolean disabled;
    private final boolean disableQuantity;
    private final boolean customPrice;
    private final boolean chooseServer;
    private final boolean limitExpires;
    private final boolean inheritCommands;
    private final boolean variableGiftcard;

    @Getter @AllArgsConstructor
    public static class Category {
        private final int id;
        private final String name;

        @Override
        public String toString() {
            return "Category{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    '}';
        }
    }

    @Getter @AllArgsConstructor
    public static class Server {
        private final int id;
        private final String name;

        @Override
        public String toString() {
            return "Server{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    '}';
        }
    }

    public static Package fromJsonObject(JsonObject jsonObject) {
        JsonObject categoryJson = jsonObject.get("category").getAsJsonObject();
        Category category = new Category(
                categoryJson.get("id").getAsInt(),
                categoryJson.get("name").getAsString()
        );

        JsonArray serversJsonArray = jsonObject.get("servers").getAsJsonArray();
        List<Server> servers = new ArrayList<>();
        for(JsonElement serverElement : serversJsonArray) {
            JsonObject serverJson = serverElement.getAsJsonObject();
            Server server = new Server(
                    serverJson.get("id").getAsInt(),
                    serverJson.get("name").getAsString()
            );
            servers.add(server);
        }

        return new Package(
                jsonObject.get("id").getAsInt(),
                jsonObject.get("name").getAsString(),
                jsonObject.get("image").getAsString(),
                jsonObject.get("price").getAsDouble(),
                jsonObject.get("expiry_length").getAsInt(),
                jsonObject.get("expiry_period").getAsString(),
                jsonObject.get("type").getAsString(),
                category,
                jsonObject.get("global_limit").getAsInt(),
                jsonObject.get("global_limit_period").getAsString(),
                jsonObject.get("user_limit").getAsInt(),
                jsonObject.get("user_limit_period").getAsString(),
                servers,
                jsonObject.getAsJsonArray("required_packages").asList().stream().map(JsonElement::getAsInt).collect(Collectors.toList()),
                jsonObject.get("require_any").getAsBoolean(),
                jsonObject.get("create_giftcard").getAsBoolean(),
                jsonObject.get("show_until").getAsBoolean(),
                !jsonObject.get("gui_item").isJsonNull() && !jsonObject.get("gui_item").getAsString().isEmpty() ? jsonObject.get("gui_item").getAsString() : null,
                jsonObject.get("disabled").getAsBoolean(),
                jsonObject.get("disable_quantity").getAsBoolean(),
                jsonObject.get("custom_price").getAsBoolean(),
                jsonObject.get("choose_server").getAsBoolean(),
                jsonObject.get("limit_expires").getAsBoolean(),
                jsonObject.get("inherit_commands").getAsBoolean(),
                jsonObject.get("variable_giftcard").getAsBoolean()
        );
    }

    @Override
    public String toString() {
        return "Package{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", image='" + image + '\'' +
                ", price=" + price +
                ", expiryLength=" + expiryLength +
                ", expiryPeriod='" + expiryPeriod + '\'' +
                ", type='" + type + '\'' +
                ", category=" + category +
                ", globalLimit=" + globalLimit +
                ", globalLimitPeriod='" + globalLimitPeriod + '\'' +
                ", userLimit=" + userLimit +
                ", userLimitPeriod='" + userLimitPeriod + '\'' +
                ", servers=" + servers +
                ", requiredPackages=" + requiredPackages +
                ", requireAny=" + requireAny +
                ", createGiftcard=" + createGiftcard +
                ", showUtil=" + showUtil +
                ", itemId=" + itemId +
                ", disabled=" + disabled +
                ", disableQuantity=" + disableQuantity +
                ", customPrice=" + customPrice +
                ", chooseServer=" + chooseServer +
                ", limitExpires=" + limitExpires +
                ", inheritCommands=" + inheritCommands +
                ", variableGiftcard=" + variableGiftcard +
                '}';
    }
}
