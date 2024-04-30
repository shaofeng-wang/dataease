package io.dataease.dto.dataset;

import io.dataease.commons.model.AuthURD;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;


@Data
public class DatasetShareFineDto implements Serializable {

    private static final long serialVersionUID = -792964171742204428L;
    @ApiModelProperty("资源ID列表")
    private List<String> resourceId;

    @ApiModelProperty("分享节点类型：leaf，spine")
    private String sharedNodeType;

    @ApiModelProperty("分享信息")
    private AuthURD authURD;
}
