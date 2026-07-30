package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PageResponse<T> {
    private List<T> items;
    private int page;
    private int limit;
    private long totalItems;
    private int totalPages;
}
