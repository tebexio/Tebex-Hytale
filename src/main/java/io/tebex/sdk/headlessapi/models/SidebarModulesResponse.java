package io.tebex.sdk.headlessapi.models;

import lombok.Data;

import java.util.List;

@Data
public class SidebarModulesResponse {
    private List<SidebarModule> data;
}
