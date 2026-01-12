package io.tebex.sdk.pluginapi.models;

import com.google.gson.JsonObject;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class Category implements ICategory {
    private final int id;
    private final int order;
    private final String name;
    private final String guiItem;
    private final boolean onlySubcategories;
    private final List<CategoryPackage> categoryPackages;
    private List<SubCategory> subCategories = new ArrayList<>();

    @Override
    public int getId() {
        return id;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getGuiItem() {
        return guiItem;
    }

    public List<CategoryPackage> getPackages() {
        return categoryPackages;
    }
}
