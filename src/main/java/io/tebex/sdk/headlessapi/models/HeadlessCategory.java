package io.tebex.sdk.headlessapi.models;

import lombok.Data;

import java.util.List;

@Data
public class HeadlessCategory {
    private int id;
    private String name;
    private String slug;
    private String description;
    private int order;
    private String displayType;
    private List<HeadlessPackage> packages;
}
