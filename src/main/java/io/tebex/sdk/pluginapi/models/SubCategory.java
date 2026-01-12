package io.tebex.sdk.pluginapi.models;

import lombok.Data;

import java.util.List;

@Data
public class SubCategory implements ICategory {
    private final int id;
    private final int order;
    private final String name;
    private final String guiItem;
    private final Category parentCategory;
    private final List<CategoryPackage> categoryPackages;

    @Override
    public String getName() {
        return name;
    }

    public List<CategoryPackage> getPackages() {
        return categoryPackages;
    }
}
