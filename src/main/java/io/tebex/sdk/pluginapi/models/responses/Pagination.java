package io.tebex.sdk.pluginapi.models.responses;

import lombok.Data;
@Data
public class Pagination {
    private final int totalResults;
    private final int currentPage;
    private final int lastPage;
    private final String previous;
    private final String next;
}
