package xyz.dsemikin.worksheettodb;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExcelFileReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelFileReader.class);

    // TODO: Decompose to multiple methods, so that the whole thing is more readable.
    public static Map<String, List<Map<String, ExcelValueWrapper>>> readExcelFile(
            final Path excelFilePath
    ) throws IOException, ExcelFileStructureException {

        // Notice: instead of creating dictionary from each row and thus repeating
        // field names in each object, we could use alternative data layout:
        // rectangular, which repeats structure of the worksheet. Name of the value
        // (or value for the given name) can be obtained by matching indices in header
        // row and row of interest. But I think, that work with dictionaries is simpler
        // and performance should not be an issue in this particular application.

        // Using list of maps has also another deficiency: it does not enforce, that
        // fields of all map objects are the same. But again, we will still use it,
        // because of convenience of doing this.

        // workbook['sheetName'] - worksheet
        // workbook['sheetName'][recordNum] - record (= row in the table)
        // workbook['sheetName'][recordNum]['key'] - value (key = column name)
        final Map<String, List<Map<String, ExcelValueWrapper>>> workbookData = new HashMap<>();

        final Workbook workbook = WorkbookFactory.create(excelFilePath.toFile());
        Iterator<Sheet> sheetIterator = workbook.sheetIterator();
        while(sheetIterator.hasNext()) {
            final Sheet sheet = sheetIterator.next();
            final String sheetName = sheet.getSheetName();

            final int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum == -1) {
                LOGGER.info("Worksheet " + sheetName + " does not have any data. Skipping.");
                continue;
            } else if (lastRowNum == 0) {
                LOGGER.info("Worksheet " + sheetName + " has only one row (header) and no data. Skipping.");
                continue;
            }


            // Read header

            Row headerRow = sheet.getRow(0);

            short headerLastCellNumPlusOne = headerRow.getLastCellNum();
            if (headerLastCellNumPlusOne == -1) {
                throw new ExcelFileStructureException("Sheet " + sheetName + " : Header row of the table must define at least one column.");
            } else if (headerLastCellNumPlusOne == 0) {
                throw new IllegalStateException("Sheet " + sheetName + ", row num 0" +
                        ": lastCellNumPlusOne == 0 - Apache POI docs promise, that this should never happen.");
            }

            final List<String> columnNames = new ArrayList<>();
            for (int cellNum = 0; cellNum < headerLastCellNumPlusOne; ++cellNum) {
                Cell cell = headerRow.getCell(cellNum);
                CellType cellType = cell.getCellType();
                if (!cellType.equals(CellType.STRING)) {
                    throw new ExcelFileStructureException("Sheet " + sheetName + ": All header cells must be of type 'text' or 'string'.");
                }
                final String columnName = cell.getStringCellValue();
                if (columnName == null || columnName.isBlank()) {
                    throw new ExcelFileStructureException("Sheet " + sheetName + ", column num " + cell.getColumnIndex() + ": Column names must be non-empty non-blank strings.");
                }
                columnNames.add(columnName);
            }


            // Read data

            List<Map<String, ExcelValueWrapper>> sheetData = new ArrayList<>();
            for (int rowNum = 1; rowNum <= lastRowNum; ++rowNum) {
                Row row = sheet.getRow(rowNum);
                short lastCellNumPlusOne = row.getLastCellNum();
                if (lastCellNumPlusOne == -1) {
                    LOGGER.info("Sheet " + sheetName + ", row num " + row.getRowNum() + ": Empty row. Skipping.");
                } else if (lastCellNumPlusOne == 0) {
                    throw new IllegalStateException("Sheet " + sheetName + ", row num " + row.getRowNum() +
                            ": lastCellNumPlusOne == 0 - Apache POI docs promise, that this should never happen.");
                }

                if (lastCellNumPlusOne > headerLastCellNumPlusOne) {
                    throw new ExcelFileStructureException("Sheet " + sheetName + ", row " + row.getRowNum() +
                            ": Cell count in the row (" + lastCellNumPlusOne + ") is greater, than cell count in header (" +
                            headerLastCellNumPlusOne + "). It is not allowed.");
                }

                Map<String, ExcelValueWrapper> dataRow = new LinkedHashMap<>();
                // We iterate up to the number of columns. If the cells at the end are empty,
                // we fill data with "EMPTY" values
                for (int cellNum = 0; cellNum < headerLastCellNumPlusOne; ++cellNum) {
                    final String columnName = columnNames.get(cellNum);
                    if (cellNum >= lastCellNumPlusOne) {
                        dataRow.put(columnName, new ExcelValueWrapper(null));
                    } else {
                        Cell cell = row.getCell(cellNum);
                        final ExcelValueWrapper dataValue =
                            cell == null
                                ? new ExcelValueWrapper(null)
                                : switch (cell.getCellType()) {
                                    case BLANK -> new ExcelValueWrapper(null);
                                    case STRING -> new ExcelValueWrapper(cell.getStringCellValue());
                                    case NUMERIC ->
                                        DateUtil.isCellDateFormatted(cell)
                                            ? new ExcelValueWrapper(cell.getLocalDateTimeCellValue())
                                            : new ExcelValueWrapper(cell.getNumericCellValue());
                                    case BOOLEAN -> new ExcelValueWrapper(cell.getBooleanCellValue());
                                    default -> throw new ExcelFileStructureException(
                                            "Sheet " + sheetName + ", row num " + rowNum + ", column " + columnName +
                                            " (" + cellNum + ")" + ": Unsupported cell type: " + cell.getCellType()
                                    );
                                };
                        dataRow.put(columnName, dataValue);
                    }
                }

                if (dataRow.values().stream().allMatch(val -> val.type() == ExcelValueWrapper.Type.EMPTY)) {
                    LOGGER.info("Sheet " + sheetName + ", row num " + rowNum + ": All cells are empty. Skipping row.");
                } else {
                    sheetData.add(dataRow);
                }
            }

            workbookData.put(sheetName, sheetData);
        }

        return workbookData;
    }
}
