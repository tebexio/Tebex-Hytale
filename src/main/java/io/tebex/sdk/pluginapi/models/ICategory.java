package io.tebex.sdk.pluginapi.models;

import java.util.List;

public interface ICategory {
    int getId();

    int getOrder();

    String getName();

    String getGuiItem();

    List<CategoryPackage> getPackages();
}
