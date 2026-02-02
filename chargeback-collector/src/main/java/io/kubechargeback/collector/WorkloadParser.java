package io.kubechargeback.collector;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import jakarta.enterprise.context.ApplicationScoped;

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
    public WorkloadData fromStatefulSet(StatefulSet s) {
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
        // returns millicores
        return (long) (Quantity.getAmountInBytes(q).doubleValue() * 1000);
    }

    /**
     * Parses memory quantity into MiB.
     *
     * @param q the memory quantity
     * @return MiB
     */
    private long parseMem(Quantity q) {
        if (q == null) return 0;
        // returns MiB
        return Quantity.getAmountInBytes(q).longValue() / (1024 * 1024);
    }
}
