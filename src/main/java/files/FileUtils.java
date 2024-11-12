package files;

import com.mxgraph.model.mxCell;
import controllers.ConstantController;
import controllers.MainController;
import database.TableCreator;
import dsl.AntlrController;
import dsl.DslController;
import dsl.DslErrorListener;
import dsl.antlr4.RelAlgebraLexer;
import dsl.antlr4.RelAlgebraParser;
import entities.cells.TableCell;
import entities.utils.cells.CellUtils;
import enums.FileType;
import exceptions.dsl.InputException;
import gui.frames.ErrorFrame;
import gui.frames.main.MainFrame;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;

public class FileUtils {

    private static final File TEMP = new File("temp");

    private FileUtils() {

    }

    public static Path getDBestLogo() throws Exception{

        File assets = new File("assets\\images");

//        File images = (Objects.requireNonNull(Arrays.stream(Objects.requireNonNull(assets.listFiles()))
//                .filter(x -> x.getName().equals("dbest-logo.png")).findFirst().orElseThrow()));
File[] files = assets.listFiles();
        if (files == null) {
            throw new NullPointerException("No files found in the assets directory.");
        }

        File images = null;
        for (File file : files) {
            if (file.getName().equals("dbest-logo.png")) {
                images = file;
                break;
            }
        }

        if (images == null) {
            throw new RuntimeException("File dbest-logo.png not found.");
        }


        return images.toPath();

    }

    public static void verifyExistingFilesToInitialize(){

        try
        {
            File initDirectory = new File("init");

            List<File> txtFiles = new ArrayList<>();
            List<File> headerFiles = new ArrayList<>();

            for (File file : Objects.requireNonNull(initDirectory.listFiles())) {

                if (!file.isFile()) continue;

                if(file.getName().endsWith(FileType.HEADER.extension)) headerFiles.add(file);

                if(file.getName().endsWith(FileType.TXT.extension)) txtFiles.add(file);
            }

            headerFiles.forEach(FileUtils::initHeader);
            txtFiles.forEach(FileUtils::initTxt);

        }
        catch (Exception e){

        }

    }

    private static void initHeader(File file)  {
        TableCell tableCell;
        try {
            tableCell = TableCreator.createTable(file);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assert tableCell != null;
        mxCell tableJCell = (mxCell) MainFrame
            .getTablesGraph()
            .insertVertex(
                MainFrame.getTablesGraph().getDefaultParent(), null, tableCell.getName(),
                0, MainController.getCurrentTableYPosition(), tableCell.getWidth(),
                tableCell.getHeight(), tableCell.getStyle()
            );

        CellUtils.addCell(tableJCell, tableCell);

        MainController.getTables().put(tableCell.getName(), tableCell);

        MainFrame.getTablesPanel().revalidate();

        MainController.incrementCurrentTableYPosition(40);
    }

    private static void initTxt(File file){

        StringBuilder content = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        RelAlgebraParser parser = new RelAlgebraParser(new CommonTokenStream(new RelAlgebraLexer(CharStreams.fromString(content.toString()))));

        parser.removeErrorListeners();

        DslErrorListener errorListener = new DslErrorListener();
        parser.addErrorListener(errorListener);

        ParseTreeWalker walker = new ParseTreeWalker();

        AntlrController listener = new AntlrController();

        walker.walk(listener, parser.command());

        if (!DslErrorListener.getErrors().isEmpty()) {
            return;
        }

        try {
            DslController.parser();
        } catch (InputException exception) {
            DslController.reset();
        }
    }

    private static void createTempIfNotExists() {
        if (Stream.of(Objects.requireNonNull(ConstantController.ROOT_DIRECTORY.toFile().listFiles())).noneMatch(file -> file.isDirectory() && file.getName().equals("temp"))) {
            TEMP.mkdir();
        }
    }

    public static Optional<File> getFileFromTempDirectory(String fileName) {
        createTempIfNotExists();

        File file = null;

        for (File tempDirectoryFile : Objects.requireNonNull(TEMP.listFiles())) {
            if (Objects.equals(fileName, tempDirectoryFile.getName())) {
                file = new File(tempDirectoryFile.getAbsolutePath());
                break;
            }
        }

        return file == null ? Optional.empty() : Optional.of(file);
    }

    public static Path getTempDirectory() {
        createTempIfNotExists();

        return TEMP.toPath();
    }

    public static void moveToTempDirectory(File... files) {
        createTempIfNotExists();

        Arrays.stream(files).forEach(file -> {
            try {
                Files.move(file.toPath(), new File(TEMP.getAbsolutePath(), file.getName()).toPath());
            } catch (IOException e) {
                new ErrorFrame(e.getMessage());
            }
        });

    }

    public static File getFile(String fileName) {
        for (File file : Objects.requireNonNull(ConstantController.ROOT_DIRECTORY.toFile().listFiles())) {
            if (fileName.equals(file.getName())) {
                return file;
            }
        }

        throw new NoSuchElementException("The file doesn't exist");
    }

    public static void deleteFile(Path path) {
        try {
            Files.delete(path);
        } catch (IOException ignored) {

        }
    }

    public static void clearMemory() {
        File directory = ConstantController.ROOT_DIRECTORY.toFile();
        File[] files = directory.listFiles();

        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory() && file.getName().equals("temp")) {
                for (File insideFiles : Objects.requireNonNull(file.listFiles())) {
                    deleteFile(insideFiles.toPath());
                }

                deleteFile(file.toPath());
            }
        }
    }

    public static List<String> getDatFileNames() {
        File directory = new File(".");
        File[] files = directory.listFiles();
        List<String> fileNames = new ArrayList<>();

        if (files == null) return fileNames;

        for (File file : files) {
            String fileName = file.getName();

            if (file.isFile() && fileName.endsWith(FileType.DAT.extension)) {
                fileNames.add(fileName.substring(0, fileName.length() - 4));
            }
        }

        return fileNames;
    }

    public static boolean copyDatFilesWithHead(String path, String tableName, Path destinationDirectory) {
        try {
            boolean shouldReplaceFileName = !path.endsWith(String.format("%s%s", tableName, FileType.HEADER.extension));

            Path headFilePath = Path.of(path);

            if (shouldReplaceFileName) {
                String newHeadFileName = String.format("%s%s", tableName, FileType.HEADER.extension);
                Path newHeadFilePath = destinationDirectory.resolve(newHeadFileName);
                Files.copy(headFilePath, newHeadFilePath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Path destinationHeadFilePath = destinationDirectory.resolve(headFilePath.getFileName());
                Files.copy(headFilePath, destinationHeadFilePath, StandardCopyOption.REPLACE_EXISTING);
            }

            String newDatFileName = String.format("%s%s", tableName, FileType.DAT.extension);
            Path datFilePath = Path.of(path.replace(FileType.HEADER.extension, FileType.DAT.extension));

            if (shouldReplaceFileName) {
                Path newDatFilePath = destinationDirectory.resolve(newDatFileName);
                Files.copy(datFilePath, newDatFilePath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Path destinationDatFilePath = destinationDirectory.resolve(datFilePath.getFileName());
                Files.copy(datFilePath, destinationDatFilePath, StandardCopyOption.REPLACE_EXISTING);
            }

            return true;
        } catch (Exception exception) {
            return false;
        }
    }
    
    public static String getFileExtension(File file) {
        String fileName = file.getName();
        int lastDotIndex = fileName.lastIndexOf('.');
        
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return ""; // No extension found
    }
}
