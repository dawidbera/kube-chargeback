package io.kubechargeback.common.model;

import java.time.Instant;
import java.util.UUID;

public class AllocationSnapshot {
    private String id;
    private Instant windowStart;
    private Instant windowEnd;
    private String groupType; // TEAM|NAMESPACE|APP
    private String groupKey;
    private long cpuMcpu;
    private long memMib;
    private double cpuCostUnits;
    private double memCostUnits;
    private double totalCostUnits;

    /**
     * Default constructor that initializes the ID with a random UUID.
     */
    public AllocationSnapshot() {
        this.id = UUID.randomUUID().toString();
    }

    /**
     * Gets the snapshot ID.
     * @return the ID
     */
    public String getId() { return id; }
    /**
     * Sets the snapshot ID.
     * @param id the ID to set
     */
    public void setId(String id) { this.id = id; }

    /**
     * Gets the window start time.
     * @return the window start
     */
    public Instant getWindowStart() { return windowStart; }
    /**
     * Sets the window start time.
     * @param windowStart the window start to set
     */
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }

    /**
     * Gets the window end time.
     * @return the window end
     */
    public Instant getWindowEnd() { return windowEnd; }
    /**
     * Sets the window end time.
     * @param windowEnd the window end to set
     */
    public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }

    /**
     * Gets the group type (TEAM, NAMESPACE, or APP).
     * @return the group type
     */
    public String getGroupType() { return groupType; }
    /**
     * Sets the group type.
     * @param groupType the group type to set
     */
    public void setGroupType(String groupType) { this.groupType = groupType; }

    /**
     * Gets the group key.
     * @return the group key
     */
    public String getGroupKey() { return groupKey; }
    /**
     * Sets the group key.
     * @param groupKey the group key to set
     */
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }

    /**
     * Gets the CPU usage in millicores.
     * @return the CPU usage
     */
    public long getCpuMcpu() { return cpuMcpu; }
    /**
     * Sets the CPU usage in millicores.
     * @param cpuMcpu the CPU usage to set
     */
    public void setCpuMcpu(long cpuMcpu) { this.cpuMcpu = cpuMcpu; }

    /**
     * Gets the memory usage in MiB.
     * @return the memory usage
     */
    public long getMemMib() { return memMib; }
    /**
     * Sets the memory usage in MiB.
     * @param memMib the memory usage to set
     */
    public void setMemMib(long memMib) { this.memMib = memMib; }

    /**
     * Gets the CPU cost in units.
     * @return the CPU cost
     */
    public double getCpuCostUnits() { return cpuCostUnits; }
    /**
     * Sets the CPU cost in units.
     * @param cpuCostUnits the CPU cost to set
     */
    public void setCpuCostUnits(double cpuCostUnits) { this.cpuCostUnits = cpuCostUnits; }

    /**
     * Gets the memory cost in units.
     * @return the memory cost
     */
    public double getMemCostUnits() { return memCostUnits; }
    /**
     * Sets the memory cost in units.
     * @param memCostUnits the memory cost to set
     */
    public void setMemCostUnits(double memCostUnits) { this.memCostUnits = memCostUnits; }

    /**
     * Gets the total cost in units.
     * @return the total cost
     */
    public double getTotalCostUnits() { return totalCostUnits; }
    /**
     * Sets the total cost in units.
     * @param totalCostUnits the total cost to set
     */
    public void setTotalCostUnits(double totalCostUnits) { this.totalCostUnits = totalCostUnits; }
}
