package xyz.dsemikin.worksheettodb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SqlDbTableImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlDbTableImporter.class);

    public static void importTables(
        final String connectionString,
        final Map<String, List<Map<String, ExcelValueWrapper>>> data
    ) {
        // - create table
        //   - table name - simplify sheet names
        //   - column names - simplify column names
        //   - column types - detect from data format

        final List<String> sheetNames = new ArrayList<>(data.keySet());
        final List<String> tableNames = generateUniqueNamesForDb(sheetNames);
        assert sheetNames.size() == tableNames.size();

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

        }

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
}
