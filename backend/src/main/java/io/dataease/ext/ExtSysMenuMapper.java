package io.dataease.ext;

import io.dataease.controller.sys.request.SimpleTreeNode;
import io.dataease.ext.query.GridExample;
import io.dataease.plugins.common.base.domain.SysMenu;

import java.util.List;

public interface ExtSysMenuMapper {

    List<SimpleTreeNode> allNodes();

    List<SimpleTreeNode> nodesByExample(GridExample example);

    List<SysMenu> querySysMenu();
}
