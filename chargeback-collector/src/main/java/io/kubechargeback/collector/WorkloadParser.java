package io.kubechargeback.collector;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class WorkloadParser {

    /**
     * Parses a Deployment into WorkloadData.
     *
     * @param d the Deployment to parse
     * @return the parsed WorkloadData
     */
    public WorkloadData fromDeployment(Deployment d) {
        WorkloadData w = new WorkloadData();
        w.namespace = d.getMetadata().getNamespace();
        w.kind = "Deployment";
        w.name = d.getMetadata().getName();
        w.labels = d.getMetadata().getLabels() != null ? d.getMetadata().getLabels() : Map.of();
        
        int replicas = d.getSpec().getReplicas() != null ? d.getSpec().getReplicas() : 1;
        parseResources(w, d.getSpec().getTemplate().getSpec().getContainers(), replicas);
        return w;
    }

    /**
     * Parses a StatefulSet into WorkloadData.
     *
     * @param s the StatefulSet to parse
     * @return the parsed WorkloadData
     */
    public WorkloadData fromStatefulSet(io.fabric8.kubernetes.api.model.apps.StatefulSet s) {
        WorkloadData w = new WorkloadData();
        w.namespace = s.getMetadata().getNamespace();
        w.kind = "StatefulSet";
        w.name = s.getMetadata().getName();
        w.labels = s.getMetadata().getLabels() != null ? s.getMetadata().getLabels() : Map.of();

        int replicas = s.getSpec().getReplicas() != null ? s.getSpec().getReplicas() : 1;
        parseResources(w, s.getSpec().getTemplate().getSpec().getContainers(), replicas);
        return w;
    }

    /**
     * Parses a DaemonSet into WorkloadData.
     *
     * @param ds the DaemonSet to parse
     * @return the parsed WorkloadData
     */
    public WorkloadData fromDaemonSet(io.fabric8.kubernetes.api.model.apps.DaemonSet ds) {
        WorkloadData w = new WorkloadData();
        w.namespace = ds.getMetadata().getNamespace();
        w.kind = "DaemonSet";
        w.name = ds.getMetadata().getName();
        w.labels = ds.getMetadata().getLabels() != null ? ds.getMetadata().getLabels() : Map.of();

        // For DaemonSets, replicas equals the number of nodes it runs on.
        // We use desiredNumberScheduled as it represents the footprint the cluster intends to have.
        int replicas = ds.getStatus() != null ? ds.getStatus().getDesiredNumberScheduled() : 0;
        parseResources(w, ds.getSpec().getTemplate().getSpec().getContainers(), replicas);
        return w;
    }

    /**
     * Parses a Job into WorkloadData, calculating effective duration within the window.
     *
     * @param j           the Job to parse
     * @param windowStart the start of the collection window
     * @param windowEnd   the end of the collection window
     * @return the parsed WorkloadData
     */
    public WorkloadData fromJob(Job j, Instant windowStart, Instant windowEnd) {
        WorkloadData w = new WorkloadData();
        w.namespace = j.getMetadata().getNamespace();
        w.kind = "Job";
        w.name = j.getMetadata().getName();
        w.labels = j.getMetadata().getLabels() != null ? j.getMetadata().getLabels() : Map.of();

        Instant startTime = null;
        Instant completionTime = null;

        if (j.getStatus() != null) {
            if (j.getStatus().getStartTime() != null) {
                startTime = Instant.parse(j.getStatus().getStartTime());
            }
            if (j.getStatus().getCompletionTime() != null) {
                completionTime = Instant.parse(j.getStatus().getCompletionTime());
            }
        }

        if (startTime == null) {
             w.durationHours = 0;
        } else {
             Instant effectiveStart = startTime.isBefore(windowStart) ? windowStart : startTime;
             Instant effectiveEnd = (completionTime == null || completionTime.isAfter(windowEnd)) ? windowEnd : completionTime;
             
             if (effectiveEnd.isBefore(effectiveStart)) {
                 w.durationHours = 0;
             } else {
                 long seconds = Duration.between(effectiveStart, effectiveEnd).getSeconds();
                 w.durationHours = (double) seconds / 3600.0;
             }
        }

        int parallelism = j.getSpec().getParallelism() != null ? j.getSpec().getParallelism() : 1;
        parseResources(w, j.getSpec().getTemplate().getSpec().getContainers(), parallelism);
        return w;
    }

    /**
     * Parses resource requirements from a list of containers and updates WorkloadData.
     *
     * @param w          the WorkloadData to update
     * @param containers the list of containers to parse
     * @param replicas   the number of replicas
     */
    private void parseResources(WorkloadData w, List<Container> containers, int replicas) {
        long totalCpuReq = 0;
        long totalMemReq = 0;
        boolean missingReq = false;
        boolean missingLim = false;

        for (Container c : containers) {
            ResourceRequirements res = c.getResources();
            Map<String, Quantity> requests = (res != null) ? res.getRequests() : null;
            Map<String, Quantity> limits = (res != null) ? res.getLimits() : null;

            if (requests == null || !requests.containsKey("cpu") || !requests.containsKey("memory")) {
                missingReq = true;
            }
            if (requests != null) {
                totalCpuReq += parseCpu(requests.get("cpu"));
                totalMemReq += parseMem(requests.get("memory"));
            }

            if (limits == null || !limits.containsKey("cpu") || !limits.containsKey("memory")) {
                missingLim = true;
            }
        }

        w.cpuReq = totalCpuReq * replicas;
        w.memReq = totalMemReq * replicas;

        if (missingReq && missingLim) w.complianceStatus = "BOTH_MISSING";
        else if (missingReq) w.complianceStatus = "MISSING_REQUESTS";
        else if (missingLim) w.complianceStatus = "MISSING_LIMITS";
        else w.complianceStatus = "OK";
    }

    /**
     * Parses CPU quantity into millicores.
     *
     * @param q the CPU quantity
     * @return millicores
     */
    private long parseCpu(Quantity q) {
        if (q == null) return 0;
        // getNumericalAmount() handles suffixes like 'm' (milli) correctly.
        // 100m -> 0.1, 1 -> 1.0
        return (long) (q.getNumericalAmount().doubleValue() * 1000);
    }

    /**
     * Parses memory quantity into MiB.
     *
     * @param q the memory quantity
     * @return MiB
     */
    private long parseMem(Quantity q) {
        if (q == null) return 0;
        // returns MiB. getAmountInBytes() correctly handles Gi, Mi, Ki etc.
        return Quantity.getAmountInBytes(q).longValue() / (1024 * 1024);
    }
}
