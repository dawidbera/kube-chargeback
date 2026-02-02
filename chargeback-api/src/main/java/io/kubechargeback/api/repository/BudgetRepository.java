package io.kubechargeback.api.repository;

import io.agroal.api.AgroalDataSource;
import io.kubechargeback.common.model.Budget;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class BudgetRepository {

    @Inject
    AgroalDataSource dataSource;

    /**
     * Retrieves all budgets from the database.
     *
     * @return a list of all budgets
     */
    public List<Budget> findAll() {
        List<Budget> budgets = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM budgets")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    budgets.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return budgets;
    }

    /**
     * Retrieves a specific budget by ID.
     *
     * @param id the budget ID
     * @return an optional containing the budget if found
     */
    public Optional<Budget> findById(String id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM budgets WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    /**
     * Persists a new budget to the database.
     *
     * @param budget the budget to create
     */
    public void create(Budget budget) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO budgets (id, name, selector_type, selector_key, selector_value, period, " +
                             "cpu_mcpu_limit, mem_mib_limit, warn_percent, enabled, webhook_secret_name, created_at, updated_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, budget.getId());
            ps.setString(2, budget.getName());
            ps.setString(3, budget.getSelectorType());
            ps.setString(4, budget.getSelectorKey());
            ps.setString(5, budget.getSelectorValue());
            ps.setString(6, budget.getPeriod());
            ps.setLong(7, budget.getCpuMcpuLimit());
            ps.setLong(8, budget.getMemMibLimit());
            ps.setInt(9, budget.getWarnPercent());
            ps.setInt(10, budget.isEnabled() ? 1 : 0);
            ps.setString(11, budget.getWebhookSecretName());
            ps.setString(12, budget.getCreatedAt().toString());
            ps.setString(13, budget.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Updates an existing budget in the database.
     *
     * @param budget the budget to update
     */
    public void update(Budget budget) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE budgets SET name=?, selector_type=?, selector_key=?, selector_value=?, period=?, " +
                             "cpu_mcpu_limit=?, mem_mib_limit=?, warn_percent=?, enabled=?, webhook_secret_name=?, updated_at=? " +
                             "WHERE id=?")) {
            ps.setString(1, budget.getName());
            ps.setString(2, budget.getSelectorType());
            ps.setString(3, budget.getSelectorKey());
            ps.setString(4, budget.getSelectorValue());
            ps.setString(5, budget.getPeriod());
            ps.setLong(6, budget.getCpuMcpuLimit());
            ps.setLong(7, budget.getMemMibLimit());
            ps.setInt(8, budget.getWarnPercent());
            ps.setInt(9, budget.isEnabled() ? 1 : 0);
            ps.setString(10, budget.getWebhookSecretName());
            ps.setString(11, Instant.now().toString());
            ps.setString(12, budget.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deletes a budget by ID from the database.
     *
     * @param id the budget ID
     */
    public void delete(String id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM budgets WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Maps a database result set row to a Budget object.
     *
     * @param rs the result set
     * @return the mapped Budget object
     * @throws SQLException if a database access error occurs
     */
    private Budget mapRow(ResultSet rs) throws SQLException {
        Budget b = new Budget();
        b.setId(rs.getString("id"));
        b.setName(rs.getString("name"));
        b.setSelectorType(rs.getString("selector_type"));
        b.setSelectorKey(rs.getString("selector_key"));
        b.setSelectorValue(rs.getString("selector_value"));
        b.setPeriod(rs.getString("period"));
        b.setCpuMcpuLimit(rs.getLong("cpu_mcpu_limit"));
        b.setMemMibLimit(rs.getLong("mem_mib_limit"));
        b.setWarnPercent(rs.getInt("warn_percent"));
        b.setEnabled(rs.getInt("enabled") == 1);
        b.setWebhookSecretName(rs.getString("webhook_secret_name"));
        b.setCreatedAt(Instant.parse(rs.getString("created_at")));
        b.setUpdatedAt(Instant.parse(rs.getString("updated_at")));
        return b;
    }
}
