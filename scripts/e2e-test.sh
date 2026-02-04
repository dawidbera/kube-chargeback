#!/bin/bash
set -e

# Configuration
export KUBECONFIG=${KUBECONFIG:-$HOME/.kube/config}
API_URL="http://localhost:8080"
COLLECTOR_DIR="chargeback-collector"
SAMPLE_WORKLOADS="examples/sample-workloads.yaml"

echo "--- 1. Applying sample workloads ---"
kubectl delete -f $SAMPLE_WORKLOADS --ignore-not-found
kubectl apply -f $SAMPLE_WORKLOADS

echo "--- 2. Running Collector (once) ---"
# We force the collector to see everything by setting allowlist to empty (all) or specific
# and we use debug.window.use-current to see results immediately.
./mvnw -f $COLLECTOR_DIR/pom.xml quarkus:run -Dquarkus.args="--once" -Dnamespace.allowlist="*" -Ddebug.window.use-current=true

echo "--- 3. Verifying data via API ---"
# Check compliance/inventory to see what was parsed
INVENTORY=$(curl -s "$API_URL/api/v1/reports/compliance?from=2000-01-01T00:00:00Z&to=2100-01-01T00:00:00Z")
echo "Inventory: $INVENTORY"

RESPONSE=$(curl -s "$API_URL/api/v1/reports/allocations?from=2000-01-01T00:00:00Z&to=2100-01-01T00:00:00Z&groupBy=team")

echo "API Response: $RESPONSE"

# Check if team-a exists and has correct CPU (200mCPU for 2 replicas of 100mCPU)
if echo "$RESPONSE" | grep -q "\"groupKey\":\"team-a\"" && echo "$RESPONSE" | grep -q "\"cpuMcpu\":200"; then
    echo "SUCCESS: team-a found with 200mCPU"
else
    echo "FAILURE: team-a not found or incorrect data"
    exit 1
fi

echo "--- E2E Test Passed Successfully ---"
