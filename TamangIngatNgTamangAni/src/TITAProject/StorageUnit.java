package TITAProject;

import java.io.Serializable;

public class StorageUnit implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private GrainQueue grainQueue;

    public StorageUnit(String name) {
        this.name = name;
        this.grainQueue = new GrainQueue();
    }

    public String getName() {
        return name;
    }

    public GrainQueue getGrainQueue() {
        return grainQueue;
    }

    public double getTotalQuantity() {
        double total = 0;
        for (Batch batch : grainQueue.getQueue()) {
            total += batch.getQuantity();
        }
        return total;
    }

    public int getBatchCount() {
        return grainQueue.size();
    }
}
