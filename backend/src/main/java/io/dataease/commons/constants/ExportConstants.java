package io.dataease.commons.constants;

public class ExportConstants {
    //
    public static final String EXPORT_DATA_CACHE = "export_data_cache";
    // 导出心跳缓存标记，如果没有心跳，前端可能关闭，或者超时，则立刻终止导出操作，避免占用资源
    public static final String EXPORT_DATA_HEARTBEAT_CACHE = "export_data_heartbeat_cache";
}
