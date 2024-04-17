package io.dataease.commons.constants;

public enum PrivilegeExtendExtend {
    EXTEND_VIEW("view"),
    EXTEND_EXPORT("export"),
    EXTEND_USE("use"),
    EXTEND_MANAGE("manage"),
    EXTEND_GRANT("grant");

    private String extend;

    public String getExtend() {
        return extend;
    }

    public void setExtend(String extend) {
        this.extend = extend;
    }

    PrivilegeExtendExtend(String extend) {
        this.extend = extend;
    }
}
