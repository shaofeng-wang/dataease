package io.dataease.ext;

import io.dataease.controller.request.panel.link.OverTimeRequest;
import org.apache.ibatis.annotations.Param;

public interface ExtPanelLinkMapper {

    void updateOverTime(@Param("request") OverTimeRequest request);
    
}
