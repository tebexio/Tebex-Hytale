package io.tebex.sdk.pluginapi.models;

import lombok.Data;

@Data
public class CommunityGoal {
    private final int id;
    private final String createdAt;
    private final String updatedAt;
    private final int accountId;
    private final String name;
    private final String description;
    private final String image;
    private final double target;
    private final double current;
    private final int repeatable; // 0 if not repeatable
    private final String lastAchieved;
    private final int timesAchieved;
    private final Status status;
    private final int sale; // 0 if not repeatable

    public enum Status {
        ACTIVE,
        COMPLETED,
        DISABLED
    }
}