package io.dataease.ext;

import io.dataease.dto.panel.PanelStoreDto;
import io.dataease.ext.query.GridExample;

import java.util.List;

public interface ExtPanelStoreMapper {

    List<PanelStoreDto> query(GridExample example);
}
