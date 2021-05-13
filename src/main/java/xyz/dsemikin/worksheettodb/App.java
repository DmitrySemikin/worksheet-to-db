package xyz.dsemikin.worksheettodb;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class App {
    public static void main(String[] args) throws ExcelFileStructureException, IOException {

        final Path inputFilePath = Paths.get("C:\\d\\tmp\\input.xlsx");
        Map<String, List<Map<String, ExcelValueWrapper>>> data = ExcelFileReader.readExcelFile(inputFilePath);
        System.out.println("Done.");
    }

}
