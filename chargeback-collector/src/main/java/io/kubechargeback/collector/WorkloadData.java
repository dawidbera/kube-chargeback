package io.kubechargeback.collector;

import java.util.Map;

public class WorkloadData {
    public String namespace;
    public String kind;
    public String name;
    public Map<String, String> labels;
    public long cpuReq;
    public long memReq;
    public double durationHours;
    public String complianceStatus;
}
