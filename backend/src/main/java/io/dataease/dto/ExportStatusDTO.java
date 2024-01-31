package io.dataease.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExportStatusDTO {
    private long percent;
    private long totalItems;
    private long completedItems;
}
