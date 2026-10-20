package TITAProject;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class Batch implements Serializable {

    private static final long serialVersionUID = 1L;

    private static int counter = 1;

    private String id;
    private String grainType;
    private double quantity;
    private LocalDate harvestDate;
    private int shelfLife;
    private String storageUnit;

    public Batch(String id, String grainType, double quantity, LocalDate harvestDate, int shelfLife, String storageUnit) {
        this.id = id;
        this.grainType = grainType;
        this.quantity = quantity;
        this.harvestDate = harvestDate;
        this.shelfLife = shelfLife;
        this.storageUnit = storageUnit;
    }

    public Batch(String grainType, double quantity, LocalDate harvestDate, int shelfLife, String storageUnit) {
        this(String.format("B%03d", counter++), grainType, quantity, harvestDate, shelfLife, storageUnit);
    }

    public static void setCounter(int newCounter) {
        counter = newCounter;
    }

    public String getId() {
        return id;
    }

    public String getGrainType() {
        return grainType;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public int getSacks() {
        return (int) Math.ceil(quantity / 50.0);
    }

    public LocalDate getHarvestDate() {
        return harvestDate;
    }

    public int getShelfLife() {
        return shelfLife;
    }

    public String getStorageUnit() {
        return storageUnit;
    }

    public void setStorageUnit(String storageUnit) {
        this.storageUnit = storageUnit;
    }

    public LocalDate getExpiryDate() {
        return harvestDate.plusDays(shelfLife);
    }

    public long getRemainingDays() {
        return ChronoUnit.DAYS.between(LocalDate.now(), getExpiryDate());
    }

    public String getStatus() {
        long days = getRemainingDays();
        if (days < 0) return "Expired";
        if (days <= 7) return "Expiring Soon";
        return "Good";
    }
}