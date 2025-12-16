package io.tebex.sdk.pluginapi.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class PlayerLookupInfo {
    public final Player player;
    public final int banCount;
    public final int chargebackRate;
    public final List<Payment> payments;
    public final Map<String, Double> purchaseTotals;

    @Data
    public static class Player {
        public final String id;
        public final String username;
        public final String meta;
        public final int pluginUsernameId;
    }


    @Data
    public static class Payment {
        public final String txnId;
        public final long time;
        public final double price;
        public final String currency;
        public final int status;

    }

    public static PlayerLookupInfo fromJsonObject(JsonObject jsonObject) {
        JsonObject playerJson = jsonObject.get("player").getAsJsonObject();
        Player player = new Player(playerJson.get("id").getAsString(), playerJson.get("username").getAsString(),
                playerJson.get("meta").getAsString(), playerJson.get("plugin_username_id").getAsInt());
        int banCount = jsonObject.get("banCount").getAsInt();
        int chargebackRate = jsonObject.get("chargebackRate").getAsInt();

        JsonArray paymentsJsonArray = jsonObject.get("payments").getAsJsonArray();
        List<Payment> payments = new ArrayList<>();
        for (JsonElement paymentElement : paymentsJsonArray) {
            JsonObject paymentJson = paymentElement.getAsJsonObject();
            Payment payment = new Payment(paymentJson.get("txn_id").getAsString(), paymentJson.get("time").getAsLong(),
                    paymentJson.get("price").getAsDouble(), paymentJson.get("currency").getAsString(), paymentJson.get("status").getAsInt());
            payments.add(payment);
        }

        Map<String, Double> purchaseTotals = new HashMap<>();
        JsonElement purchaseTotalsJson = jsonObject.get("purchaseTotals");
        if (purchaseTotalsJson.isJsonObject()) { // empty
            JsonObject purchaseTotalsObj = purchaseTotalsJson.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : purchaseTotalsObj.entrySet()) {
                purchaseTotals.put(entry.getKey(), entry.getValue().getAsDouble());
            }
        }

        return new PlayerLookupInfo(player, banCount, chargebackRate, payments, purchaseTotals);
    }
}
