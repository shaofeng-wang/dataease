package io.dataease.service.panel.applog;

import io.dataease.ext.query.GridExample;
import lombok.Data;

@Data
public class AppLogQueryParam extends GridExample {
    private String userId;

}
