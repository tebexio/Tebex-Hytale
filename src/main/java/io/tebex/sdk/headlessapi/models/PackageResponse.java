package io.tebex.sdk.headlessapi.models;

import lombok.Data;

import java.util.List;

@Data
public class PackageResponse {
    private List<HeadlessPackage> data;
}
