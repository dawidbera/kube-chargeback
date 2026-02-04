package io.kubechargeback.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.kubechargeback.common.model.AllocationSnapshot;
import io.kubechargeback.common.model.Budget;
import io.kubechargeback.common.model.WorkloadInventory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@ApplicationScoped
public class CollectorService {

    private static final Logger LOG = Logger.getLogger(CollectorService.class);

    @Inject
    KubernetesClient k8s;

    @Inject
    CollectorConfig config;

    @Inject
    CollectorRepository repository;

    @Inject
    WorkloadParser parser;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Executes the collection process.
     */
    public void runCollection() {
        LOG.info("Starting collector run...");
        repository.initDb();

        // 1. Define Window (Previous full hour)
        Instant now = Instant.now();
        Instant windowEnd = now.truncatedTo(ChronoUnit.HOURS);
        Instant windowStart = windowEnd.minus(config.getWindowHours(), ChronoUnit.HOURS);
        
        LOG.infof("Window:  - ", windowStart, windowEnd);

        // 2. Fetch Workloads
        List<WorkloadData> workloads = new ArrayList<>();
        
        k8s.apps().deployments().inAnyNamespace().list().getItems().forEach(d -> {
            if (isAllowed(d.getMetadata().getNamespace())) {
                workloads.add(parser.fromDeployment(d));
            }
        });
        
        k8s.apps().statefulSets().inAnyNamespace().list().getItems().forEach(s -> {
            if (isAllowed(s.getMetadata().getNamespace())) {
                workloads.add(parser.fromStatefulSet(s));
            }
        });

        k8s.apps().daemonSets().inAnyNamespace().list().getItems().forEach(ds -> {
            if (isAllowed(ds.getMetadata().getNamespace())) {
                workloads.add(parser.fromDaemonSet(ds));
            }
        });

        k8s.batch().v1().jobs().inAnyNamespace().list().getItems().forEach(j -> {
            if (isAllowed(j.getMetadata().getNamespace())) {
                workloads.add(parser.fromJob(j, windowStart, windowEnd));
            }
        });

        // 3. Aggregate & Create Snapshots
        Map<String, AllocationSnapshot> teamSnapshots = new HashMap<>();
        Map<String, AllocationSnapshot> nsSnapshots = new HashMap<>();
        Map<String, AllocationSnapshot> appSnapshots = new HashMap<>();

        for (WorkloadData w : workloads) {
            // Aggregation logic
            double duration = w.durationHours > 0 ? w.durationHours : config.getWindowHours();
            double cpuCost = w.cpuReq * config.getRateCpu() * duration;
            double memCost = w.memReq * config.getRateMem() * duration;
            double totalCost = cpuCost + memCost;

            // TEAM
            String team = w.labels.getOrDefault(config.getLabelTeam(), "unknown");
            String teamSnapId = String.format("_TEAM_", windowStart, team);
            accumulate(teamSnapshots, "TEAM", team, w.cpuReq, w.memReq, cpuCost, memCost, totalCost, windowStart, windowEnd, teamSnapId);

            // NAMESPACE
            String nsSnapId = String.format("_NAMESPACE_", windowStart, w.namespace);
            accumulate(nsSnapshots, "NAMESPACE", w.namespace, w.cpuReq, w.memReq, cpuCost, memCost, totalCost, windowStart, windowEnd, nsSnapId);

            // APP
            String app = w.labels.getOrDefault(config.getLabelApp(), "unknown");
            String appSnapId = String.format("_APP_", windowStart, app);
            accumulate(appSnapshots, "APP", app, w.cpuReq, w.memReq, cpuCost, memCost, totalCost, windowStart, windowEnd, appSnapId);

            // Inventory
            WorkloadInventory inv = new WorkloadInventory();
            inv.setSnapshotId(appSnapId); // Link to APP snapshot for granular reporting
            inv.setNamespace(w.namespace);
            inv.setKind(w.kind);
            inv.setName(w.name);
            try {
                inv.setLabelsJson(mapper.writeValueAsString(w.labels));
            } catch (Exception e) { inv.setLabelsJson("{}"); }
            inv.setCpuRequestMcpu(w.cpuReq);
            inv.setMemRequestMib(w.memReq);
            inv.setComplianceStatus(w.complianceStatus);
            repository.saveInventory(inv);
        }

        // 4. Persist Snapshots
        persist(teamSnapshots);
        persist(nsSnapshots);
        persist(appSnapshots);

        // 5. Check Budgets
        checkBudgets(now);
        
        LOG.info("Collector run complete.");
    }

