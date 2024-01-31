package io.dataease.controller.panel;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.dataease.commons.model.BaseRspModel;
import io.dataease.service.panel.PanelGroupService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Author: wangjiahao
 * Date: 2021-03-05
 * Description:
 */
@Api(tags = "仪表板：设计")
@ApiSupport(order = 140)
@RestController
@RequestMapping("panel/design")
public class PanelDesignController {

    @Resource
    private PanelGroupService panelGroupService;

    @ApiOperation("保存仪表板设计")
    @PostMapping("/saveDesign/{id}")
    public BaseRspModel deleteCircle(@PathVariable String id) {
        panelGroupService.deleteCircle(id);
        return new BaseRspModel();
    }


}
