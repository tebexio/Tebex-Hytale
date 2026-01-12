package io.tebex.sdk.pluginapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PlayerLookupInfo {
    public final Player player;
    @SerializedName("ban_count")
    public final int banCount;
    @SerializedName("chargeback_rate")
    public final int chargebackRate;
    public final List<Payment> payments;
    @SerializedName("purchase_totals")
    public final Map<String, Double> purchaseTotals;

    @Data
    public static class Player {
        public final String id;
        public final String username;
        public final String meta;
        @SerializedName("plugin_username_id")
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
}
