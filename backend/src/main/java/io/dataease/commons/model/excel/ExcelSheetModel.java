package io.dataease.commons.model.excel;

import lombok.Data;

import java.util.List;

@Data
public class ExcelSheetModel {

    private String sheetName;

    private List<String> heads;

    private List<List<String>> data;

}
