package TITAProject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class HistoryLog {

    private static final String FILE_NAME = "history.txt";

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void log(
            String action,
            String batchId,
            String grainType,
            double quantity,
            String storageUnit
    ) {
        try {
            File file = new File(FILE_NAME);

            if (!file.exists()) {
                file.createNewFile();
            }

            FileWriter writer = new FileWriter(file, true);
            String dateTime = LocalDateTime.now().format(FORMAT);
            int sacks = (int) Math.ceil(quantity / 50.0);

            writer.write("[" + dateTime + "] " + action + "\n");
            writer.write("Batch ID: " + batchId + "\n");
            writer.write("Grain Type: " + grainType + "\n");
            writer.write("Quantity: " + quantity + " kg\n");
            writer.write("Sacks: " + sacks + " sacks\n");
            writer.write("Storage Unit: " + storageUnit + "\n");
            writer.write("----------------------------------------\n");
            writer.close();

        } catch (IOException e) {
            System.out.println("Unable to save history.");
        }
    }

    public static String readHistory() {
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            return "No history available.";
        }

        StringBuilder history = new StringBuilder();
        boolean needsMigration = false;
        List<String[]> parsedEntries = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            String dateTime = "", action = "", batchId = "", grainType = "", quantityStr = "", sacksStr = "", storageUnit = "";
            double currentQty = 0;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("[")) {
                    int closingBracket = line.indexOf("]");
                    if (closingBracket > 0) {
                        dateTime = line.substring(1, closingBracket);
                        action = line.substring(closingBracket + 2);
                    }
                } else if (line.startsWith("Batch ID:")) {
                    batchId = line.substring("Batch ID:".length()).trim();
                } else if (line.startsWith("Grain Type:")) {
                    grainType = line.substring("Grain Type:".length()).trim();
                } else if (line.startsWith("Quantity:")) {
                    quantityStr = line.substring("Quantity:".length()).trim();
                    if (quantityStr.contains("(") && quantityStr.contains(")")) {
                        needsMigration = true;
                        int start = quantityStr.indexOf("(");
                        int end = quantityStr.indexOf(")");
                        sacksStr = quantityStr.substring(start + 1, end);
                        quantityStr = quantityStr.substring(0, start).trim();
                    }
                    
                    quantityStr = quantityStr.replaceAll("(?i)\\bkg\\b", "").trim();
                    
                    String cleanNum = quantityStr.replaceAll("[^0-9.]", "");
                    if (!cleanNum.isEmpty()) {
                        try {
                            currentQty = Double.parseDouble(cleanNum);
                        } catch (NumberFormatException ignored) {}
                    }
                } else if (line.startsWith("Sacks:")) {
                    sacksStr = line.substring("Sacks:".length()).trim();
                } else if (line.startsWith("Storage Unit:")) {
                    storageUnit = line.substring("Storage Unit:".length()).trim();
                } else if (line.startsWith("----------------------------------------")) {
                    int calculatedSacks = (int) Math.ceil(currentQty / 50.0);
                    if (sacksStr.isEmpty() || sacksStr.equals("0 sacks") || sacksStr.equals("0")) {
                        sacksStr = calculatedSacks + " sacks";
                        needsMigration = true;
                    }

                    if (currentQty > 0) {
                        quantityStr = currentQty + " kg";
                    } else if (quantityStr.isEmpty()) {
                        quantityStr = "0.0 kg";
                    } else if (!quantityStr.toLowerCase().contains("kg")) {
                        quantityStr = quantityStr + " kg";
                    }

                    parsedEntries.add(new String[]{dateTime, action, batchId, grainType, quantityStr, sacksStr, storageUnit});

                    dateTime = ""; action = ""; batchId = ""; grainType = ""; quantityStr = ""; sacksStr = ""; storageUnit = "";
                    currentQty = 0;
                }
            }
        } catch (IOException e) {
            return "Unable to read history.";
        }

        for (String[] entry : parsedEntries) {
            history.append("[").append(entry[0]).append("] ").append(entry[1]).append("\n");
            history.append("Batch ID: ").append(entry[2]).append("\n");
            history.append("Grain Type: ").append(entry[3]).append("\n");
            history.append("Quantity: ").append(entry[4]).append("\n");
            history.append("Sacks: ").append(entry[5]).append("\n");
            history.append("Storage Unit: ").append(entry[6]).append("\n");
            history.append("----------------------------------------\n");
        }

        if (needsMigration || true) {
            try (FileWriter writer = new FileWriter(file, false)) {
                writer.write(history.toString());
            } catch (IOException ignored) {}
        }

        return history.length() > 0 ? history.toString() : "No history available.";
    }

    public static void clearHistory() {
        try {
            File file = new File(FILE_NAME);

            if (file.exists()) {
                FileWriter writer = new FileWriter(file, false);
                writer.write("");
                writer.close();
            }

        } catch (IOException e) {
            System.out.println("Unable to clear history.");
        }
    }
}
