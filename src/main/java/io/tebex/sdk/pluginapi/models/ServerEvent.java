package io.tebex.sdk.pluginapi.models;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Getter
public class ServerEvent {
    @SerializedName("username_id")
    private final String uuid;
    @SerializedName("event_type")
    private final String eventType;
    @SerializedName("event_date")
    private final String eventDate;
    private final String username;
    private final String ip;

    public enum EnumServerEventType {
        JOIN, LEAVE
    }

    public ServerEvent(String uuid, String username, String ip, EnumServerEventType eventType) {
        this.uuid = uuid;
        this.username = username;
        this.ip = anonymizeIp(ip);
        this.eventType = eventType.name().toLowerCase();
        this.eventDate = Instant.now()
                .atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
    }

    /**
     * Anonymizes the last octet in a given IP.
     *
     * @param ipIn The full IP address, ex. 192.168.1.100
     * @return An anonymized IP, ex. 192.168.1.x
     */
    private String anonymizeIp(String ipIn) {
        int lastOctetStart = ipIn.lastIndexOf(".");
        if (lastOctetStart == -1) {
            return ipIn;
        }
        return ipIn.substring(0, lastOctetStart) + ".x";
    }
}
