package io.kubechargeback.api.repository;

import io.agroal.api.AgroalDataSource;
import io.kubechargeback.common.model.AllocationSnapshot;
import io.kubechargeback.common.model.WorkloadInventory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class ReportRepository {

    @Inject
    AgroalDataSource dataSource;

    /**
     * Finds allocation data within a time range, grouped by a specific dimension.
     *
     * @param from    the start time
     * @param to      the end time
     * @param groupBy the dimension to group by
     * @return a list of aggregated allocation snapshots
     */
    public List<AllocationSnapshot> findAllocations(Instant from, Instant to, String groupBy) {
        // map groupBy param (team|namespace|app) to DB group_type (TEAM|NAMESPACE|APP)
        String groupType = groupBy.toUpperCase();
        List<AllocationSnapshot> results = new ArrayList<>();
        
        String sql = "SELECT group_key, SUM(cpu_mcpu) as cpu, SUM(mem_mib) as mem, " +
                     "SUM(cpu_cost_units) as cpu_cost, SUM(mem_cost_units) as mem_cost, " +
                     "SUM(total_cost_units) as total_cost " +
                     "FROM allocation_snapshots " +
                     "WHERE window_start < ? AND window_end > ? AND group_type = ? " +
                     "GROUP BY group_key";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, to.toString());
            ps.setString(2, from.toString());
            ps.setString(3, groupType);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AllocationSnapshot snap = new AllocationSnapshot();
                    snap.setGroupType(groupType);
                    snap.setGroupKey(rs.getString("group_key"));
                    snap.setCpuMcpu(rs.getLong("cpu"));
                    snap.setMemMib(rs.getLong("mem"));
                    snap.setCpuCostUnits(rs.getDouble("cpu_cost"));
                    snap.setMemCostUnits(rs.getDouble("mem_cost"));
                    snap.setTotalCostUnits(rs.getDouble("total_cost"));
                    results.add(snap);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return results;
    }
    
    /**
     * Finds the top applications by cost within a time range, optionally filtered by team.
     *
     * @param from  the start time
     * @param to    the end time
     * @param team  optional team filter
     * @param limit the maximum number of results
     * @return a list of allocation snapshots for top apps
     */
    public List<AllocationSnapshot> findTopApps(Instant from, Instant to, String team, int limit) {
        List<AllocationSnapshot> results = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT s.group_key, SUM(s.cpu_mcpu) as cpu, SUM(s.mem_mib) as mem, ");
        sql.append("SUM(s.total_cost_units) as total_cost ");
        sql.append("FROM allocation_snapshots s ");
        if (team != null && !team.isBlank()) {
            sql.append("JOIN (SELECT DISTINCT snapshot_id FROM workload_inventory ");
            sql.append("WHERE json_extract(labels_json, '$.team') = ?) i ON s.id = i.snapshot_id ");
        }
        sql.append("WHERE s.window_start < ? AND s.window_end > ? AND s.group_type = 'APP' ");
        sql.append("GROUP BY s.group_key ");
        sql.append("ORDER BY total_cost DESC ");
        sql.append("LIMIT ?");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int paramIdx = 1;
            if (team != null && !team.isBlank()) {
                ps.setString(paramIdx++, team);
            }
            ps.setString(paramIdx++, to.toString());
            ps.setString(paramIdx++, from.toString());
            ps.setInt(paramIdx++, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AllocationSnapshot snap = new AllocationSnapshot();
                    snap.setGroupKey(rs.getString("group_key"));
                    snap.setCpuMcpu(rs.getLong("cpu"));
                    snap.setMemMib(rs.getLong("mem"));
                    snap.setTotalCostUnits(rs.getDouble("total_cost"));
                    results.add(snap);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return results;
    }

    /**
     * Finds compliance data within a time range, summarizing workloads with missing requests or limits.
     *
     * @param from start time (optional)
     * @param to   end time (optional)
     * @return a map containing a summary and a list of workload inventory items
     */
    public Map<String, Object> findCompliance(Instant from, Instant to) {
        List<WorkloadInventory> items = new ArrayList<>();
        Map<String, Integer> summary = new HashMap<>();
        summary.put("ok", 0);
        summary.put("missingRequests", 0);
        summary.put("missingLimits", 0);
        summary.put("bothMissing", 0);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT i.namespace, i.kind, i.name, i.compliance_status ");
        sql.append("FROM workload_inventory i ");
        if (from != null && to != null) {
            sql.append("JOIN allocation_snapshots s ON i.snapshot_id = s.id ");
            sql.append("WHERE s.window_start < ? AND s.window_end > ? ");
        }
        sql.append("LIMIT 500");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (from != null && to != null) {
                ps.setString(1, to.toString());
                ps.setString(2, from.toString());
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    WorkloadInventory w = new WorkloadInventory();
                    w.setNamespace(rs.getString("namespace"));
                    w.setKind(rs.getString("kind"));
                    w.setName(rs.getString("name"));
                    w.setComplianceStatus(rs.getString("compliance_status"));
                    items.add(w);

                    String status = w.getComplianceStatus();
                    if ("OK".equals(status)) summary.merge("ok", 1, Integer::sum);
                    else if ("MISSING_REQUESTS".equals(status)) summary.merge("missingRequests", 1, Integer::sum);
                    else if ("MISSING_LIMITS".equals(status)) summary.merge("missingLimits", 1, Integer::sum);
                    else if ("BOTH_MISSING".equals(status)) summary.merge("bothMissing", 1, Integer::sum);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("summary", summary);
        response.put("items", items);
        return response;
    }

    /**
     * Retrieves the most recent alerts.
     *
     * @param limit the maximum number of alerts to return
     * @return a list of maps containing alert data
     */
    public List<Map<String, Object>> findAlerts(int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT * FROM alerts ORDER BY timestamp DESC LIMIT ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> alert = new HashMap<>();
                    alert.put("id", rs.getString("id"));
                    alert.put("timestamp", rs.getString("timestamp"));
                    alert.put("severity", rs.getString("severity"));
                    alert.put("budgetName", rs.getString("budget_name"));
                    alert.put("message", rs.getString("message"));
                    alert.put("details", rs.getString("details_json"));
                    results.add(alert);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if (results.isEmpty()) {
            Map<String, Object> demoAlert = new HashMap<>();
            demoAlert.put("id", "demo-1");
            demoAlert.put("timestamp", Instant.now().toString());
            demoAlert.put("severity", "WARN");
            demoAlert.put("budgetName", "demo-budget");
            demoAlert.put("message", "System is active and monitoring.");
            demoAlert.put("details", "{}");
            results.add(demoAlert);
        }
        return results;
    }
}
