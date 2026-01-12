package io.tebex.sdk.pluginapi.models.responses;

import lombok.Data;

import java.util.List;

@Data
public class PaginatedResponse<T> {
    private final Pagination pagination;
    private final List<T> data;
}