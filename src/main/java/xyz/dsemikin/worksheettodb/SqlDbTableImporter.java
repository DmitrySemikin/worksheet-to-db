package xyz.dsemikin.worksheettodb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class SqlDbTableImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlDbTableImporter.class);
    private static final Integer NON_STRING_FIELD_LENGTH_PLACEHOLDER = -4455; // just random number, which is invalid length

    public static void importTables(
            final String connectionString,
            final Properties connectionProperties,
            final Map<String, List<Map<String, ExcelValueWrapper>>> data
    ) throws ExcelFileStructureException, SQLException {

        if (data.isEmpty()) {
            LOGGER.info("Provided data is empty. No DB modification will be done.");
            return;
        }

        // - create table
        //   - table name - simplify sheet names
        //   - column names - simplify column names
        //   - column types - detect from data format

        final List<String> sheetNames = new ArrayList<>(data.keySet());
        final List<String> tableNames = generateUniqueNamesForDb(sheetNames);
        assert sheetNames.size() == tableNames.size();

        final TableDefinitions tableDefinitions = calculateTableDefinitions(data, sheetNames, tableNames);

        final Map<String, String> createTableStatements = generateCreateTableStatements(tableNames, tableDefinitions);
        final Map<String, String> insertStatements = generateInsertStatements(tableNames, tableDefinitions);


        try (Connection connection = DriverManager.getConnection(connectionString, connectionProperties)) {
            for (int sheetNum = 0; sheetNum < sheetNames.size(); ++ sheetNum) {
                final String sheetName = sheetNames.get(sheetNum);
                final String tableName = tableNames.get(sheetNum);

                List<Map<String, ExcelValueWrapper>> sheetData = data.get(sheetName);
                assert sheetData.size() > 0;
                List<String> fieldNames = getFieldNames(sheetData);

                final String createTableStatement = createTableStatements.get(tableName);
                LOGGER.info("Create table statement: ");
                LOGGER.info(createTableStatement);
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(createTableStatement);
                }

                final String insertStatement = insertStatements.get(tableName);
                LOGGER.info("Inset data statement: ");
                LOGGER.info(insertStatement);
                try (PreparedStatement statement = connection.prepareStatement(insertStatement)) {
                    for (final var rowData : sheetData) {
                        final int columnsCount = tableDefinitions.columnsCount(tableName);
                        for (int columnNum = 0; columnNum < columnsCount; ++columnNum) {
                            final String fieldName = fieldNames.get(columnNum);
                            ExcelValueWrapper.Type columnType = tableDefinitions.columnTypes(tableName).get(columnNum);
                            ExcelValueWrapper value = rowData.get(fieldName);
                            final int parameterIndex = columnNum + 1; // parameterIndex is 1-based
                            switch (columnType) {
                                case DOUBLE  -> statement.setDouble(parameterIndex, value.maybeDoubleValue());
                                case DATE    -> statement.setTimestamp(parameterIndex, Timestamp.valueOf(value.maybeDateValue()));
                                case BOOLEAN -> statement.setBoolean(parameterIndex, value.maybeBooleanValue());
                                case STRING  -> statement.setString(parameterIndex, value.maybeStringValue());
                                case EMPTY   -> statement.setString(parameterIndex, ""); // by convention we use empty string
                                default -> throw new IllegalStateException(
                                        "Sheet: " + sheetName + ", column " +
                                                tableDefinitions.columnNames(tableName).get(columnNum) +
                                                "Unknown column type: " + columnType
                                );
                            }
                        }
                        statement.executeUpdate();
                    }
                }
            }
        }
    }

    private static ArrayList<String> getFieldNames(List<Map<String, ExcelValueWrapper>> sheetData) {
        return new ArrayList<>(sheetData.get(0).keySet());
    }

    private static Map<String, String> generateInsertStatements(
            final List<String> tableNames,
            final TableDefinitions tableDefinitions
    ) {
        final Map<String, String> insertStatements = new HashMap<>();
        for (final String tableName : tableNames) {
            final List<String> columnNames = tableDefinitions.columnNames(tableName);
            final int columnsCount = tableDefinitions.columnsCount(tableName);

            final StringBuilder insertStatement = new StringBuilder();
            insertStatement.append("INSERT INTO ").append(tableName).append(" ( ");
            for (int columnNum = 0; columnNum < columnsCount; ++columnNum) {
                insertStatement.append(columnNames.get(columnNum));
                if (columnNum != columnsCount - 1) {
                    insertStatement.append(", ");
                }
            }
            insertStatement.append(" ) VALUES ( " );
            for (int columnNum = 0; columnNum < columnsCount; ++columnNum) {
                insertStatement.append("?");
                if (columnNum != columnsCount - 1) {
                    insertStatement.append(", ");
                }
            }
            insertStatement.append(")");
            insertStatements.put(tableName, insertStatement.toString());
        }
        return insertStatements;
    }

    private static Map<String, String> generateCreateTableStatements(
            final List<String> tableNames,
            final TableDefinitions tableDefinitions
    ) {
        final Map<String, String> createTableStatements = new HashMap<>();
        for (final String tableName : tableNames) {

            final List<String> columnNames = tableDefinitions.columnNames(tableName);
            final List<ExcelValueWrapper.Type> columnTypes = tableDefinitions.columnTypes(tableName);
            final List<Integer> stringColumnLengths = tableDefinitions.stringColumnLengths(tableName);

            final StringBuilder createTableSqlStatement = new StringBuilder();
            createTableSqlStatement
                    .append("CREATE TABLE ").append(tableName)
                    .append("(");
            final int columnsCount = tableDefinitions.columnsCount(tableName);
            for (int columnNum = 0; columnNum < columnsCount; ++columnNum) {
                final String columnType = switch (columnTypes.get(columnNum)) {
                    case BOOLEAN -> "BOOLEAN";
                    case DATE -> "TIMESTAMP WITH TIME ZONE";
                    case DOUBLE -> "DOUBLE";
                    case EMPTY -> "VARCHAR(10)"; // just default type. Could be anything
                    case STRING -> "VARCHAR(" + (stringColumnLengths.get(columnNum) + 3) + ")"; // +3 - just in case
                };
                createTableSqlStatement
                        .append(columnNames.get(columnNum)).append(" ").append(columnType);
                if (columnNum != columnsCount - 1) {
                    createTableSqlStatement.append(", ");
                }
            }
            createTableSqlStatement.append(")");
            createTableStatements.put(tableName, createTableSqlStatement.toString());
        }
        return createTableStatements;
    }

    private static TableDefinitions calculateTableDefinitions(
            Map<String, List<Map<String, ExcelValueWrapper>>> data,
            List<String> sheetNames,
            List<String> tableNames
    ) throws ExcelFileStructureException {
        if (sheetNames.size() != tableNames.size()) {
            String message = "Sizes of sheetNames and tableNames must be equal. They are (respectively): "
                    + sheetNames.size() + " and " + tableNames.size() + ".";
            assert false: message;
            throw new IllegalStateException(message);
        }

        final TableDefinitions tableDefinitions = new TableDefinitions();

        for (int sheetNum = 0; sheetNum < sheetNames.size(); ++sheetNum) {

            final String sheetName = sheetNames.get(sheetNum);
            final String tableName = tableNames.get(sheetNum);
            final List<Map<String, ExcelValueWrapper>> sheetData = data.get(sheetName);

            if (sheetData.isEmpty()) {
                LOGGER.info("Data for sheet " + sheetName + " is empty. Skipping.");
                continue;
            }

            final List<String> fieldNames = new ArrayList<>(sheetData.get(0).keySet());
            final List<String> dbColumnNames = generateUniqueNamesForDb(fieldNames);
            final List<ExcelValueWrapper.Type> dbColumnTypes = new ArrayList<>();
            final List<Integer> stringColumnLengths = new ArrayList<>(); // For non-string columns it is just -1

            for (final String fieldName : fieldNames) {
                Set<ExcelValueWrapper.Type> columnTypes = sheetData.stream()
                        .map(rowData -> rowData.get(fieldName).type())
                        .filter(type -> !type.equals(ExcelValueWrapper.Type.EMPTY))
                        .collect(Collectors.toSet());
                final ExcelValueWrapper.Type columnType;
                if (columnTypes.isEmpty()) {
                    columnType = ExcelValueWrapper.Type.EMPTY;
                } else if (columnTypes.size() == 1) {
                    columnType = columnTypes.iterator().next();
                } else {
                    throw new ExcelFileStructureException("Sheet " + sheetName + ": ERROR: column " + fieldName +
                            " has cells of more than one type in it. It is not allowed.");
                }
                dbColumnTypes.add(columnType);

                Optional<Integer> maxFieldLength = sheetData.stream()
                        .map(rowData -> {
                            ExcelValueWrapper value = rowData.get(fieldName);
                            if (value.type().equals(ExcelValueWrapper.Type.STRING)) {
                                return value.maybeStringValue().length();
                            } else {
                                return NON_STRING_FIELD_LENGTH_PLACEHOLDER;
                            }
                        })
                        .max(Comparator.naturalOrder());
                if (maxFieldLength.isEmpty()) {
                    String message = "Sheet " + sheetName + ": Sheet data is not expected to be empty at this point.";
                    assert false : message;
                    throw new IllegalStateException(message);
                }
                stringColumnLengths.add(maxFieldLength.get());
            }

            tableDefinitions.addTableDefinition(tableName, dbColumnNames, dbColumnTypes, stringColumnLengths);
        }
        return tableDefinitions;
    }

    private static List<String> generateUniqueNamesForDb(final List<String> baseNames) {
        final Set<String> usedNames = new HashSet<>();
        final List<String> generatedNames = new ArrayList<>();
        for (var baseName : baseNames) {
            final var simplifiedName = simplifyStringForDbName(baseName);
            final var uniqueName = ensureNameIsUnique(simplifiedName, usedNames);
            usedNames.add(uniqueName);
            generatedNames.add(uniqueName);
        }
        if (generatedNames.size() != baseNames.size()) {
            throw new IllegalStateException("Failed to generate unique names for DB. Algorithm does not support import data.");
        }
        return generatedNames;
    }

    private static String simplifyStringForDbName(final String baseName) {
        return baseName.toLowerCase(Locale.ROOT); // TODO
    }

    private static String ensureNameIsUnique(final String baseName, final Set<String> existingNames) {
        return baseName; // TODO
    }

    private static class TableDefinitions {

        private final Map<String, List<String>> tableColumnNamesMap = new HashMap<>();
        private final Map<String, List<ExcelValueWrapper.Type>> tableColumnTypesMap = new HashMap<>();
        private final Map<String, List<Integer>> tableStringColumnLengthsMap = new HashMap<>();

        public TableDefinitions() {
            // Nothing to do here yet
        }

        public void addTableDefinition(
                final String tableName,
                final List<String> tableColumnNames,
                final List<ExcelValueWrapper.Type> tableColumnTypes,
                final List<Integer> tableStringColumnLengths
        ) {
            if (
                    tableColumnNames.size() != tableColumnTypes.size() ||
                    tableColumnNames.size() != tableStringColumnLengths.size()
            ) {
                throw new IllegalArgumentException("Length of column names list must be " +
                        "equal to column types list and to the column lengths list. " +
                        "Provided column names list length: " + tableColumnNames.size() +
                        ". Provided column types length: " + tableColumnTypes.size() +
                        ". Provided column lengths length: " + tableStringColumnLengths.size()
                );
            }
            if (tableColumnNamesMap.containsKey(tableName)) {
                LOGGER.warn("Table definitions already contains definition for table " + tableName + ". It will be replaced.");
            }
            tableColumnNamesMap.put(tableName, tableColumnNames);
            tableColumnTypesMap.put(tableName, tableColumnTypes);
            tableStringColumnLengthsMap.put(tableName, tableStringColumnLengths);
        }

        public List<String> columnNames(final String tableName) {
            return tableColumnNamesMap.get(tableName);
        }

        public List<ExcelValueWrapper.Type> columnTypes(final String tableName) {
            return tableColumnTypesMap.get(tableName);
        }

        public List<Integer> stringColumnLengths(final String tableName) {
            return tableStringColumnLengthsMap.get(tableName);
        }

        public int columnsCount(final String tableName) {
            return tableColumnNamesMap.get(tableName).size();
        }
    }

}
