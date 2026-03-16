package io.tebex.sdk.headlessapi.models;

import lombok.Data;

import java.util.List;

@Data
public class CategoryResponse {
    private List<HeadlessCategory> data;
}
