package io.dataease.ext;

import io.dataease.controller.sys.response.SysUserGridResponse;
import io.dataease.ext.query.GridExample;

import java.util.List;

public interface ExtSysUserMapper {
    List<SysUserGridResponse> query(GridExample example);

    List<String> ldapUserNames(Integer from);
}
