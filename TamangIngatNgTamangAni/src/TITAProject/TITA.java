package TITAProject;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.PriorityQueue;
import java.util.Set;

public class TITA extends JFrame {

    private CardLayout cardLayout;
    private JPanel mainPanel;

    private ArrayList<Batch> batches;
    private ArrayList<StorageUnit> storageUnits;

    private DefaultTableModel inventoryModel;
    private JTable inventoryTable;
    private DefaultTableModel shelfModel;
    private JTable shelfTable;
    private DefaultTableModel historyModel;
    private JTable historyTable;
    private DefaultTableModel warningModel;
    private JScrollPane warningScrollPane;

    private JLabel totalGrainLabel;
    private JLabel totalBatchLabel;
    private JLabel expiringLabel;

    private JPanel warehouseContainerPanel;

    private JLabel totalSacksLabel;
    private UI.Card expiringCard;
    private JPanel stockPanel;
    private JPanel warningCardPanel;
    private CardLayout warningCards;

    private static final String DATA_FILE = "batches.dat";

    private int wh1Counter = 1;
    private int wh2Counter = 1;
    private int wh3Counter = 1;

    public TITA() {
        UI.install();
        setIconImage(UI.appIcon());

        setTitle("TITA - Grain Storage Management System (FEFO Enabled)");
        setSize(1280, 800);
        setMinimumSize(new Dimension(1000, 660));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(UI.BG);

        batches = new ArrayList<Batch>();
        storageUnits = new ArrayList<StorageUnit>();

        storageUnits.add(new StorageUnit("Warehouse 1"));
        storageUnits.add(new StorageUnit("Warehouse 2"));
        storageUnits.add(new StorageUnit("Warehouse 3"));

        loadData();

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.setBackground(UI.BG);

        mainPanel.add(createDashboard(), "Dashboard");
        mainPanel.add(createInventory(), "Inventory");
        mainPanel.add(createAddBatch(), "Add Batch");
        mainPanel.add(createWarehouses(), "Warehouses");
        mainPanel.add(createShelfLife(), "Shelf-life");
        mainPanel.add(createHistory(), "History");

        // Top Navigation Bar
        add(createNavigation(), BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);

        updateDashboard();
        updateWarnings();

        showPage("Dashboard");
    }

    private void restartApplication() {
        this.dispose();
        SwingUtilities.invokeLater(() -> {
            TITA newWindow = new TITA();
            newWindow.setVisible(true);
        });
    }

    private String getWarehouseCode(String warehouseName) {
        if (warehouseName.equalsIgnoreCase("Warehouse 1")) return "A";
        if (warehouseName.equalsIgnoreCase("Warehouse 2")) return "B";
        if (warehouseName.equalsIgnoreCase("Warehouse 3")) return "C";
        return "X";
    }

    private String getGrainCode(String grainType) {
        if (grainType.startsWith("Rice")) return "R";
        if (grainType.equalsIgnoreCase("Corn")) return "C";
        if (grainType.equalsIgnoreCase("Mungbean")) return "M";
        return "G";
    }

    private String generateBatchId(String warehouseName, String grainType) {
        String warehouseCode = getWarehouseCode(warehouseName);
        String grainCode = getGrainCode(grainType);

        int currentNum = 1;
        if (warehouseName.equalsIgnoreCase("Warehouse 1")) {
            currentNum = wh1Counter++;
        } else if (warehouseName.equalsIgnoreCase("Warehouse 2")) {
            currentNum = wh2Counter++;
        } else if (warehouseName.equalsIgnoreCase("Warehouse 3")) {
            currentNum = wh3Counter++;
        }

        return String.format("%s%s%04d", warehouseCode, grainCode, currentNum);
    }

    private String peekBatchId(String warehouseName, String grainType) {
        int next = 1;
        if (warehouseName.equalsIgnoreCase("Warehouse 1")) next = wh1Counter;
        else if (warehouseName.equalsIgnoreCase("Warehouse 2")) next = wh2Counter;
        else if (warehouseName.equalsIgnoreCase("Warehouse 3")) next = wh3Counter;
        return String.format("%s%s%04d", getWarehouseCode(warehouseName), getGrainCode(grainType), next);
    }

    private JPanel previewRow(String name, JLabel value) {
        JPanel row = UI.transparent(new BorderLayout(0, 3));
        row.add(UI.label(name, 12.5f, UI.MUTED, true), BorderLayout.NORTH);
        value.setFont(UI.font(Font.BOLD, 17));
        value.setForeground(UI.INK);
        row.add(value, BorderLayout.CENTER);
        return row;
    }

    private static class NumberFilter extends DocumentFilter {
        private final boolean allowDecimal;

        public NumberFilter(boolean allowDecimal) {
            this.allowDecimal = allowDecimal;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (string == null) return;
            if (isValidInput(fb.getDocument().getText(0, fb.getDocument().getLength()), string, offset)) {
                super.insertString(fb, offset, string, attr);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (text == null) return;
            if (isValidInput(fb.getDocument().getText(0, fb.getDocument().getLength()), text, offset)) {
                super.replace(fb, offset, length, text, attrs);
            }
        }

        private boolean isValidInput(String currentText, String newText, int offset) {
            StringBuilder builder = new StringBuilder(currentText);
            builder.insert(offset, newText);
            String fullText = builder.toString();

            if (allowDecimal) {
                return fullText.matches("^\\d*\\.?\\d*$");
            } else {
                return fullText.matches("^\\d*$");
            }
        }
    }

