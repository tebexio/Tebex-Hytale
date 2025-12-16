package io.tebex.sdk.pluginapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class ServerInformation {
    @SerializedName("account")
    private final Store store;
    @SerializedName("server")
    private final Server server;


    @Data
    public static class Store {
        private final int id;
        private final String domain;
        private final String name;
        private final Currency currency;
        private final boolean onlineMode;
        private final String gameType;
        private final boolean logEvents;

        @Data
        public static class Currency {
            private final String iso4217;
            private final String symbol;
        }
    }

    @Data
    public static class Server {
        private final int id;
        private final String name;
    }
}