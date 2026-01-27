package io.tebex.sdk.pluginapi.models;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface ICommand {
    String getCommand();
    int getId();
    @Nullable Integer getPayment();
    @Nullable Integer getPackageId();
    CommandConditions getConditions();
    default String getParsedCommand(@Nonnull String username, @Nonnull String uuid) {
        return getCommand()
                .replace("{name}", username)
                .replace("{username}", username)
                .replace("{uuid}", uuid)
                .replace("{id}", uuid);
    }
}
