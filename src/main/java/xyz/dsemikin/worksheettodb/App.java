package xyz.dsemikin.worksheettodb;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class App {
    public static void main(String[] args) throws ExcelFileStructureException, IOException {

        if (args.length != 1) {
            throw new IllegalArgumentException("Application takes exactly one argument: path to excel file");
        }

        final Path inputFilePath = Paths.get(args[0]);
        Map<String, List<Map<String, ExcelValueWrapper>>> data = ExcelFileReader.readExcelFile(inputFilePath);
        System.out.println("Done.");
    }

}
