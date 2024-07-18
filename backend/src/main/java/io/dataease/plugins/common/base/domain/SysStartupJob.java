package io.dataease.plugins.common.base.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class SysStartupJob implements Serializable {
    private String id;

    private String name;

    private String status;

    private static final long serialVersionUID = 1L;
}
