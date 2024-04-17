package io.dataease.controller.request.panel;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PanelShareSearchRequest implements Serializable {

    @ApiModelProperty(value = "分享目标类型", allowableValues = "0:user,1:role,2:dept")
    private String type;

    @ApiModelProperty("资源id")
    private String resourceId;

    @ApiModelProperty("资源id列表")
    private List<String> resourceIds;

    @ApiModelProperty("资源类型")
    private String resourceType;

    @ApiModelProperty("当前用户")
    private String currentUserName;

}
