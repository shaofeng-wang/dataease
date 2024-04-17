package io.dataease.controller.sys;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.dataease.auth.annotation.DeLog;
import io.dataease.commons.constants.SysLogConstants;
import io.dataease.plugins.common.base.domain.SysRole;
import io.dataease.service.sys.SysRoleService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@Api(tags = "系统：角色管理")
@ApiSupport(order = 220)
@RequestMapping("/api/role")
public class SysRoleController {

    @Resource
    private SysRoleService sysRoleService;

    @ApiOperation("创建角色")
    @RequiresPermissions("role:add")
    @PostMapping("/create")
    @DeLog(
        operatetype = SysLogConstants.OPERATE_TYPE.CREATE,
        sourcetype = SysLogConstants.SOURCE_TYPE.ROLE,
        value = "name"
    )
    public void create(@RequestBody SysRole request) {
        sysRoleService.create(request);
    }

    @ApiOperation("更新角色")
    @RequiresPermissions("role:edit")
    @PostMapping("/update")
    @DeLog(
        operatetype = SysLogConstants.OPERATE_TYPE.MODIFY,
        sourcetype = SysLogConstants.SOURCE_TYPE.ROLE,
        value = "roleId"
    )
    public void update(@RequestBody SysRole request) {
        sysRoleService.update(request);
    }

    @ApiOperation("删除角色")
    @RequiresPermissions("role:del")
    @PostMapping("/delete/{roleId}")
    @ApiImplicitParam(paramType = "path", value = "角色ID", name = "roleId", required = true, dataType = "Integer")
    @DeLog(
        operatetype = SysLogConstants.OPERATE_TYPE.DELETE,
        sourcetype = SysLogConstants.SOURCE_TYPE.ROLE
    )
    public void delete(@PathVariable("roleId") Long roleId) {
        sysRoleService.delete(roleId);
    }
}
