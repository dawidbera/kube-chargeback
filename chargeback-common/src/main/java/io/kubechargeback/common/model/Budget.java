package io.kubechargeback.common.model;

import java.time.Instant;
import java.util.UUID;

public class Budget {
    private String id;
    private String name;
    private String selectorType; // TEAM|NAMESPACE|LABEL
    private String selectorKey;
    private String selectorValue;
    private String period; // DAILY|WEEKLY|MONTHLY
    private long cpuMcpuLimit;
    private long memMibLimit;
    private int warnPercent = 80;
    private boolean enabled = true;
    private String webhookSecretName;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Default constructor that initializes ID, createdAt, and updatedAt.
     */
    public Budget() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Gets the budget ID.
     * @return the ID
     */
    public String getId() { return id; }
    /**
     * Sets the budget ID.
     * @param id the ID to set
     */
    public void setId(String id) { this.id = id; }

    /**
     * Gets the budget name.
     * @return the name
     */
    public String getName() { return name; }
    /**
     * Sets the budget name.
     * @param name the name to set
     */
    public void setName(String name) { this.name = name; }

    /**
     * Gets the selector type (TEAM, NAMESPACE, or LABEL).
     * @return the selector type
     */
    public String getSelectorType() { return selectorType; }
    /**
     * Sets the selector type.
     * @param selectorType the selector type to set
     */
    public void setSelectorType(String selectorType) { this.selectorType = selectorType; }

    /**
     * Gets the selector key (e.g., label name).
     * @return the selector key
     */
    public String getSelectorKey() { return selectorKey; }
    /**
     * Sets the selector key.
     * @param selectorKey the selector key to set
     */
    public void setSelectorKey(String selectorKey) { this.selectorKey = selectorKey; }

    /**
     * Gets the selector value.
     * @return the selector value
     */
    public String getSelectorValue() { return selectorValue; }
    /**
     * Sets the selector value.
     * @param selectorValue the selector value to set
     */
    public void setSelectorValue(String selectorValue) { this.selectorValue = selectorValue; }

    /**
     * Gets the budget period (DAILY, WEEKLY, or MONTHLY).
     * @return the period
     */
    public String getPeriod() { return period; }
    /**
     * Sets the budget period.
     * @param period the period to set
     */
    public void setPeriod(String period) { this.period = period; }

    /**
     * Gets the CPU limit in millicores.
     * @return the CPU limit
     */
    public long getCpuMcpuLimit() { return cpuMcpuLimit; }
    /**
     * Sets the CPU limit in millicores.
     * @param cpuMcpuLimit the CPU limit to set
     */
    public void setCpuMcpuLimit(long cpuMcpuLimit) { this.cpuMcpuLimit = cpuMcpuLimit; }

    /**
     * Gets the memory limit in MiB.
     * @return the memory limit
     */
    public long getMemMibLimit() { return memMibLimit; }
    /**
     * Sets the memory limit in MiB.
     * @param memMibLimit the memory limit to set
     */
    public void setMemMibLimit(long memMibLimit) { this.memMibLimit = memMibLimit; }

    /**
     * Gets the warning threshold percentage.
     * @return the warning percentage
     */
    public int getWarnPercent() { return warnPercent; }
    /**
     * Sets the warning threshold percentage.
     * @param warnPercent the warning percentage to set
     */
    public void setWarnPercent(int warnPercent) { this.warnPercent = warnPercent; }

    /**
     * Checks if the budget is enabled.
     * @return true if enabled
     */
    public boolean isEnabled() { return enabled; }
    /**
     * Sets whether the budget is enabled.
     * @param enabled true to enable
     */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /**
     * Gets the name of the secret containing the webhook URL.
     * @return the webhook secret name
     */
    public String getWebhookSecretName() { return webhookSecretName; }
    /**
     * Sets the name of the secret containing the webhook URL.
     * @param webhookSecretName the webhook secret name to set
     */
    public void setWebhookSecretName(String webhookSecretName) { this.webhookSecretName = webhookSecretName; }

    /**
     * Gets the creation timestamp.
     * @return the creation time
     */
    public Instant getCreatedAt() { return createdAt; }
    /**
     * Sets the creation timestamp.
     * @param createdAt the creation time to set
     */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /**
     * Gets the last update timestamp.
     * @return the update time
     */
    public Instant getUpdatedAt() { return updatedAt; }
    /**
     * Sets the last update timestamp.
     * @param updatedAt the update time to set
     */
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