    /**
     * Checks if a namespace is allowed based on the allowlist configuration.
     *
     * @param namespace the namespace to check
     * @return true if allowed, false otherwise
     */
    private boolean isAllowed(String namespace) {
        if (config.getAllowlist().isPresent() && !config.getAllowlist().get().isBlank()) {
            String[] allowed = config.getAllowlist().get().split(",");
            for (String a : allowed) {
                if (a.trim().equals(namespace)) return true;
            }
            return false;
        }
        // Spec: empty means “current namespace only”
        String currentNamespace = k8s.getNamespace();
        return currentNamespace == null || currentNamespace.equals(namespace);
    }

    /**
     * Accumulates workload data into a snapshot.
     *
     * @param map       the map of snapshots
     * @param type      the group type
     * @param key       the group key
     * @param cpu       the CPU request in millicores
     * @param mem       the memory request in MiB
     * @param cpuCost   the calculated CPU cost
     * @param memCost   the calculated memory cost
     * @param totalCost the total calculated cost
     * @param start     the window start time
     * @param end       the window end time
     * @param id        the snapshot ID
     */
    private void accumulate(Map<String, AllocationSnapshot> map, String type, String key, 
                            long cpu, long mem, double cpuCost, double memCost, double totalCost,
                            Instant start, Instant end, String id) {
        AllocationSnapshot s = map.computeIfAbsent(key, k -> {
            AllocationSnapshot snap = new AllocationSnapshot();
            snap.setId(id);
            snap.setGroupType(type);
            snap.setGroupKey(key);
            snap.setWindowStart(start);
            snap.setWindowEnd(end);
            return snap;
        });
        s.setCpuMcpu(s.getCpuMcpu() + cpu);
        s.setMemMib(s.getMemMib() + mem);
        s.setCpuCostUnits(s.getCpuCostUnits() + cpuCost);
        s.setMemCostUnits(s.getMemCostUnits() + memCost);
        s.setTotalCostUnits(s.getTotalCostUnits() + totalCost);
    }

    /**
     * Persists a map of snapshots to the repository.
     *
     * @param map the map of snapshots
     */
    private void persist(Map<String, AllocationSnapshot> map) {
        for (AllocationSnapshot s : map.values()) {
            repository.saveSnapshot(s);
        }
    }

    /**
     * Checks budgets for a given point in time.
     *
     * @param now the current time
     */
    private void checkBudgets(Instant now) {
        List<Budget> budgets = repository.findAllEnabledBudgets();
        for (Budget b : budgets) {
            // Determine Period
            Instant start = null;
            Instant end = now;
            
            if ("DAILY".equals(b.getPeriod())) {
                start = now.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
            } else if ("WEEKLY".equals(b.getPeriod())) {
                 // ISO week starting Monday
                 // Simple approximation: go back to last Monday
                 start = now.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS) 
                         .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                         .toInstant();
            } else if ("MONTHLY".equals(b.getPeriod())) {
                start = now.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS)
                        .with(java.time.temporal.TemporalAdjusters.firstDayOfMonth())
                        .toInstant();
            }

