package io.kubechargeback.common.model;

public class WorkloadInventory {
    private String snapshotId;
    private String namespace;
    private String kind;
    private String name;
    private String labelsJson;
    private long cpuRequestMcpu;
    private long memRequestMib;
    private String complianceStatus;

    /**
     * Gets the snapshot ID.
     * @return the snapshot ID
     */
    public String getSnapshotId() { return snapshotId; }
    /**
     * Sets the snapshot ID.
     * @param snapshotId the snapshot ID to set
     */
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }

    /**
     * Gets the namespace.
     * @return the namespace
     */
    public String getNamespace() { return namespace; }
    /**
     * Sets the namespace.
     * @param namespace the namespace to set
     */
    public void setNamespace(String namespace) { this.namespace = namespace; }

    /**
     * Gets the workload kind.
     * @return the kind
     */
    public String getKind() { return kind; }
    /**
     * Sets the workload kind.
     * @param kind the kind to set
     */
    public void setKind(String kind) { this.kind = kind; }

    /**
     * Gets the workload name.
     * @return the name
     */
    public String getName() { return name; }
    /**
     * Sets the workload name.
     * @param name the name to set
     */
    public void setName(String name) { this.name = name; }

    /**
     * Gets the labels as a JSON string.
     * @return the labels JSON
     */
    public String getLabelsJson() { return labelsJson; }
    /**
     * Sets the labels JSON string.
     * @param labelsJson the labels JSON to set
     */
    public void setLabelsJson(String labelsJson) { this.labelsJson = labelsJson; }

    /**
     * Gets the CPU request in millicores.
     * @return the CPU request
     */
    public long getCpuRequestMcpu() { return cpuRequestMcpu; }
    /**
     * Sets the CPU request in millicores.
     * @param cpuRequestMcpu the CPU request to set
     */
    public void setCpuRequestMcpu(long cpuRequestMcpu) { this.cpuRequestMcpu = cpuRequestMcpu; }

    /**
     * Gets the memory request in MiB.
     * @return the memory request
     */
    public long getMemRequestMib() { return memRequestMib; }
    /**
     * Sets the memory request in MiB.
     * @param memRequestMib the memory request to set
     */
    public void setMemRequestMib(long memRequestMib) { this.memRequestMib = memRequestMib; }

    /**
     * Gets the compliance status.
     * @return the compliance status
     */
    public String getComplianceStatus() { return complianceStatus; }
    /**
     * Sets the compliance status.
     * @param complianceStatus the compliance status to set
     */
    public void setComplianceStatus(String complianceStatus) { this.complianceStatus = complianceStatus; }
}
