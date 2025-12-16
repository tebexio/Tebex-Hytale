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

    public static Category fromJsonObject(JsonObject jsonObject) {
        Category category = new Category(
                jsonObject.get("id").getAsInt(),
                jsonObject.get("order").getAsInt(),
                jsonObject.get("name").getAsString(),
                jsonObject.get("gui_item").getAsString(),jsonObject.has("only_subcategories") && jsonObject.get("only_subcategories").getAsBoolean(),
                jsonObject.getAsJsonArray("packages").asList().stream().map(item -> CategoryPackage.fromJsonObject(item.getAsJsonObject())).collect(Collectors.toList())
        );

        category.setSubCategories(jsonObject.getAsJsonArray("subcategories").asList().stream().map(item -> SubCategory.fromJsonObject(item.getAsJsonObject(), category)).collect(Collectors.toList()));
        return category;
    }
}
