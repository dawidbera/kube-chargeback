-- 3.1 budgets
CREATE TABLE IF NOT EXISTS budgets (
  id TEXT PRIMARY KEY, -- UUID
  name TEXT UNIQUE NOT NULL,
  selector_type TEXT NOT NULL, -- TEAM|NAMESPACE|LABEL
  selector_key TEXT,
  selector_value TEXT,
  period TEXT NOT NULL, -- DAILY|WEEKLY|MONTHLY
  cpu_mcpu_limit INTEGER NOT NULL,
  mem_mib_limit INTEGER NOT NULL,
  warn_percent INTEGER NOT NULL DEFAULT 80,
  enabled INTEGER NOT NULL DEFAULT 1,
  webhook_secret_name TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

-- 3.2 allocation_snapshots
CREATE TABLE IF NOT EXISTS allocation_snapshots (
  id TEXT PRIMARY KEY, -- UUID
  window_start TEXT NOT NULL, -- ISO-8601
  window_end TEXT NOT NULL,
  group_type TEXT NOT NULL, -- TEAM|NAMESPACE|APP
  group_key TEXT NOT NULL,
  cpu_mcpu INTEGER NOT NULL,
  mem_mib INTEGER NOT NULL,
  cpu_cost_units REAL NOT NULL,
  mem_cost_units REAL NOT NULL,
  total_cost_units REAL NOT NULL,
  UNIQUE(window_start, window_end, group_type, group_key)
);

-- 3.3 workload_inventory
CREATE TABLE IF NOT EXISTS workload_inventory (
  snapshot_id TEXT NOT NULL,
  namespace TEXT NOT NULL,
  kind TEXT NOT NULL,
  name TEXT NOT NULL,
  labels_json TEXT NOT NULL,
  cpu_request_mcpu INTEGER NOT NULL,
  mem_request_mib INTEGER NOT NULL,
  compliance_status TEXT NOT NULL
);

-- 3.4 alerts
CREATE TABLE IF NOT EXISTS alerts (
  id TEXT PRIMARY KEY,
  timestamp TEXT NOT NULL,
  severity TEXT NOT NULL,
  budget_name TEXT NOT NULL,
  message TEXT NOT NULL,
  details_json TEXT
);
