package io.dataease.controller.panel;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.dataease.dto.panel.PanelViewTableDTO;
import io.dataease.service.panel.PanelViewService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * Author: wangjiahao
 * Date: 2021-03-05
 * Description:
 */
@Api(tags = "仪表板：仪表板视图")
@ApiSupport(order = 150)
@RestController
@RequestMapping("panel/view")
public class PanelViewController {

    @Resource
    private PanelViewService panelViewService;

    @ApiOperation("视图详细信息")
    @GetMapping("/detailList/{panelId}")
    public List<PanelViewTableDTO> detailList(@PathVariable String panelId) throws Exception {
        return panelViewService.detailList(panelId);
    }
}