            if (start != null) {
                AllocationSnapshot usage = repository.getUsageForBudget(b, start, end);
                checkThresholds(b, usage, start, end);
            }
        }
    }

    /**
     * Checks if usage exceeds budget thresholds.
     *
     * @param b     the budget
     * @param usage the calculated usage
     * @param start the period start
     * @param end   the period end
     */
    private void checkThresholds(Budget b, AllocationSnapshot usage, Instant start, Instant end) {
        double cpuPercent = (double) usage.getCpuMcpu() / b.getCpuMcpuLimit() * 100.0;
        double memPercent = (double) usage.getMemMib() / b.getMemMibLimit() * 100.0;
        
        String severity = null;
        if (cpuPercent >= 100 || memPercent >= 100) {
            severity = "CRITICAL";
        } else if (cpuPercent >= b.getWarnPercent() || memPercent >= b.getWarnPercent()) {
            severity = "WARN";
        }

        if (severity != null) {
            List<AllocationSnapshot> topOffenders = repository.getTopOffenders(b, start, end, 5);
            sendAlert(b, usage, severity, start, end, topOffenders);
        }
    }

    /**
     * Sends an alert for a budget violation.
     *
     * @param b             the budget
     * @param usage         the current usage
     * @param severity      the severity level
     * @param start         the period start
     * @param end           the period end
     * @param topOffenders  the list of top apps contributing to usage
     */
    private void sendAlert(Budget b, AllocationSnapshot usage, String severity, Instant start, Instant end, List<AllocationSnapshot> topOffenders) {
        String webhookUrl = getWebhookUrl(b.getWebhookSecretName());
        if (webhookUrl == null) {
            LOG.warnf("No webhook URL found for budget  (secret: )", b.getName(), b.getWebhookSecretName());
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("timestamp", Instant.now().toString());
            payload.put("severity", severity);
            payload.put("budgetId", b.getId());
            payload.put("budgetName", b.getName());
            payload.put("selectorType", b.getSelectorType());
            payload.put("selectorValue", b.getSelectorValue());
            payload.put("period", b.getPeriod());
            payload.put("periodStart", start.toString());
            payload.put("periodEnd", end.toString());
            payload.put("currentCpuMcpu", usage.getCpuMcpu());
            payload.put("currentMemMib", usage.getMemMib());
            payload.put("limitCpuMcpu", b.getCpuMcpuLimit());
            payload.put("limitMemMib", b.getMemMibLimit());

            if (config.getDashboardUrl() != null && !config.getDashboardUrl().isBlank()) {
                payload.put("dashboardUrl", config.getDashboardUrl());
            }

            List<Map<String, Object>> offendersList = new ArrayList<>();
            for (AllocationSnapshot off : topOffenders) {
                offendersList.add(Map.of(
                    "app", off.getGroupKey(),
                    "cpuMcpu", off.getCpuMcpu(),
                    "memMib", off.getMemMib(),
                    "totalCostUnits", off.getTotalCostUnits()
                ));
            }
            payload.put("topOffenders", offendersList);

            String json = mapper.writeValueAsString(payload);

            // Save to local database for Dashboard
            String alertId = UUID.randomUUID().toString();
            String message = String.format("Budget '%s' exceeded. Severity: %s", b.getName(), severity);
            repository.saveAlert(alertId, severity, b.getName(), message, json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(res -> {
                        if (res.statusCode() >= 300) {
                            LOG.errorf("Failed to send alert for budget : ", b.getName(), res.body());
                        }
                    })
                    .exceptionally(e -> {
                        LOG.errorf("Error sending alert for budget : ", b.getName(), e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            LOG.errorf("Error preparing alert for budget : ", b.getName(), e.getMessage());
        }
    }

    /**
     * Retrieves the webhook URL from a Kubernetes Secret.
     *
     * @param secretName the name of the secret
     * @return the webhook URL, or null if not found
     */
    private String getWebhookUrl(String secretName) {
        if (secretName == null || secretName.isBlank()) return null;
        try {
            Secret s = k8s.secrets().withName(secretName).get();
            if (s != null && s.getData() != null && s.getData().containsKey("webhook.url")) {
                return new String(Base64.getDecoder().decode(s.getData().get("webhook.url")));
            }
        } catch (Exception e) {
            LOG.errorf("Error fetching secret : ", secretName, e.getMessage());
        }
        return null;
    }
}
