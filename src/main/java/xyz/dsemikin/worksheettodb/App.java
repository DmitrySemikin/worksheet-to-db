package xyz.dsemikin.worksheettodb;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class App {
    public static void main(String[] args) throws ExcelFileStructureException, IOException, SQLException {

        if (args.length != 4) {
            throw new IllegalArgumentException("""
                    "Application takes exactly four arguments:
                     1. path to excel file,
                     2. DB connection string
                     3. DB username
                     4. DB password
                    """);
        }

        final String inputPath = args[0];
        final String connectionString = args[1];
        final String dbUsername = args[2];
        final String dbPassword = args[3];


        final Path inputFilePath = Paths.get(args[0]);
        Map<String, List<Map<String, ExcelValueWrapper>>> data = ExcelFileReader.readExcelFile(inputFilePath);

        final Properties connectionProperties = new Properties();
        connectionProperties.put("user", dbUsername);
        connectionProperties.put("password", dbPassword);

        SqlDbTableImporter.importTables(connectionString, connectionProperties, data);

        System.out.println("Done.");
    }

}
