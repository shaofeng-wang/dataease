package io.dataease.service.sys;


import io.dataease.controller.sys.base.BaseGridRequest;
import io.dataease.controller.sys.response.RoleUserItem;
import io.dataease.ext.ExtSysRoleMapper;
import io.dataease.plugins.common.base.domain.SysRole;
import io.dataease.plugins.common.base.mapper.SysRoleMapper;
import io.dataease.plugins.common.base.mapper.SysUsersRolesMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class SysRoleService {

    @Resource
    private ExtSysRoleMapper extSysRoleMapper;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private SysUsersRolesMapper sysUsersRolesMapper;

    public List<SysRole> query(BaseGridRequest request) {
        List<SysRole> result = extSysRoleMapper.query(request.convertExample());
        return result;
    }

    public List<RoleUserItem> allRoles() {
        return extSysRoleMapper.queryAll();
    }

    public void create(SysRole request) {
        request.setCreateTime(System.currentTimeMillis());
        request.setUpdateTime(request.getCreateTime());
        sysRoleMapper.insertSelective(request);
    }

    public void update(SysRole request) {
        request.setUpdateTime(System.currentTimeMillis());
        sysRoleMapper.updateByPrimaryKeySelective(request);
    }

    public void delete(Long roleId) {
        sysRoleMapper.deleteByPrimaryKey(roleId);
    }

}
