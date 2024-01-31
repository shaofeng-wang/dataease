package io.dataease.mobile.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class DirRequest implements Serializable {

    private String pid;

    private String name;

}
