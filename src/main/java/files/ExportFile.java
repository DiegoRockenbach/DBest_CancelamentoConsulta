package files;

import com.mxgraph.swing.mxGraphComponent;
import controllers.ConstantController;
import controllers.MainController;
import database.TableCreator;
import database.TuplesExtractor;
import static database.TuplesExtractor.getPossibleKeys;
import dsl.utils.DslUtils;
import engine.exceptions.DataBaseException;
import entities.Column;
import entities.Tree;
import entities.cells.Cell;
import entities.cells.TableCell;
import enums.ColumnDataType;
import enums.FileType;
import gui.frames.ErrorFrame;
import gui.frames.forms.importexport.ExportSQLScriptForm;
import gui.frames.main.MainFrame;
import ibd.query.Operation;
import ibd.query.Tuple;
import net.coobird.thumbnailator.Thumbnails;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class ExportFile extends JPanel {

    private final JFileChooser fileChooser = new JFileChooser();

    public ExportFile() {
        this.fileChooser.setDialogTitle(ConstantController.getString("exportFile.saveFile"));
        this.fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        this.fileChooser.setCurrentDirectory(MainController.getLastDirectory());
    }

    public void exportToMySQLScript(Cell cell) {
        String pathname = String.format("%s.sql", ConstantController.getString("file.tableFileName"));
        this.fileChooser.setSelectedFile(new File(pathname));

        int userSelection = this.fileChooser.showSaveDialog(null);

        if (userSelection != JFileChooser.APPROVE_OPTION) {
            return;
        }

        MainController.setLastDirectory(new File(this.fileChooser.getCurrentDirectory().getAbsolutePath()));

        File fileToSave = this.fileChooser.getSelectedFile();
        String filePath = fileToSave.getAbsolutePath();

        if (!filePath.endsWith(FileType.SQL.extension)) {
            filePath = String.format("%s.sql", filePath);
            fileToSave = new File(filePath);
        }

        if (fileToSave.exists()) {
            int selectedOption = JOptionPane.showConfirmDialog(
                    null, ConstantController.getString("file.substitution"),
                    ConstantController.getString("file.substitutionConfirmation"), JOptionPane.YES_NO_OPTION
            );

            if (selectedOption == JOptionPane.NO_OPTION) {
                this.exportToMySQLScript(cell);
                return;
            }
        }

        AtomicReference<Boolean> exitReference = new AtomicReference<>(false);

        String text = String.format("%s: ", ConstantController.getString("exportFile.createNewDatabase"));

        ExportSQLScriptForm.SQLScriptInf inf = new ExportSQLScriptForm.SQLScriptInf(
                new StringBuilder(), new StringBuilder(), new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new JCheckBox(text), new StringBuilder(), new Vector<>(), new Vector<>()
        );

        new ExportSQLScriptForm(cell, inf, exitReference);

        if (exitReference.get()) {
            return;
        }

        try {
            FileWriter sql = new FileWriter(fileToSave);

            StringBuilder sqlContent = new StringBuilder();

            if (inf.checkBoxCreateDatabase().isSelected()) {
                sqlContent.append(String.format("DROP DATABASE IF EXISTS %s;%n%n", inf.databaseName()));
                sqlContent.append(String.format("CREATE DATABASE IF NOT EXISTS %s;%n%n", inf.databaseName()));
            }

            sqlContent.append(String.format("USE %s;%n%n", inf.databaseName()));
            sqlContent.append(String.format("DROP TABLE IF EXISTS %s;%n%n", inf.tableName()));
            sqlContent.append(String.format("CREATE TABLE IF NOT EXISTS %s (%n", inf.tableName()));

            for (String columnName : inf.columnNames().subList(0, inf.columnNames().size() - 1)) {
                if (!inf.columnNames().get(0).equals(columnName)) {
                    sqlContent.append(",\n");
                }

                Column column = cell
                        .getColumns()
                        .stream()
                        .filter(c -> c.getSourceAndName().equals(columnName))
                        .findFirst()
                        .orElseThrow();

                String name = inf.newColumnNameTxtFields().get(columnName).getText();

                String type = switch (column.DATA_TYPE) {
                    case INTEGER, LONG ->
                        "INT";
                    case FLOAT ->
                        "FLOAT";
                    case DOUBLE ->
                        "DOUBLE";
                    case CHARACTER ->
                        "VARCHAR(1)";
                    case STRING, NONE ->
                        "TEXT";
                    case BOOLEAN ->
                        "BOOLEAN";
                };

                sqlContent.append(String.format("\t%s %s ", name, type));

                if (!inf.nullCheckBoxes().get(columnName).isSelected()) {
                    sqlContent.append("NOT ");
                }

                sqlContent.append("NULL");
            }

            int numberOfPrimaryKeys = inf
                    .pkCheckBoxes()
                    .values()
                    .stream()
                    .filter(AbstractButton::isSelected)
                    .toList()
                    .size();

            if (numberOfPrimaryKeys > 0) {
                sqlContent.append(",\n\n\tPRIMARY KEY (");

                for (Map.Entry<String, JCheckBox> primaryKey : inf.pkCheckBoxes().entrySet()) {
                    if (primaryKey.getValue().isSelected()) {
                        sqlContent.append(inf.newColumnNameTxtFields().get(primaryKey.getKey()).getText());

                        numberOfPrimaryKeys--;

                        sqlContent.append(numberOfPrimaryKeys != 0 ? ", " : ")");
                    }
                }
            }

            sqlContent.append("\n);\n\n");

            for (Vector<Object> row : inf.content()) {
                sqlContent.append(String.format("INSERT INTO %s VALUES (", inf.tableName()));

                for (int i = 0; i < row.size() - 1; i++) {
                    int finalI = i;

                    Vector<String> finalColumnNames = inf.columnNames();

                    ColumnDataType type = cell
                            .getColumns()
                            .stream()
                            .filter(c -> c.getSourceAndName().equals(finalColumnNames.get(finalI)))
                            .findFirst()
                            .orElseThrow().DATA_TYPE;

                    boolean isString = type == ColumnDataType.STRING || type == ColumnDataType.NONE || type == ColumnDataType.CHARACTER;

                    String data = Objects.toString(row.get(i)).replaceAll("'", "\\\\'");

                    if (isString && !data.equalsIgnoreCase("null")) {
                        data = String.format("'%s'", data);
                    }

                    sqlContent.append(data);

                    if (i != row.size() - 2) {
                        sqlContent.append(", ");
                    }
                }

                sqlContent.append(");\n");
            }

            if (!inf.additionalCommand().isEmpty()) {
                sqlContent.append(String.format("%n%n%s", inf.additionalCommand()));
            }

            sql.write(sqlContent.toString());

            sql.close();
        } catch (IOException exception) {
            new ErrorFrame(exception.getMessage());
        }
    }

    public void exportToFYI(Cell cell, List<Column> primaryKeyColumns, boolean unique) {
        if (primaryKeyColumns == null || primaryKeyColumns.isEmpty()) {
            return;
        }

        String pathname = String.format("%s%s", cell.getName(), FileType.HEADER.extension);

        this.fileChooser.setSelectedFile(new File(pathname));

        int userSelection = this.fileChooser.showSaveDialog(null);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            MainController.setLastDirectory(new File(this.fileChooser.getCurrentDirectory().getAbsolutePath()));
            File fileToSave = this.fileChooser.getSelectedFile();
            String filePath = fileToSave.getAbsolutePath();

            if (!filePath.endsWith(FileType.HEADER.extension)) {
                filePath += FileType.HEADER.extension;
                fileToSave = new File(filePath);
            }

            String headFileName = this.fileChooser.getSelectedFile().getName();
            String fileName = headFileName.endsWith(FileType.HEADER.extension) ? headFileName.substring(0, headFileName.indexOf(".")) : headFileName;

            if (fileToSave.exists()) {
                int result = JOptionPane.showConfirmDialog(null, ConstantController.getString("file.substitution"), ConstantController.getString("file.substitutionConfirmation"), JOptionPane.YES_NO_OPTION);

                if (result == JOptionPane.NO_OPTION) {
                    this.exportToFYI(cell, primaryKeyColumns, unique);
                    return;
                }
            }

            Map<Integer, Map<String, String>> rows = new HashMap<>();

            Operation operator = cell.getOperator();

            try {
                operator.open();
                
                int i = 0;
                while (operator.hasNext()) {
                    Tuple tuple = operator.next();
                    Map<String, String> row = TuplesExtractor.getRow_(tuple, operator, false);
                    if (row != null) {
                        rows.put(i++, row);
                    }
                }

                operator.close();
            } catch (Exception ex) {
            }

            AtomicReference<Boolean> exitReference = new AtomicReference<>(false);

            List<Column> columnsWithPrimaryKey = new ArrayList<>();

            for (Column pkColumns : primaryKeyColumns) {
                columnsWithPrimaryKey.add(new Column(cell.getColumns().stream()
                        .filter(c -> c.getSourceAndName()
                        .equalsIgnoreCase((primaryKeyColumns.stream()
                                .filter(x -> x.getSourceAndName()
                                .equalsIgnoreCase(pkColumns.getSourceAndName()))
                                .findFirst().orElseThrow()).getSourceAndName())).findFirst().orElseThrow(), true));
            }

            for (Column c : cell.getColumns()) {

                if (columnsWithPrimaryKey.stream().anyMatch(x -> x.getSourceAndName().equalsIgnoreCase(c.getSourceAndName()))) {
                    continue;
                }

                columnsWithPrimaryKey.add(new Column(c, false));

            }

            if (exitReference.get()) {
                return;
            }

            try {

                TableCell createdCell = TableCreator.createIndex(fileName, columnsWithPrimaryKey, rows, fileToSave, true, unique);
//                createdCell.getTable().saveHeader(headFileName);
//                createdCell.getTable().close();

            } catch (DataBaseException e) {

                new ErrorFrame(e.getMessage());
                return;
            }

//            Path headSourcePath = Paths.get(headFileName);
//            String datFileName = String.format("%s%s", fileName, FileType.FYI.extension);
//            Path datSourcePath = Paths.get(datFileName);
//
//            Path headDestinationPath = Paths.get(filePath);
//            Path datDestinationPath = Paths.get(filePath.replace(headFileName, datFileName));
//
//            try {
//                Files.move(headSourcePath, headDestinationPath, StandardCopyOption.REPLACE_EXISTING);
//                Files.move(datSourcePath, datDestinationPath, StandardCopyOption.REPLACE_EXISTING);
//            } catch (Exception exception) {
//                new ErrorFrame(exception.getMessage());
//            }
        }
    }

    
    public void exportToCSV(Cell cell) {
        try {
            String defaultFileName = String.format("%s%s", cell.getSources().stream().findFirst().orElse(null).getName(), FileType.CSV.extension);

            this.fileChooser.setSelectedFile(new File(defaultFileName));

            int userSelection = this.fileChooser.showSaveDialog(null);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                MainController.setLastDirectory(new File(this.fileChooser.getCurrentDirectory().getAbsolutePath()));

                File fileToSave = this.fileChooser.getSelectedFile();
                String filePath = fileToSave.getAbsolutePath();

                if (!filePath.endsWith(FileType.CSV.extension)) {
                    filePath = String.format("%s%s", filePath, FileType.CSV.extension);
                    fileToSave = new File(filePath);
                }

                if (fileToSave.exists()) {
                    int result = JOptionPane.showConfirmDialog(null, ConstantController.getString("file.substitution"), ConstantController.getString("file.substitutionConfirmation"), JOptionPane.YES_NO_OPTION);
                    if (result == JOptionPane.NO_OPTION) {
                        this.exportToCSV(cell);
                        return;
                    }
                }

                FileWriter csv = new FileWriter(fileToSave);

                boolean columnsPut = false;

                cell.getOperator().open();
                Tuple tuple = cell.getOperator().next();

                //Map<String, String> row = TuplesExtractor.getRow_(this.cell.getOperator(), true);
                while (tuple != null) {

                    Map<String, String> row = TuplesExtractor.getRow_(tuple, cell.getOperator(), true);

                    if (!columnsPut) {
                        boolean repeatedColumnName = false;

                        Set<String> columnNames = new HashSet<>();

                        for (String column : row.keySet()) {
                            if (!columnNames.add(Column.removeSource(column))) {
                                repeatedColumnName = true;
                            }
                        }

                        int i = 0;

                        for (String inf : row.keySet()) {
                            if (i++ != 0) {
                                csv.write(",");
                            }

                            String columnName = repeatedColumnName ? inf : Column.removeSource(inf);
                            csv.write(columnName);
                        }

                        csv.write("\n");
                        columnsPut = true;

                        

                    }

                    int i = 0;

                    for (String inf : row.values()) {
                        if (i++ != 0) {
                            csv.write(",");
                        }

                        csv.write(inf);
                    }

                    csv.write("\n");
                    tuple = cell.getOperator().next();
                }
                cell.getOperator().close();
                csv.close();
            }
        } catch (Exception exception) {
            new ErrorFrame(exception.getMessage());
        }
    }
    
    private void exportToImage() {
        try {
            mxGraphComponent component = MainFrame.getGraphComponent();

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle(ConstantController.getString("exportFile.saveImage"));
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

            String defaultFileName = String.format("%s.jpeg", ConstantController.getString("file.treeFileName"));
            fileChooser.setSelectedFile(new File(defaultFileName));

            int userSelection = fileChooser.showSaveDialog(component);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                String path = fileToSave.getPath();

                if (!path.toLowerCase().endsWith(".jpeg") && !path.toLowerCase().endsWith(".jpg")) {
                    path = String.format("%s.jpeg", path);
                }

                Dimension size = component.getGraphControl().getSize();
                BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = image.createGraphics();

                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, size.width, size.height);

                component.getGraphControl().paint(g2d);

                g2d.dispose();

                Thumbnails.of(image).size(size.width, size.height).outputQuality(1.0f).toFile(new File(path));
            }
        } catch (IOException exception) {
            System.out.printf(String.format("%s: %s", ConstantController.getString("file.error.toSave"), exception.getMessage()));
        }
    }

    public void exportToDsl(Tree tree) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(ConstantController.getString("exportFile.saveTree"));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        String defaultFileName = String.format("%s.txt", ConstantController.getString("file.treeFileName"));
        fileChooser.setSelectedFile(new File(defaultFileName));

        if (fileChooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        String filePath = fileChooser.getSelectedFile().getPath();

        if (!filePath.toLowerCase().endsWith(".txt")) {
            filePath = String.format("%s.txt", filePath);
        }

        BufferedWriter writer = null;

        try {
            writer = new BufferedWriter(new FileWriter(filePath));
            writer.write(DslUtils.generateDslTree(tree));
        } catch (IOException exception) {
            System.out.printf(String.format("%s: %s", ConstantController.getString("file.error.toSave"), exception.getMessage()));
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException exception) {
                System.out.printf(String.format("%s: %s", ConstantController.getString("file.error.toSave"), exception.getMessage()));
            }
        }

        final String finalPath = filePath.substring(0, filePath.lastIndexOf("/") + 1);

        tree.getLeaves().forEach(table -> {
            String tableName = table.getName();
            
            FileUtils.copyDatFilesWithHead(String.format("%s%s", tableName, FileType.HEADER.extension), tableName, Path.of(finalPath));
        });
    }
    
    /**
     * Exports a subtree as an image using SVG-like rendering
     * This method provides an alternative graphical representation of the tree structure
     */
    public void exportSubtreeToImage(Tree tree, Cell rootCell) {
        JFileChooser imageFileChooser = new JFileChooser();
        imageFileChooser.setDialogTitle("Save Subtree as Image");
        imageFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        imageFileChooser.setCurrentDirectory(MainController.getLastDirectory());

        String defaultFileName = String.format("subtree_%s.png", 
            rootCell.getName().replaceAll("[^a-zA-Z0-9._-]", "_"));
        imageFileChooser.setSelectedFile(new File(defaultFileName));

        // Set file filter for PNG images
        javax.swing.filechooser.FileNameExtensionFilter filter = 
            new javax.swing.filechooser.FileNameExtensionFilter("PNG Images", "png");
        imageFileChooser.setFileFilter(filter);

        if (imageFileChooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        String filePath = imageFileChooser.getSelectedFile().getPath();

        if (!filePath.toLowerCase().endsWith(".png")) {
            filePath = String.format("%s.png", filePath);
        }

        try {
            MainController.setLastDirectory(new File(imageFileChooser.getCurrentDirectory().getAbsolutePath()));
            createTreeImage(tree, rootCell, filePath);
            
            JOptionPane.showMessageDialog(null, 
                "Subtree image saved successfully!", 
                "Success", 
                JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException exception) {
            System.out.printf(String.format("%s: %s", 
                ConstantController.getString("file.error.toSave"), exception.getMessage()));
            JOptionPane.showMessageDialog(null, 
                "Error saving image: " + exception.getMessage(), 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Creates a visual representation of the tree structure as an image
     */
    private void createTreeImage(Tree tree, Cell rootCell, String filePath) throws IOException {
        // Get the subtree cells starting from rootCell
        Set<Cell> subtreeCells = getSubtreeCells(rootCell);
        
        if (subtreeCells.isEmpty()) {
            throw new IOException("No cells found in subtree");
        }
        
        // Calculate layout
        Map<Cell, Point> cellPositions = calculateTreeLayout(subtreeCells, rootCell);
        
        // Determine image size
        Rectangle bounds = calculateImageBounds(cellPositions);
        int padding = 50;
        int imageWidth = bounds.width + (padding * 2);
        int imageHeight = bounds.height + (padding * 2);
        
        // Create the image
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        // Set rendering hints for better quality
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        // Fill background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, imageWidth, imageHeight);
        
        // Offset for padding
        int offsetX = padding - bounds.x;
        int offsetY = padding - bounds.y;
        
        // Draw edges first (so they appear behind nodes)
        drawTreeEdges(g2d, subtreeCells, cellPositions, offsetX, offsetY);
        
        // Draw nodes
        drawTreeNodes(g2d, subtreeCells, cellPositions, offsetX, offsetY);
        
        g2d.dispose();
        
        // Save the image
        javax.imageio.ImageIO.write(image, "PNG", new File(filePath));
    }
    
    // Pega as células do subárvore a partir de "rootCell"
    private Set<Cell> getSubtreeCells(Cell rootCell) {
        Set<Cell> subtreeCells = new HashSet<>();
        Queue<Cell> cellsToProcess = new LinkedList<>();
        
        cellsToProcess.offer(rootCell);
        
        while (!cellsToProcess.isEmpty()) {
            Cell currentCell = cellsToProcess.poll();
            if (subtreeCells.add(currentCell)) {
                // Add all parent cells to the queue for processing
                cellsToProcess.addAll(currentCell.getParents());
            }
        }
        
        return subtreeCells;
    }
    
    /**
     * Calcula a posição de cada célula na árvore para o layout
     */
    private Map<Cell, Point> calculateTreeLayout(Set<Cell> cells, Cell rootCell) {
        Map<Cell, Point> positions = new HashMap<>();
        Map<Cell, Integer> levels = new HashMap<>();
        
        // Calcula a profundidade de cada célula
        calculateCellLevels(rootCell, 0, levels, new HashSet<>());
        
        // Agrupa  por profundidade
        Map<Integer, List<Cell>> cellsByLevel = new HashMap<>();
        for (Cell cell : cells) {
            int level = levels.getOrDefault(cell, 0);
            cellsByLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(cell);
        }
        
        // Layout cells with increased spacing for detailed information
        int nodeWidth = 220; // Significantly increased width to accommodate join conditions
        int nodeHeight = 100; // Increased height for join conditions
        int horizontalSpacing = 60; // More horizontal spacing
        int verticalSpacing = 120; // More vertical spacing
        
        // Start with the root cell
        positions.put(rootCell, new Point(0, 0));
        
        // Process each level, positioning cells relative to their children
        for (int level = 1; level <= levels.values().stream().mapToInt(Integer::intValue).max().orElse(0); level++) {
            List<Cell> levelCells = cellsByLevel.get(level);
            if (levelCells == null) continue;
            
            // Group cells by their children to position LEFT/RIGHT correctly
            Map<Cell, List<Cell>> childToParents = new HashMap<>();
            for (Cell cell : levelCells) {
                for (Cell child : cells) {
                    if (child.getParents().contains(cell)) {
                        childToParents.computeIfAbsent(child, k -> new ArrayList<>()).add(cell);
                    }
                }
            }
            
            // Track positions to avoid overlaps
            Set<Point> usedPositions = new HashSet<>();
            Map<Cell, Point> tempPositions = new HashMap<>();
            
            // Position cells based on their children's positions
            for (Map.Entry<Cell, List<Cell>> entry : childToParents.entrySet()) {
                Cell child = entry.getKey();
                List<Cell> parents = entry.getValue();
                Point childPos = positions.get(child);
                
                if (childPos != null && parents.size() == 2) {
                    // For binary operations, position LEFT parent to the left, RIGHT parent to the right
                    Cell leftParent = parents.get(0);  // First parent is LEFT
                    Cell rightParent = parents.get(1); // Second parent is RIGHT
                    
                    int childX = childPos.x;
                    int parentY = level * (nodeHeight + verticalSpacing);
                    
                    // Position LEFT parent to the left of the child
                    int leftX = childX - nodeWidth - horizontalSpacing/2;
                    Point leftPos = new Point(leftX, parentY);
                    
                    // Position RIGHT parent to the right of the child
                    int rightX = childX + nodeWidth + horizontalSpacing/2;
                    Point rightPos = new Point(rightX, parentY);
                    
                    // Check for conflicts and resolve them
                    leftPos = resolvePositionConflict(leftParent, leftPos, tempPositions, usedPositions, nodeWidth, horizontalSpacing);
                    rightPos = resolvePositionConflict(rightParent, rightPos, tempPositions, usedPositions, nodeWidth, horizontalSpacing);
                    
                    tempPositions.put(leftParent, leftPos);
                    tempPositions.put(rightParent, rightPos);
                    usedPositions.add(leftPos);
                    usedPositions.add(rightPos);
                    
                } else if (childPos != null && parents.size() == 1) {
                    // For unary operations, center the parent above the child
                    Cell parent = parents.get(0);
                    int parentX = childPos.x;
                    int parentY = level * (nodeHeight + verticalSpacing);
                    Point parentPos = new Point(parentX, parentY);
                    
                    // Check for conflicts and resolve them
                    parentPos = resolvePositionConflict(parent, parentPos, tempPositions, usedPositions, nodeWidth, horizontalSpacing);
                    
                    tempPositions.put(parent, parentPos);
                    usedPositions.add(parentPos);
                }
            }
            
            // Add temporary positions to main positions map
            positions.putAll(tempPositions);
            
            // Handle cells that don't have children (shouldn't happen in a proper tree, but just in case)
            for (Cell cell : levelCells) {
                if (!positions.containsKey(cell)) {
                    // Position orphaned cells in sequence
                    int orphanX = levelCells.indexOf(cell) * (nodeWidth + horizontalSpacing);
                    int orphanY = level * (nodeHeight + verticalSpacing);
                    positions.put(cell, new Point(orphanX, orphanY));
                }
            }
        }
        
        return positions;
    }
    
    /**
     * Resolves position conflicts for shared nodes by finding a non-overlapping position
     */
    private Point resolvePositionConflict(Cell cell, Point proposedPos, Map<Cell, Point> tempPositions, 
                                        Set<Point> usedPositions, int nodeWidth, int horizontalSpacing) {
        // If the cell already has a position assigned, return it to maintain consistency
        if (tempPositions.containsKey(cell)) {
            return tempPositions.get(cell);
        }
        
        // Check if the proposed position overlaps with any existing position
        Point finalPos = new Point(proposedPos);
        
        boolean hasOverlap = true;
        int attempts = 0;
        int maxAttempts = 20; // Prevent infinite loops
        
        while (hasOverlap && attempts < maxAttempts) {
            hasOverlap = false;
            
            // Check for overlaps with all used positions
            for (Point usedPos : usedPositions) {
                if (positionsOverlap(finalPos, usedPos, nodeWidth, horizontalSpacing)) {
                    hasOverlap = true;
                    break;
                }
            }
            
            if (hasOverlap) {
                // Try moving the position to the right to avoid overlap
                finalPos.x += (nodeWidth + horizontalSpacing);
                attempts++;
            }
        }
        
        return finalPos;
    }
    
    /**
     * Checks if two positions would result in overlapping nodes
     */
    private boolean positionsOverlap(Point pos1, Point pos2, int nodeWidth, int horizontalSpacing) {
        // Calculate the minimum distance needed between nodes to avoid overlap
        int minDistance = nodeWidth + horizontalSpacing / 2;
        
        // Check if positions are on the same level (same Y coordinate)
        if (pos1.y == pos2.y) {
            // Check if horizontal distance is less than minimum required
            int horizontalDistance = Math.abs(pos1.x - pos2.x);
            return horizontalDistance < minDistance;
        }
        
        return false; // No overlap if not on the same level
    }
    
    /**
     * Calculates the level (depth) of each cell in the tree
     */
    private void calculateCellLevels(Cell cell, int level, Map<Cell, Integer> levels, Set<Cell> visited) {
        if (visited.contains(cell)) {
            return;
        }
        
        visited.add(cell);
        levels.put(cell, level);
        
        for (Cell parent : cell.getParents()) {
            calculateCellLevels(parent, level + 1, levels, visited);
        }
    }
    
    /**
     * Calculates the bounding rectangle for the tree layout
     */
    private Rectangle calculateImageBounds(Map<Cell, Point> positions) {
        if (positions.isEmpty()) {
            return new Rectangle(0, 0, 600, 400);
        }
        
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        
        int nodeWidth = 220; // Updated to match layout
        int baseNodeHeight = 100; // Base node height
        
        for (Map.Entry<Cell, Point> entry : positions.entrySet()) {
            Cell cell = entry.getKey();
            Point pos = entry.getValue();
            
            // Calculate actual height for this cell
            int actualHeight = calculateActualNodeHeight(cell, baseNodeHeight);
            
            minX = Math.min(minX, pos.x);
            minY = Math.min(minY, pos.y);
            maxX = Math.max(maxX, pos.x + nodeWidth);
            maxY = Math.max(maxY, pos.y + actualHeight);
        }
        
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }
    
    /**
     * Draws the edges between cells
     */
    private void drawTreeEdges(Graphics2D g2d, Set<Cell> cells, Map<Cell, Point> positions, int offsetX, int offsetY) {
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2));

        int nodeWidth = 220; // Updated to match layout
        int baseNodeHeight = 100; // Base node height
        
        for (Cell cell : cells) {
            Point cellPos = positions.get(cell);
            if (cellPos == null) continue;
            
            // Calculate actual node height for the current cell
            int actualCellHeight = calculateActualNodeHeight(cell, baseNodeHeight);
            
            int cellCenterX = cellPos.x + offsetX + nodeWidth / 2;
            int cellBottomY = cellPos.y + offsetY + actualCellHeight;
            
            List<Cell> parents = cell.getParents();
            for (int i = 0; i < parents.size(); i++) {
                Cell parent = parents.get(i);
                if (cells.contains(parent)) {
                    Point parentPos = positions.get(parent);
                    if (parentPos != null) {
                        int parentCenterX = parentPos.x + offsetX + nodeWidth / 2;
                        int parentTopY = parentPos.y + offsetY;
                        
                        // Draw line from cell bottom to parent top
                        g2d.drawLine(cellCenterX, cellBottomY, parentCenterX, parentTopY);
                        
                        // Draw arrow head
                        drawArrowHead(g2d, cellCenterX, cellBottomY, parentCenterX, parentTopY);
                        
                        // Add LEFT/RIGHT labels for binary operations
                        if (parents.size() == 2 && cell instanceof entities.cells.OperationCell) {
                            g2d.setFont(new Font("Arial", Font.BOLD, 11));
                            String label = (i == 0) ? "LEFT" : "RIGHT";
                            
                            // Calculate the exact middle point of the arrow
                            int midX = (cellCenterX + parentCenterX) / 2;
                            int midY = (cellBottomY + parentTopY) / 2;
                            
                            // Calculate text dimensions for proper centering
                            FontMetrics fm = g2d.getFontMetrics();
                            int textWidth = fm.stringWidth(label);
                            int textHeight = fm.getHeight();
                            int textAscent = fm.getAscent();
                            
                            // Position the label exactly at the center of the arrow
                            int labelX = midX - textWidth / 2;
                            int labelY = midY + textAscent / 2;
                            
                            // Draw a small white background with black border for readability
                            int padding = 2;
                            int bgX = labelX - padding;
                            int bgY = labelY - textAscent;
                            int bgWidth = textWidth + (2 * padding);
                            int bgHeight = textHeight;
                            
                            g2d.setColor(Color.WHITE);
                            g2d.fillRect(bgX, bgY, bgWidth, bgHeight);
                            g2d.setColor(Color.BLACK);
                            g2d.drawRect(bgX, bgY, bgWidth, bgHeight);
                            
                            // Draw the label in blue
                            g2d.setColor(Color.BLUE);
                            g2d.drawString(label, labelX, labelY);
                            
                            g2d.setColor(Color.BLACK); // Reset color
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Calculates the actual node height based on content
     */
    private int calculateActualNodeHeight(Cell cell, int baseHeight) {
        if (cell instanceof entities.cells.OperationCell) {
            entities.cells.OperationCell opCell = (entities.cells.OperationCell) cell;
            if (opCell.getArguments() != null && !opCell.getArguments().isEmpty()) {
                // Calculate needed height based on arguments
                int extraHeight = 0;
                for (String arg : opCell.getArguments()) {
                    if (arg != null && !arg.trim().isEmpty()) {
                        // Estimate lines needed for this argument
                        int estimatedLines = Math.max(1, arg.length() / 25);
                        extraHeight += estimatedLines * 15; // More space per line
                    }
                }
                return Math.max(baseHeight, baseHeight + extraHeight + 20); // Extra padding
            }
        }
        return baseHeight;
    }
    
    /**
     * Draws the tree nodes
     */
    private void drawTreeNodes(Graphics2D g2d, Set<Cell> cells, Map<Cell, Point> positions, int offsetX, int offsetY) {
        int nodeWidth = 220; // Updated to match layout
        int nodeHeight = 100; // Updated to match layout
        
        for (Cell cell : cells) {
            Point pos = positions.get(cell);
            if (pos == null) continue;
            
            int x = pos.x + offsetX;
            int y = pos.y + offsetY;
            
            // Adjust node height for operation cells with arguments
            int actualNodeHeight = nodeHeight;
            if (cell instanceof entities.cells.OperationCell) {
                entities.cells.OperationCell opCell = (entities.cells.OperationCell) cell;
                if (opCell.getArguments() != null && !opCell.getArguments().isEmpty()) {
                    // Calculate needed height based on arguments
                    int extraHeight = 0;
                    for (String arg : opCell.getArguments()) {
                        if (arg != null && !arg.trim().isEmpty()) {
                            // Estimate lines needed for this argument
                            int estimatedLines = Math.max(1, arg.length() / 25);
                            extraHeight += estimatedLines * 15; // More space per line
                        }
                    }
                    actualNodeHeight = Math.max(nodeHeight, nodeHeight + extraHeight + 20); // Extra padding
                }
            }
            
            // Determine cell color based on type
            Color bgColor = getCellColor(cell);
            
            // Draw cell background
            g2d.setColor(bgColor);
            g2d.fillRoundRect(x, y, nodeWidth, actualNodeHeight, 10, 10);
            
            // Draw cell border
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawRoundRect(x, y, nodeWidth, actualNodeHeight, 10, 10);
            
            // Draw cell content
            g2d.setColor(Color.BLACK);
            
            if (cell instanceof entities.cells.OperationCell) {
                drawOperationCellContent(g2d, (entities.cells.OperationCell) cell, x, y, nodeWidth, actualNodeHeight);
            } else {
                drawTableCellContent(g2d, cell, x, y, nodeWidth, actualNodeHeight);
            }
        }
    }
    
    /**
     * Draws content for operation cells with detailed information
     */
    private void drawOperationCellContent(Graphics2D g2d, entities.cells.OperationCell opCell, int x, int y, int width, int height) {
        // Draw operation name
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        String cellName = opCell.getName();
        if (cellName.length() > 25) {
            cellName = cellName.substring(0, 22) + "...";
        }
        
        FontMetrics fm = g2d.getFontMetrics();
        int textX = x + (width - fm.stringWidth(cellName)) / 2;
        g2d.drawString(cellName, textX, y + 18);
        
        // Draw operation symbol
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        String opSymbol = opCell.getType().symbol;
        fm = g2d.getFontMetrics();
        textX = x + (width - fm.stringWidth(opSymbol)) / 2;
        g2d.drawString(opSymbol, textX, y + 35);
        
        // Draw operation type name
        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
        String opTypeName = getOperationDisplayName(opCell.getType());
        fm = g2d.getFontMetrics();
        textX = x + (width - fm.stringWidth(opTypeName)) / 2;
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawString(opTypeName, textX, y + 50);
        g2d.setColor(Color.BLACK);
        
        // Draw join conditions or other arguments with better visibility
        if (opCell.getArguments() != null && !opCell.getArguments().isEmpty()) {
            g2d.setFont(new Font("Arial", Font.BOLD, 10)); // Made bold and larger
            fm = g2d.getFontMetrics();
            
            List<String> args = opCell.getArguments();
            int currentY = y + 68;
            
            for (String arg : args) {
                if (arg != null && !arg.trim().isEmpty()) {
                    // Format join conditions nicely
                    String displayArg = formatJoinCondition(arg);
                    
                    // Split long conditions into multiple lines if needed
                    String[] lines = splitLongText(displayArg, width - 20, fm); // More padding
                    
                    for (String line : lines) {
                        if (currentY < y + height - 5) {
                            textX = x + (width - fm.stringWidth(line)) / 2;
                            g2d.setColor(new Color(0, 80, 0)); // Darker green for better visibility
                            g2d.drawString(line, textX, currentY);
                            currentY += 13; // More space between lines
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Draws content for table cells
     */
    private void drawTableCellContent(Graphics2D g2d, Cell cell, int x, int y, int width, int height) {
        // Draw table name
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        String cellName = cell.getName();
        if (cellName.length() > 15) {
            cellName = cellName.substring(0, 12) + "...";
        }
        
        FontMetrics fm = g2d.getFontMetrics();
        int textX = x + (width - fm.stringWidth(cellName)) / 2;
        int textY = y + (height + fm.getHeight()) / 2 - fm.getDescent();
        
        g2d.drawString(cellName, textX, textY);
        
        // Draw table indicator
        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
        String tableInfo = "TABLE";
        fm = g2d.getFontMetrics();
        textX = x + (width - fm.stringWidth(tableInfo)) / 2;
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawString(tableInfo, textX, textY + 15);
    }
    
    /**
     * Gets a user-friendly display name for operation types
     */
    private String getOperationDisplayName(enums.OperationType operationType) {
        String name = operationType.name();
        if (name.contains("JOIN")) {
            if (name.contains("LEFT_OUTER")) return "Left Outer Join";
            if (name.contains("RIGHT_OUTER")) return "Right Outer Join";
            if (name.contains("FULL_OUTER")) return "Full Outer Join";
            if (name.contains("LEFT_SEMI")) return "Left Semi Join";
            if (name.contains("RIGHT_SEMI")) return "Right Semi Join";
            if (name.contains("LEFT_ANTI")) return "Left Anti Join";
            if (name.contains("RIGHT_ANTI")) return "Right Anti Join";
            if (name.contains("NESTED_LOOP")) return "NL Join";
            if (name.contains("HASH")) return "Hash Join";
            if (name.contains("MERGE")) return "Merge Join";
            return "Join";
        }
        
        return operationType.displayName;
    }
    
    /**
     * Formats join conditions to be more readable
     */
    private String formatJoinCondition(String condition) {
        if (condition.contains("=")) {
            String[] parts = condition.split("=");
            if (parts.length == 2) {
                String left = parts[0].trim();
                String right = parts[1].trim();
                return left + " = " + right;
            }
        }
        return condition;
    }
    
    /**
     * Splits long text into multiple lines that fit within the given width
     */
    private String[] splitLongText(String text, int maxWidth, FontMetrics fm) {
        if (fm.stringWidth(text) <= maxWidth) {
            return new String[]{text};
        }
        
        List<String> lines = new ArrayList<>();
        String remaining = text;
        
        while (fm.stringWidth(remaining) > maxWidth && remaining.length() > 0) {
            // Calculate approximate character count that fits
            int avgCharWidth = fm.charWidth('A'); // Use 'A' as average character width
            int maxChars = Math.max(1, maxWidth / avgCharWidth);
            int splitPoint = Math.min(maxChars, remaining.length());
            
            // Look for good split points in join conditions
            for (int i = splitPoint - 1; i > Math.max(1, splitPoint / 3) && i >= 0; i--) {
                char c = remaining.charAt(i);
                if (c == '=' || c == '.' || c == ' ' || c == ',' || c == '&') {
                    splitPoint = (c == '=' || c == '&') ? i + 1 : i; // Include operators
                    break;
                }
            }
            
            if (splitPoint <= 0) splitPoint = 1; // Prevent infinite loop
            
            String line = remaining.substring(0, splitPoint).trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
            remaining = remaining.substring(splitPoint).trim();
        }
        
        if (!remaining.isEmpty()) {
            lines.add(remaining);
        }
        
        return lines.toArray(new String[0]);
    }
    
    /**
     * Gets the appropriate color for a cell based on its type
     */
    private Color getCellColor(Cell cell) {
        if (cell instanceof entities.cells.TableCell) {
            return new Color(173, 216, 230); // Light blue
        } else if (cell instanceof entities.cells.OperationCell) {
            entities.cells.OperationCell opCell = (entities.cells.OperationCell) cell;
            String operationType = opCell.getType().toString();
            
            if (operationType.contains("JOIN")) {
                return new Color(255, 182, 193); // Light pink
            } else if (operationType.contains("AGGREGATION") || operationType.contains("GROUP")) {
                return new Color(152, 251, 152); // Light green
            } else if (operationType.contains("FILTER") || operationType.contains("PROJECTION")) {
                return new Color(255, 255, 224); // Light yellow
            } else if (operationType.contains("SORT")) {
                return new Color(221, 160, 221); // Plum
            } else {
                return new Color(255, 218, 185); // Peach
            }
        }
        
        return Color.LIGHT_GRAY;
    }
    
    /**
     * Draws an arrow head at the end of a line
     */
    private void drawArrowHead(Graphics2D g2d, int x1, int y1, int x2, int y2) {
        double angle = Math.atan2(y2 - y1, x2 - x1);
        
        int arrowLength = 8;
        double arrowAngle = Math.PI / 6;
        
        int arrowX1 = (int) (x2 - arrowLength * Math.cos(angle - arrowAngle));
        int arrowY1 = (int) (y2 - arrowLength * Math.sin(angle - arrowAngle));
        
        int arrowX2 = (int) (x2 - arrowLength * Math.cos(angle + arrowAngle));
        int arrowY2 = (int) (y2 - arrowLength * Math.sin(angle + arrowAngle));
        
        g2d.drawLine(x2, y2, arrowX1, arrowY1);
        g2d.drawLine(x2, y2, arrowX2, arrowY2);
    }
}