    private void saveData() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(batches);
        } catch (IOException e) {
            System.err.println("Failed to save inventory data: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadData() {
        File file = new File(DATA_FILE);
        if (!file.exists()) {
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            batches = (ArrayList<Batch>) ois.readObject();

            int maxWh1 = 0, maxWh2 = 0, maxWh3 = 0;

            for (Batch batch : batches) {
                if (batch.getStorageUnit() != null && batch.getStorageUnit().toLowerCase().contains("silo")) {
                    batch.setStorageUnit(batch.getStorageUnit().replaceAll("(?i)Silo", "Warehouse"));
                }

                StorageUnit warehouse = findStorageUnit(batch.getStorageUnit());
                if (warehouse != null) {
                    warehouse.getGrainQueue().addBatch(batch);
                }

                String id = batch.getId();
                if (id != null && id.length() >= 6) {
                    try {
                        int num = Integer.parseInt(id.substring(2));
                        if (id.startsWith("A")) {
                            if (num > maxWh1) maxWh1 = num;
                        } else if (id.startsWith("B")) {
                            if (num > maxWh2) maxWh2 = num;
                        } else if (id.startsWith("C")) {
                            if (num > maxWh3) maxWh3 = num;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            wh1Counter = maxWh1 + 1;
            wh2Counter = maxWh2 + 1;
            wh3Counter = maxWh3 + 1;

        } catch (Exception e) {
            System.err.println("Failed to load inventory data: " + e.getMessage());
        }
    }

    private void clearAllInventory() {
        int confirm = UI.showConfirm(
                this,
                "Are you sure you want to clear ALL inventory batches?\nThis action cannot be undone.",
                "Warning: Clear Inventory",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            batches.clear();
            for (StorageUnit warehouse : storageUnits) {
                warehouse.getGrainQueue().getQueue().clear();
            }

            wh1Counter = 1;
            wh2Counter = 1;
            wh3Counter = 1;

            saveData();
            HistoryLog.log("INVENTORY CLEARED", "N/A", "All Grains", 0, "All Warehouses");

            UI.showMessage(this, "All inventory records have been cleared successfully.");
            restartApplication();
        }
    }

    private final java.util.LinkedHashMap<String, UI.NavButton> navButtons =
            new java.util.LinkedHashMap<String, UI.NavButton>();

    private void showPage(String name) {
        if (name.equals("Dashboard")) {
            updateDashboard();
            updateWarnings();
        } else if (name.equals("Inventory")) {
            updateInventory();
        } else if (name.equals("Warehouses")) {
            updateWarehouses();
        } else if (name.equals("Shelf-life")) {
            updateShelfLife();
        } else if (name.equals("History")) {
            updateHistory();
        }
        cardLayout.show(mainPanel, name);
        for (java.util.Map.Entry<String, UI.NavButton> entry : navButtons.entrySet()) {
            entry.getValue().setSelected(entry.getKey().equals(name));
        }
    }

    private JPanel createNavigation() {
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = UI.g2(g);
                g2.setPaint(new GradientPaint(0, 0, UI.PRIMARY_DARK, getWidth(), 0, UI.PRIMARY));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        nav.setOpaque(false);
        nav.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        String[][] pages = {
                {"Dashboard", "Dashboard", "dashboard"},
                {"Inventory", "Inventory", "inventory"},
                {"Add Batch", "Add batch", "add"},
                {"Warehouses", "Warehouses", "warehouse"},
                {"Shelf-life", "Shelf-life", "clock"},
                {"History", "History", "history"}
        };

        for (String[] page : pages) {
            final String key = page[0];
            UI.NavButton button = new UI.NavButton(page[1], page[2]);
            button.addActionListener(e -> showPage(key));
            navButtons.put(key, button);
            nav.add(button);
        }

        return nav;
    }

    private JPanel createDashboard() {
        JPanel panel = UI.page();
        JLabel title = new JLabel("Dashboard");

        totalGrainLabel = new JLabel(UI.big("0", "kg"));
        totalSacksLabel = new JLabel("0 sacks in storage");
        totalBatchLabel = new JLabel("0");
        expiringLabel = new JLabel("0");
        JLabel unitsLabel = new JLabel(String.valueOf(storageUnits.size()));

        JPanel stats = UI.transparent(new GridLayout(1, 4, 16, 0));
        stats.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
        stats.add(UI.statCard("Total grain", totalGrainLabel, totalSacksLabel, "grain", UI.PRIMARY, UI.GOOD_BG));
        stats.add(UI.statCard("Active batches", totalBatchLabel, new JLabel("Currently stored"), "inventory", UI.PRIMARY, UI.GOOD_BG));
        expiringCard = UI.statCard("Expiring soon", expiringLabel, new JLabel("Within the next 7 days"), "alert", UI.WARN, UI.WARN_BG);
        stats.add(expiringCard);
        stats.add(UI.statCard("Storage units", unitsLabel, new JLabel("Warehouse 1–" + storageUnits.size()), "warehouse", UI.PRIMARY, UI.GOOD_BG));

        String[] columns = {"Batch ID", "Grain type", "Storage unit", "Sacks", "Time left", "Status"};
        warningModel = UI.model(columns);
        JTable warningTable = new JTable(warningModel);
        warningTable.setAutoCreateRowSorter(true);
        UI.styleTable(warningTable);
        warningTable.getColumnModel().getColumn(4).setCellRenderer(UI.days());
        warningTable.getColumnModel().getColumn(5).setCellRenderer(UI.pill());
        int[] w = {90, 190, 130, 100, 190, 150};
        for (int i = 0; i < w.length; i++) warningTable.getColumnModel().getColumn(i).setPreferredWidth(w[i]);

        JScrollPane warningScrollPane = UI.scroll(warningTable);
        warningScrollPane.setPreferredSize(new Dimension(100, 130));

        JLabel emptyLabel = new JLabel("Every batch is within a safe shelf-life.", SwingConstants.CENTER);
        emptyLabel.setFont(UI.font(Font.PLAIN, 14));
        emptyLabel.setForeground(UI.MUTED);

        warningCards = new CardLayout();
        warningCardPanel = UI.transparent(warningCards);
        warningCardPanel.add(warningScrollPane, "table");
        warningCardPanel.add(emptyLabel, "empty");
        warningCards.show(warningCardPanel, "empty");

        UI.Card warnCard = UI.card(new BorderLayout(0, 10));
        warnCard.setBorder(BorderFactory.createEmptyBorder(18, 6, 8, 6));
        JPanel warnHead = UI.transparent(new GridLayout(2, 1, 0, 2));
        warnHead.setBorder(BorderFactory.createEmptyBorder(0, 12, 6, 12));
        warnHead.add(UI.label("Shelf-life alerts", 16, UI.INK, true));
        warnHead.add(UI.label("Batches that have expired or expire within 7 days", 12.5f, UI.MUTED, false));
        warnCard.add(warnHead, BorderLayout.NORTH);
        warnCard.add(warningCardPanel, BorderLayout.CENTER);

        stockPanel = UI.transparent(new GridLayout(1, storageUnits.size(), 32, 0));
        UI.Card stockCard = UI.card(new BorderLayout(0, 16));
        JPanel stockHead = UI.transparent(new GridLayout(2, 1, 0, 2));
        stockHead.add(UI.label("Stock by warehouse", 16, UI.INK, true));
        stockHead.add(UI.label("Share of all grain in storage, by warehouse", 12.5f, UI.MUTED, false));
        stockCard.add(stockHead, BorderLayout.NORTH);
        stockCard.add(stockPanel, BorderLayout.CENTER);

        JPanel lower = UI.transparent(new BorderLayout(0, 16));
        lower.add(stockCard, BorderLayout.NORTH);
        lower.add(warnCard, BorderLayout.CENTER);

        panel.add(UI.north(UI.header(title, "Stock levels and the batches that need attention."), stats), BorderLayout.NORTH);
        panel.add(lower, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createInventory() {
        JPanel panel = UI.page();
        JLabel title = new JLabel("Inventory");

        JPanel searchPanel = UI.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JTextField searchField = UI.searchField("Search inventory");

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterTable(searchField.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterTable(searchField.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTable(searchField.getText()); }

            private void filterTable(String query) {
                TableRowSorter<DefaultTableModel> sorter = (TableRowSorter<DefaultTableModel>) inventoryTable.getRowSorter();
                if (query.trim().length() == 0) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + query));
                }
            }
        });

        searchPanel.add(searchField);

        JPanel topContainer = UI.header(title, "All stored batches. Select a row to adjust its stock.");

        String[] columns = {
                "Batch ID", "Grain Type", "Quantity (kg)", "Sacks", "Harvest Date",
                "Shelf Life", "Expiry Date", "Warehouse", "Status"
        };

        inventoryModel = UI.model(columns);
        inventoryTable = new JTable(inventoryModel);
        inventoryTable.setAutoCreateRowSorter(true);
        UI.styleTable(inventoryTable);
        inventoryTable.getColumnModel().getColumn(8).setCellRenderer(UI.pill());
        inventoryTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        inventoryTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        inventoryTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        inventoryTable.getColumnModel().getColumn(3).setPreferredWidth(70);
        inventoryTable.getColumnModel().getColumn(4).setPreferredWidth(110);
        inventoryTable.getColumnModel().getColumn(5).setPreferredWidth(90);
        inventoryTable.getColumnModel().getColumn(6).setPreferredWidth(110);
        inventoryTable.getColumnModel().getColumn(7).setPreferredWidth(120);
        inventoryTable.getColumnModel().getColumn(8).setPreferredWidth(140);

        JButton addButton = UI.primary("Add batch");
        JButton releaseButton = UI.secondary("Release grain");
        JButton adjustButton = UI.secondary("Adjust stock");
        JButton exportButton = UI.secondary("Export CSV");
        JButton clearInventoryButton = UI.danger("Clear inventory");

        addButton.addActionListener(e -> showPage("Add Batch"));
        releaseButton.addActionListener(e -> releaseGrain());
        adjustButton.addActionListener(e -> adjustStock());
        exportButton.addActionListener(e -> exportTableToCSV(inventoryModel, "inventory_report.csv", "Inventory"));
        clearInventoryButton.addActionListener(e -> clearAllInventory());

        JPanel buttons = UI.buttonRow();
        buttons.add(releaseButton);
        buttons.add(adjustButton);
        buttons.add(exportButton);
        buttons.add(addButton);

        JPanel footer = UI.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        footer.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        footer.add(clearInventoryButton);

        panel.add(UI.north(topContainer, UI.toolbar(searchPanel, buttons)), BorderLayout.NORTH);
        panel.add(UI.tableCard(inventoryTable), BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);

        return panel;
    }

    private void exportTableToCSV(DefaultTableModel model, String defaultFilename, String reportType) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save " + reportType + " Report as CSV");
        fileChooser.setSelectedFile(new File(defaultFilename));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();

            try (PrintWriter pw = new PrintWriter(new FileWriter(fileToSave))) {
                for (int i = 0; i < model.getColumnCount(); i++) {
                    pw.print(model.getColumnName(i));
                    if (i < model.getColumnCount() - 1) pw.print(",");
                }
                pw.println();

                for (int row = 0; row < model.getRowCount(); row++) {
                    for (int col = 0; col < model.getColumnCount(); col++) {
                        Object value = model.getValueAt(row, col);
                        pw.print(value != null ? value.toString() : "");
                        if (col < model.getColumnCount() - 1) pw.print(",");
                    }
                    pw.println();
                }

                UI.showMessage(this, reportType + " successfully exported to:\n" + fileToSave.getAbsolutePath(), "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                UI.showMessage(this, "Failed to export CSV file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void adjustStock() {
        if (inventoryTable == null) return;
        int selectedRow = inventoryTable.getSelectedRow();
        if (selectedRow == -1) {
            UI.showMessage(this, "Please select a batch from the inventory table to adjust.", "No Batch Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = inventoryTable.convertRowIndexToModel(selectedRow);
        String batchId = (String) inventoryModel.getValueAt(modelRow, 0);

        Batch batch = findBatch(batchId);
        if (batch == null) {
            UI.showMessage(this, "Selected batch not found.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0;

        JTextField newQtyField = new JTextField(String.valueOf(batch.getQuantity()));
        JComboBox<String> reasonBox = UI.combo(new String[]{
                "Moisture Shrinkage",
                "Spillage / Handling Loss",
                "Pest / Spoilage Removal",
                "Physical Count Correction",
                "Other"
        });

        JLabel otherLabel = new JLabel("Specify 'Other' cause:");
        JTextField otherReasonField = new JTextField();

        otherLabel.setVisible(false);
        otherReasonField.setVisible(false);

        reasonBox.addActionListener(e -> {
            boolean isOther = "Other".equals(reasonBox.getSelectedItem());
            otherLabel.setVisible(isOther);
            otherReasonField.setVisible(isOther);
            Window window = SwingUtilities.getWindowAncestor(panel);
            if (window != null) window.pack();
        });

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Batch ID:"), gbc);
        gbc.gridx = 1; panel.add(new JLabel(batch.getId() + " (" + batch.getGrainType() + ")"), gbc);

        gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel("Current Quantity (kg):"), gbc);
        gbc.gridx = 1; panel.add(new JLabel(batch.getQuantity() + " kg (" + batch.getSacks() + " sacks)"), gbc);

        gbc.gridx = 0; gbc.gridy = 2; panel.add(new JLabel("New Physical Quantity (kg):"), gbc);
        gbc.gridx = 1; panel.add(newQtyField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; panel.add(new JLabel("Reason for Adjustment:"), gbc);
        gbc.gridx = 1; panel.add(reasonBox, gbc);

        gbc.gridx = 0; gbc.gridy = 4; panel.add(otherLabel, gbc);
        gbc.gridx = 1; panel.add(otherReasonField, gbc);

        int result = UI.showConfirm(
                this,
                panel,
                "Stock Adjustment / Audit",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) return;

        String qtyText = newQtyField.getText().trim();
        if (!qtyText.matches("\\d+(\\.\\d+)?")) {
            UI.showMessage(this, "Please enter a valid positive number for the new quantity.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        double newQty = Double.parseDouble(qtyText);
        if (newQty < 0) {
            UI.showMessage(this, "Quantity cannot be negative.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String reason = (String) reasonBox.getSelectedItem();
        if ("Other".equals(reason)) {
            String customReason = otherReasonField.getText().trim();
            reason = customReason.isEmpty() ? "Other Adjustment" : customReason;
        }

        double oldQty = batch.getQuantity();
        batch.setQuantity(newQty);

        saveData();
        HistoryLog.log("STOCK ADJUSTED (" + reason + ")", batch.getId(), batch.getGrainType(), batch.getQuantity(), batch.getStorageUnit());

        UI.showMessage(this, "Stock adjustment successful!\nBatch ID: " + batch.getId() + "\nPrevious: " + oldQty + " kg\nNew: " + newQty + " kg (" + batch.getSacks() + " sacks)\nReason: " + reason);

        updateInventory();
        updateShelfLife();
        updateDashboard();
        updateWarnings();
        updateHistory();
        updateWarehouses();
    }

    private JPanel createAddBatch() {
        JPanel panel = UI.page();
        JLabel title = new JLabel("Add grain batch");

        JPanel form = UI.transparent(new GridBagLayout());
        JComboBox<String> grainBox = UI.combo(new String[]{"Rice", "Corn", "Mungbean"});

        JLabel riceTypeLabel = UI.fieldLabel("Rice variety");
        JTextField riceTypeField = UI.hintField("e.g. Dinorado");

        JTextField quantityField = UI.hintField("0");
        JTextField dateField = UI.hintField("YYYY-MM-DD");
        dateField.setText(LocalDate.now().toString());
        JTextField shelfField = UI.hintField("e.g. 180");
        JComboBox<String> warehouseBox = UI.combo(new String[]{"Warehouse 1", "Warehouse 2", "Warehouse 3"});

        ((AbstractDocument) quantityField.getDocument()).setDocumentFilter(new NumberFilter(true));
        ((AbstractDocument) shelfField.getDocument()).setDocumentFilter(new NumberFilter(false));

        JLabel sacksIndicationLabel = new JLabel("Equivalent Sacks: 0 sacks (Max 50kg/sack)");
        sacksIndicationLabel.setFont(UI.font(Font.PLAIN, 12));
        sacksIndicationLabel.setForeground(UI.MUTED);

        quantityField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateIndication(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateIndication(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateIndication(); }

            private void updateIndication() {
                try {
                    String text = quantityField.getText().trim();
                    if (text.isEmpty() || text.equals(".")) {
                        sacksIndicationLabel.setText("Equivalent Sacks: 0 sacks (Max 50kg/sack)");
                    } else {
                        double qty = Double.parseDouble(text);
                        int calculatedSacks = (int) Math.ceil(qty / 50.0);
                        sacksIndicationLabel.setText("Equivalent Sacks: " + calculatedSacks + " sacks (Based on 50kg/sack max)");
                    }
                } catch (NumberFormatException ex) {
                    sacksIndicationLabel.setText("Equivalent Sacks: 0 sacks (Max 50kg/sack)");
                }
            }
        });

        JPanel quantityPanel = UI.transparent(new BorderLayout(0, 6));
        quantityPanel.add(quantityField, BorderLayout.CENTER);
        quantityPanel.add(sacksIndicationLabel, BorderLayout.SOUTH);

        UI.cell(form, UI.fieldLabel("Grain type"), 0, 0);
        UI.cell(form, riceTypeLabel, 1, 0);
        UI.cell(form, grainBox, 0, 1);
        UI.cell(form, riceTypeField, 1, 1);
        UI.cell(form, UI.fieldLabel("Quantity (kg)"), 0, 2);
        UI.cell(form, UI.fieldLabel("Harvest date"), 1, 2);
        UI.cell(form, quantityPanel, 0, 3);
        UI.cell(form, dateField, 1, 3);
        UI.cell(form, UI.fieldLabel("Shelf life (days)"), 0, 4);
        UI.cell(form, UI.fieldLabel("Storage unit"), 1, 4);
        UI.cell(form, shelfField, 0, 5);
        UI.cell(form, warehouseBox, 1, 5);

        grainBox.addActionListener(e -> {
            boolean isRice = "Rice".equals(grainBox.getSelectedItem());
            riceTypeLabel.setVisible(isRice);
            riceTypeField.setVisible(isRice);
            if (!isRice) riceTypeField.setText("");
            form.revalidate();
            form.repaint();
        });

        boolean initialIsRice = "Rice".equals(grainBox.getSelectedItem());
        riceTypeLabel.setVisible(initialIsRice);
        riceTypeField.setVisible(initialIsRice);

        JButton addButton = UI.primary("Add batch");
        JButton clearButton = UI.secondary("Clear");

        JPanel buttons = UI.buttonRow();
        buttons.add(clearButton);
        buttons.add(addButton);

        addButton.addActionListener(e -> {
            String selectedGrain = grainBox.getSelectedItem().toString();
            String grainType;

            if ("Rice".equalsIgnoreCase(selectedGrain)) {
                String riceTypeInput = riceTypeField.getText().trim();
                if (riceTypeInput.isEmpty()) {
                    UI.showMessage(this, "Please enter a Rice Variety (or type 'Unspecified').", "Input Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                grainType = "Rice (" + riceTypeInput + ")";
            } else {
                grainType = selectedGrain;
            }

            String qtyInput = quantityField.getText().trim();
            String shelfInput = shelfField.getText().trim();
            String dateInput = dateField.getText().trim();

            if (qtyInput.isEmpty() || shelfInput.isEmpty()) {
                UI.showMessage(this, "Please fill in all required fields.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            double quantity = Double.parseDouble(qtyInput);
            int shelfLife = Integer.parseInt(shelfInput);

            if (quantity <= 0 || shelfLife <= 0) {
                UI.showMessage(this, "Quantity and Shelf Life must be greater than zero.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            LocalDate harvestDate;
            try {
                harvestDate = LocalDate.parse(dateInput);
            } catch (DateTimeParseException ex) {
                UI.showMessage(this, "Invalid date format. Please use YYYY-MM-DD format.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String warehouseName = warehouseBox.getSelectedItem().toString();
            String generatedId = generateBatchId(warehouseName, selectedGrain);

            Batch batch = new Batch(generatedId, grainType, quantity, harvestDate, shelfLife, warehouseName);
            batches.add(batch);

            StorageUnit warehouse = findStorageUnit(warehouseName);
            if (warehouse != null) {
                warehouse.getGrainQueue().addBatch(batch);
            }

            HistoryLog.log("BATCH ADDED", batch.getId(), batch.getGrainType(), batch.getQuantity(), batch.getStorageUnit());
            saveData();

            UI.showMessage(this, "Batch successfully added!\nAssigned Batch ID: " + batch.getId() + "\nGrain Type: " + grainType + "\nSacks: " + batch.getSacks() + " sacks");

            quantityField.setText("");
            shelfField.setText("");
            riceTypeField.setText("");

            updateInventory();
            updateShelfLife();
            updateDashboard();
            updateWarnings();
            updateHistory();
            updateWarehouses();
        });

        clearButton.addActionListener(e -> {
            quantityField.setText("");
            shelfField.setText("");
            riceTypeField.setText("");
        });

        UI.Card formCard = UI.card(new BorderLayout(0, 26));
        formCard.setBorder(BorderFactory.createEmptyBorder(26, 28, 22, 28));
        formCard.add(form, BorderLayout.CENTER);
        formCard.add(buttons, BorderLayout.SOUTH);

        JLabel pvId = new JLabel("–");
        JLabel pvSacks = new JLabel("–");
        JLabel pvExpiry = new JLabel("–");
        JLabel pvLeft = new JLabel("–");
        Runnable refreshPreview = () -> {
            pvId.setText(peekBatchId((String) warehouseBox.getSelectedItem(), (String) grainBox.getSelectedItem()));
            try {
                int sacks = (int) Math.ceil(Double.parseDouble(quantityField.getText().trim()) / 50.0);
                pvSacks.setText(sacks + (sacks == 1 ? " sack" : " sacks"));
            } catch (NumberFormatException ex) {
                pvSacks.setText("–");
            }
            try {
                LocalDate expiry = LocalDate.parse(dateField.getText().trim()).plusDays(Integer.parseInt(shelfField.getText().trim()));
                long left = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiry);
                pvExpiry.setText(expiry.toString());
                pvLeft.setText(left < 0 ? "Already expired" : left + (left == 1 ? " day" : " days") + " from today");
            } catch (Exception ex) {
                pvExpiry.setText("–");
                pvLeft.setText("–");
            }
        };
        UI.onChange(quantityField, refreshPreview);
        UI.onChange(dateField, refreshPreview);
        UI.onChange(shelfField, refreshPreview);
        warehouseBox.addActionListener(e -> refreshPreview.run());
        grainBox.addActionListener(e -> refreshPreview.run());
        refreshPreview.run();

        UI.Card previewCard = UI.card(new BorderLayout(0, 20));
        previewCard.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JPanel previewHead = UI.transparent(new GridLayout(2, 1, 0, 2));
        previewHead.add(UI.label("Batch preview", 16, UI.INK, true));
        previewHead.add(UI.label("Updates as you fill in the form", 12.5f, UI.MUTED, false));
        JPanel previewRows = UI.transparent(new GridLayout(4, 1, 0, 16));
        previewRows.add(previewRow("Next batch ID", pvId));
        previewRows.add(previewRow("Sacks needed", pvSacks));
        previewRows.add(previewRow("Expiry date", pvExpiry));
        previewRows.add(previewRow("Time left", pvLeft));
        previewCard.add(previewHead, BorderLayout.NORTH);
        previewCard.add(previewRows, BorderLayout.CENTER);

        JPanel left = UI.transparent(new BorderLayout());
        left.add(formCard, BorderLayout.NORTH);
        JPanel right = UI.transparent(new BorderLayout());
        right.add(previewCard, BorderLayout.NORTH);
        right.setPreferredSize(new Dimension(290, 10));
        JPanel body = UI.transparent(new BorderLayout(16, 0));
        body.add(left, BorderLayout.CENTER);
        body.add(right, BorderLayout.EAST);

        panel.add(UI.header(title, "Record a new batch. Stock is released earliest expiry first."), BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createWarehouses() {
        JPanel panel = UI.page();
        JLabel title = new JLabel("Warehouses");

        warehouseContainerPanel = UI.transparent(new GridLayout(1, storageUnits.size(), 16, 0));
        JPanel wrap = UI.transparent(new BorderLayout());
        wrap.add(warehouseContainerPanel, BorderLayout.NORTH);

        panel.add(UI.header(title, "Grain held in each storage unit. It is released earliest expiry first."), BorderLayout.NORTH);
        panel.add(wrap, BorderLayout.CENTER);

        updateWarehouses();
        return panel;
    }

    private void updateWarehouses() {
        if (warehouseContainerPanel == null) return;
        warehouseContainerPanel.removeAll();

        double grandTotal = 0;
        for (StorageUnit w : storageUnits) grandTotal += w.getTotalQuantity();

        for (StorageUnit warehouse : storageUnits) {
            double qty = warehouse.getTotalQuantity();
            int sacks = 0;
            for (Batch b : warehouse.getGrainQueue().getQueue()) sacks += b.getSacks();
            int count = warehouse.getBatchCount();
            double share = grandTotal > 0 ? qty / grandTotal : 0;

            UI.Card box = UI.card(new BorderLayout(0, 20));
            box.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));

            JPanel head = UI.transparent(new BorderLayout(12, 0));
            head.add(UI.chip("warehouse", UI.PRIMARY, UI.GOOD_BG, 38), BorderLayout.WEST);
            head.add(UI.label(warehouse.getName(), 17, UI.INK, true), BorderLayout.CENTER);

            JLabel qtyLabel = new JLabel(UI.big(String.format("%,.0f", qty), "kg"));
            qtyLabel.setForeground(UI.INK);
            qtyLabel.setFont(UI.font(Font.BOLD, 28));
            JPanel totals = UI.transparent(new GridLayout(2, 1, 0, 2));
            totals.add(qtyLabel);
            totals.add(UI.label(count + (count == 1 ? " batch, " : " batches, ") + sacks + (sacks == 1 ? " sack" : " sacks"),
                    13, UI.MUTED, false));

            JPanel shareBlock = UI.transparent(new BorderLayout(0, 7));
            shareBlock.add(new UI.Bar(share), BorderLayout.NORTH);
            shareBlock.add(UI.label(Math.round(share * 100) + "% of all stored grain", 12.5f, UI.MUTED, false), BorderLayout.CENTER);

            Batch next = warehouse.getGrainQueue().peekBatch();
            JPanel nextBlock = UI.transparent(new GridLayout(3, 1, 0, 3));
            nextBlock.add(UI.label("Next out", 12.5f, UI.MUTED, true));
            if (next == null) {
                nextBlock.add(UI.label("Nothing stored", 14, UI.INK, false));
                nextBlock.add(UI.label(" ", 12.5f, UI.MUTED, false));
            } else {
                long days = next.getRemainingDays();
                String when = days < 0 ? "Expired " + Math.abs(days) + (Math.abs(days) == 1 ? " day ago" : " days ago")
                        : "Expires in " + days + (days == 1 ? " day" : " days");
                nextBlock.add(UI.label(next.getId() + "  " + next.getGrainType(), 14, UI.INK, true));
                nextBlock.add(UI.label(when, 12.5f, days < 0 ? UI.BAD : (days <= 7 ? UI.WARN : UI.MUTED), days <= 7));
            }

            JPanel mid = UI.transparent(new BorderLayout(0, 18));
            mid.add(shareBlock, BorderLayout.NORTH);
            mid.add(nextBlock, BorderLayout.CENTER);

            JPanel body = UI.transparent(new BorderLayout(0, 18));
            body.add(totals, BorderLayout.NORTH);
            body.add(mid, BorderLayout.CENTER);

            JButton viewButton = UI.secondary("View contents");
            viewButton.addActionListener(e -> showWarehouseInventory(warehouse));
            JPanel foot = UI.transparent(new BorderLayout());
            foot.add(viewButton, BorderLayout.CENTER);

            box.add(head, BorderLayout.NORTH);
            box.add(body, BorderLayout.CENTER);
            box.add(foot, BorderLayout.SOUTH);

            warehouseContainerPanel.add(box);
        }

        warehouseContainerPanel.revalidate();
        warehouseContainerPanel.repaint();
    }

    private void showWarehouseInventory(StorageUnit warehouse) {
        PriorityQueue<Batch> tempQueue = new PriorityQueue<>(Comparator.comparing(Batch::getExpiryDate));
        tempQueue.addAll(warehouse.getGrainQueue().getQueue());

        JPanel content = UI.transparent(new BorderLayout(0, 14));
        content.add(UI.label("Release order follows the earliest expiry date (FEFO).", 13.5f, UI.MUTED, false), BorderLayout.NORTH);

        if (tempQueue.isEmpty()) {
            JLabel empty = UI.label("No batches stored in this warehouse.", 14, UI.INK, false);
            empty.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
            content.add(empty, BorderLayout.CENTER);
        } else {
            String[] cols = {"Order", "Batch ID", "Grain type", "Quantity (kg)", "Sacks", "Expires", "Time left", "Status"};
            DefaultTableModel m = UI.model(cols);
            int order = 1;
            while (!tempQueue.isEmpty()) {
                Batch b = tempQueue.poll();
                m.addRow(new Object[]{order++, b.getId(), b.getGrainType(), b.getQuantity(), b.getSacks(),
                        b.getExpiryDate(), b.getRemainingDays(), b.getStatus()});
            }
            JTable t = new JTable(m);
            t.setAutoCreateRowSorter(true);
            UI.styleTable(t);
            t.getColumnModel().getColumn(6).setCellRenderer(UI.days());
            t.getColumnModel().getColumn(7).setCellRenderer(UI.pill());
            t.getColumnModel().getColumn(0).setPreferredWidth(60);
            t.getColumnModel().getColumn(2).setPreferredWidth(170);
            t.getColumnModel().getColumn(6).setPreferredWidth(130);
            t.getColumnModel().getColumn(7).setPreferredWidth(140);
            JScrollPane sp = UI.scroll(t);
            sp.setBorder(BorderFactory.createLineBorder(UI.LINE));
            sp.setPreferredSize(new Dimension(800, Math.min(340, 44 + m.getRowCount() * 41)));
            content.add(sp, BorderLayout.CENTER);
        }

        UI.showMessage(this, content, warehouse.getName(), JOptionPane.PLAIN_MESSAGE);
    }

    private JPanel createShelfLife() {
        JPanel panel = UI.page();
        JLabel title = new JLabel("Shelf-life");

        JPanel searchPanel = UI.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JTextField searchField = UI.searchField("Search shelf-life");

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterTable(searchField.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterTable(searchField.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTable(searchField.getText()); }

            private void filterTable(String query) {
                TableRowSorter<DefaultTableModel> sorter = (TableRowSorter<DefaultTableModel>) shelfTable.getRowSorter();
                if (query.trim().length() == 0) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + query));
                }
            }
        });

        searchPanel.add(searchField);

        JPanel topContainer = UI.header(title, "Days remaining for each batch. Sort by time left to see what to release first.");

        String[] columns = { "Batch ID", "Grain Type", "Warehouse", "Remaining Days", "Status" };

        shelfModel = UI.model(columns);
        shelfTable = new JTable(shelfModel);
        shelfTable.setAutoCreateRowSorter(true);
        UI.styleTable(shelfTable);
        shelfTable.getColumnModel().getColumn(3).setCellRenderer(UI.days());
        shelfTable.getColumnModel().getColumn(4).setCellRenderer(UI.pill());
        shelfTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        shelfTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        shelfTable.getColumnModel().getColumn(2).setPreferredWidth(130);
        shelfTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        shelfTable.getColumnModel().getColumn(4).setPreferredWidth(140);

        JButton exportButton = UI.secondary("Export CSV");
        exportButton.addActionListener(e -> exportTableToCSV(shelfModel, "shelflife_report.csv", "Shelf-life"));

        JPanel buttons = UI.buttonRow();
        buttons.add(exportButton);

        panel.add(UI.north(topContainer, UI.toolbar(searchPanel, buttons)), BorderLayout.NORTH);
        panel.add(UI.tableCard(shelfTable), BorderLayout.CENTER);

        return panel;
    }

    private JPanel createHistory() {
        JPanel panel = UI.page();
        JLabel title = new JLabel("History");

        JPanel searchPanel = UI.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JTextField searchField = UI.searchField("Search history");

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterTable(searchField.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterTable(searchField.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTable(searchField.getText()); }

            private void filterTable(String query) {
                TableRowSorter<DefaultTableModel> sorter = (TableRowSorter<DefaultTableModel>) historyTable.getRowSorter();
                if (query.trim().length() == 0) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + query));
                }
            }
        });

        searchPanel.add(searchField);

        JPanel topContainer = UI.header(title, "A log of every addition, release and adjustment.");

        String[] columns = {
                "Date & Time", "Action", "Batch ID", "Grain Type", "Quantity", "Sacks", "Storage Unit"
        };

        historyModel = UI.model(columns);
        historyTable = new JTable(historyModel);
        historyTable.setAutoCreateRowSorter(true);
        UI.styleTable(historyTable);
        historyTable.getColumnModel().getColumn(1).setCellRenderer(UI.action());
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(160);
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(250);
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        historyTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        historyTable.getColumnModel().getColumn(5).setPreferredWidth(80);
        historyTable.getColumnModel().getColumn(6).setPreferredWidth(120);

        JButton refreshButton = UI.secondary("Refresh");
        JButton openFileButton = UI.secondary("Open history file");
        JButton exportButton = UI.secondary("Export CSV");
        JButton clearButton = UI.danger("Clear history");

        refreshButton.addActionListener(e -> updateHistory());

        openFileButton.addActionListener(e -> {
            try {
                File file = new File("history.txt");
                if (!file.exists()) file.createNewFile();
                Desktop.getDesktop().open(file);
            } catch (Exception ex) {
                UI.showMessage(this, "Unable to open history file.");
            }
        });

        exportButton.addActionListener(e -> exportTableToCSV(historyModel, "history_report.csv", "History"));

        clearButton.addActionListener(e -> {
            int confirm = UI.showConfirm(
                    this,
                    "Are you sure you want to clear all history records?\nThis action cannot be undone.",
                    "Warning: Clear History",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (confirm == JOptionPane.YES_OPTION) {
                HistoryLog.clearHistory();
                updateHistory();
                UI.showMessage(this, "History log has been cleared successfully.");
            }
        });

        JPanel buttons = UI.buttonRow();
        buttons.add(refreshButton);
        buttons.add(openFileButton);
        buttons.add(exportButton);
        buttons.add(clearButton);

        panel.add(UI.north(topContainer, UI.toolbar(searchPanel, buttons)), BorderLayout.NORTH);
        panel.add(UI.tableCard(historyTable), BorderLayout.CENTER);

        return panel;
    }

    private void updateHistory() {
        if (historyModel == null) return;
        historyModel.setRowCount(0);
        String history = HistoryLog.readHistory();

        if (history.equals("No history available.") || history.equals("Unable to read history.")) return;

        String[] entries = history.split("----------------------------------------");

        for (String entry : entries) {
            if (entry.trim().isEmpty()) continue;

            String dateTime = "", action = "", batchId = "", grainType = "", quantity = "", sacksVal = "0 sacks", storageUnit = "";
            String[] lines = entry.trim().split("\\r?\\n");

            for (String line : lines) {
                if (line.startsWith("[")) {
                    int closingBracket = line.indexOf("]");
                    if (closingBracket > 0) {
                        dateTime = line.substring(1, closingBracket);
                        action = line.substring(closingBracket + 2);
                    }
                }
                if (line.startsWith("Batch ID:")) batchId = line.substring("Batch ID:".length()).trim();
                if (line.startsWith("Grain Type:")) grainType = line.substring("Grain Type:".length()).trim();
                if (line.startsWith("Quantity:")) quantity = line.substring("Quantity:".length()).trim();
                if (line.startsWith("Sacks:")) sacksVal = line.substring("Sacks:".length()).trim();
                if (line.startsWith("Storage Unit:")) storageUnit = line.substring("Storage Unit:".length()).trim();
            }

            if (storageUnit.toLowerCase().contains("silo")) {
                storageUnit = storageUnit.replaceAll("(?i)Silo", "Warehouse");
            } else if (storageUnit.equalsIgnoreCase("All Silos")) {
                storageUnit = "All Warehouses";
            }

            if (sacksVal.equals("0 sacks") && quantity.contains("(") && quantity.contains(")")) {
                int start = quantity.indexOf("(");
                int end = quantity.indexOf(")");
                sacksVal = quantity.substring(start + 1, end);
                quantity = quantity.substring(0, start).trim();
            }

            historyModel.addRow(new Object[]{ dateTime, action, batchId, grainType, quantity, sacksVal, storageUnit });
        }
    }

    private void updateInventory() {
        if (inventoryModel == null) return;
        inventoryModel.setRowCount(0);
        for (Batch batch : batches) {
            inventoryModel.addRow(new Object[]{
                    batch.getId(),
                    batch.getGrainType(),
                    batch.getQuantity(),
                    batch.getSacks(),
                    batch.getHarvestDate(),
                    batch.getShelfLife(),
                    batch.getExpiryDate(),
                    batch.getStorageUnit(),
                    batch.getStatus()
            });
        }
    }

    private void updateShelfLife() {
        if (shelfModel == null) return;
        shelfModel.setRowCount(0);
        for (Batch batch : batches) {
            shelfModel.addRow(new Object[]{
                    batch.getId(),
                    batch.getGrainType(),
                    batch.getStorageUnit(),
                    batch.getRemainingDays(),
                    batch.getStatus()
            });
        }
    }

    private void updateDashboard() {
        double total = 0;
        int totalSacks = 0;
        int warningCount = 0;

        for (Batch batch : batches) {
            total += batch.getQuantity();
            totalSacks += batch.getSacks();
            long days = batch.getRemainingDays();
            if (days >= 0 && days <= 7) warningCount++;
        }

        if (totalGrainLabel != null) totalGrainLabel.setText(UI.big(String.format("%,.0f", total), "kg"));
        if (totalSacksLabel != null) totalSacksLabel.setText(totalSacks + (totalSacks == 1 ? " sack" : " sacks") + " in storage");
        if (totalBatchLabel != null) totalBatchLabel.setText(String.valueOf(batches.size()));
        if (expiringLabel != null) expiringLabel.setText(String.valueOf(warningCount));
        if (expiringCard != null) {
            if (warningCount > 0) expiringCard.tone(new Color(0xFFF6E3), new Color(0xF0D79F));
            else expiringCard.tone(UI.CARD, UI.LINE);
        }

        if (stockPanel != null) {
            stockPanel.removeAll();
            double grand = 0;
            for (StorageUnit w : storageUnits) grand += w.getTotalQuantity();
            for (StorageUnit w : storageUnits) {
                double q = w.getTotalQuantity();
                stockPanel.add(UI.stockRow(w.getName(), String.format("%,.0f kg", q), grand > 0 ? q / grand : 0));
            }
            stockPanel.revalidate();
            stockPanel.repaint();
        }
    }

    private void updateWarnings() {
        if (warningModel == null) return;
        warningModel.setRowCount(0);
        ArrayList<Batch> warningBatches = new ArrayList<>();

        for (Batch batch : batches) {
            if (batch.getRemainingDays() <= 7) warningBatches.add(batch);
        }

        warningBatches.sort(Comparator.comparingLong(Batch::getRemainingDays));

        for (Batch batch : warningBatches) {
            long days = batch.getRemainingDays();
            String daysText = (days < 0) ? "Expired (" + Math.abs(days) + " days ago)" : days + " days";

            warningModel.addRow(new Object[]{
                    batch.getId(),
                    batch.getGrainType(),
                    batch.getStorageUnit(),
                    batch.getSacks() + " sacks",
                    daysText,
                    batch.getStatus()
            });
        }

        if (warningCards != null && warningCardPanel != null) {
            if (warningModel.getRowCount() > 0) {
                warningCards.show(warningCardPanel, "table");
            } else {
                warningCards.show(warningCardPanel, "empty");
            }
            warningCardPanel.revalidate();
            warningCardPanel.repaint();
        }
    }

    private void releaseGrain() {
        String[] warehouseOptions = {"Warehouse 1", "Warehouse 2", "Warehouse 3"};
        JComboBox<String> warehouseComboBox = UI.combo(warehouseOptions);

        int warehouseChoice = UI.showConfirm(
                this,
                warehouseComboBox,
                "Select Storage Unit for Release",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (warehouseChoice != JOptionPane.OK_OPTION) return;

        String warehouseName = (String) warehouseComboBox.getSelectedItem();
        StorageUnit warehouse = findStorageUnit(warehouseName);

        if (warehouse == null || warehouse.getGrainQueue().isEmpty()) {
            UI.showMessage(this, "No grain stored in " + warehouseName + ".", "Empty Warehouse", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Set<String> baseGrainTypes = new LinkedHashSet<>();
        for (Batch b : warehouse.getGrainQueue().getQueue()) {
            String gType = b.getGrainType();
            if (gType.startsWith("Rice")) baseGrainTypes.add("Rice");
            else baseGrainTypes.add(gType.trim());
        }

        JPanel selectionPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        JComboBox<String> grainTypeBox = UI.combo(baseGrainTypes.toArray(new String[0]));
        JLabel riceTypeLabel = new JLabel("Select Rice Type:");
        JComboBox<String> riceTypeBox = UI.combo();

        selectionPanel.add(new JLabel("Select Grain Type:"));
        selectionPanel.add(grainTypeBox);
        selectionPanel.add(riceTypeLabel);
        selectionPanel.add(riceTypeBox);

        Runnable populateRiceTypes = () -> {
            riceTypeBox.removeAllItems();
            Set<String> availableRiceTypes = new LinkedHashSet<>();
            for (Batch b : warehouse.getGrainQueue().getQueue()) {
                if (b.getGrainType().startsWith("Rice (") && b.getGrainType().endsWith(")")) {
                    String sub = b.getGrainType().substring(6, b.getGrainType().length() - 1);
                    availableRiceTypes.add(sub);
                } else if (b.getGrainType().equalsIgnoreCase("Rice")) {
                    availableRiceTypes.add("Unspecified");
                }
            }
            for (String rt : availableRiceTypes) riceTypeBox.addItem(rt);
        };

        grainTypeBox.addActionListener(e -> {
            boolean isRice = "Rice".equals(grainTypeBox.getSelectedItem());
            riceTypeLabel.setVisible(isRice);
            riceTypeBox.setVisible(isRice);
            if (isRice) populateRiceTypes.run();
        });

        boolean initialIsRice = "Rice".equals(grainTypeBox.getSelectedItem());
        riceTypeLabel.setVisible(initialIsRice);
        riceTypeBox.setVisible(initialIsRice);
        if (initialIsRice) populateRiceTypes.run();

        int grainChoice = UI.showConfirm(
                this,
                selectionPanel,
                "Select Grain to Release from " + warehouseName,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (grainChoice != JOptionPane.OK_OPTION) return;

        String selectedBaseGrain = (String) grainTypeBox.getSelectedItem();
        String targetFullGrainType;

        if ("Rice".equalsIgnoreCase(selectedBaseGrain)) {
            String selectedRiceType = (String) riceTypeBox.getSelectedItem();
            if (selectedRiceType == null) {
                UI.showMessage(this, "No specific Rice Type available to release.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            targetFullGrainType = "Unspecified".equalsIgnoreCase(selectedRiceType) ? "Rice" : "Rice (" + selectedRiceType + ")";
        } else {
            targetFullGrainType = selectedBaseGrain;
        }

        double availableForType = 0;
        for (Batch b : warehouse.getGrainQueue().getQueue()) {
            if (b.getGrainType().trim().equalsIgnoreCase(targetFullGrainType.trim())) {
                availableForType += b.getQuantity();
            }
        }

        String amountText = UI.showInput(
                this,
                "Quantity to release for " + targetFullGrainType + " (kg):\nAvailable: " + availableForType + " kg"
        );
        if (amountText == null) return;

        amountText = amountText.trim();
        if (!amountText.matches("\\d+(\\.\\d+)?")) {
            UI.showMessage(this, "Quantity must be a positive number.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        double amount = Double.parseDouble(amountText);
        if (amount <= 0 || amount > availableForType) {
            UI.showMessage(this, "Invalid quantity entered or insufficient stock.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        double remainingAmount = amount;
        PriorityQueue<Batch> tempQueue = new PriorityQueue<>(Comparator.comparing(Batch::getExpiryDate));
        tempQueue.addAll(warehouse.getGrainQueue().getQueue());

        while (remainingAmount > 0 && !tempQueue.isEmpty()) {
            Batch currentBatch = tempQueue.poll();
            if (!currentBatch.getGrainType().trim().equalsIgnoreCase(targetFullGrainType.trim())) {
                continue;
            }

            double releasedQuantity;
            if (currentBatch.getQuantity() <= remainingAmount) {
                releasedQuantity = currentBatch.getQuantity();
                remainingAmount -= releasedQuantity;
                HistoryLog.log("GRAIN RELEASED (FEFO)", currentBatch.getId(), currentBatch.getGrainType(), releasedQuantity, warehouse.getName());
                batches.remove(currentBatch);
            } else {
                releasedQuantity = remainingAmount;
                currentBatch.setQuantity(currentBatch.getQuantity() - releasedQuantity);
                remainingAmount = 0;
                HistoryLog.log("GRAIN PARTIALLY RELEASED (FEFO)", currentBatch.getId(), currentBatch.getGrainType(), releasedQuantity, warehouse.getName());
                tempQueue.add(currentBatch);
            }
        }

        // Rebuild queue from scratch to fix priority heap corruption after partial updates
        warehouse.getGrainQueue().getQueue().clear();
        for (Batch b : batches) {
            if (b.getStorageUnit() != null && b.getStorageUnit().equalsIgnoreCase(warehouse.getName())) {
                warehouse.getGrainQueue().addBatch(b);
            }
        }

        saveData();
        UI.showMessage(this, "FEFO release completed successfully.");

        updateInventory();
        updateShelfLife();
        updateDashboard();
        updateWarnings();
        updateHistory();
        updateWarehouses();
    }

    private Batch findBatch(String id) {
        for (Batch batch : batches) {
            if (batch.getId().equalsIgnoreCase(id)) return batch;
        }
        return null;
    }

    private StorageUnit findStorageUnit(String name) {
        for (StorageUnit warehouse : storageUnits) {
            if (warehouse.getName().equalsIgnoreCase(name)) return warehouse;
        }
        return null;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TITA window = new TITA();
            window.setVisible(true);
        });
    }
}