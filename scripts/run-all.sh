#!/bin/bash
# run-all.sh
# Script starts API and Frontend.

# Determine the script directory and the project root (relative)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
LOG_DIR="$PROJECT_ROOT"

echo "1. Killing old processes..."
pkill -f "quarkus" || true
pkill -f "vite" || true
pkill -f "node" || true
sleep 2

echo "2. Configuring environment..."
# Use the default config if the variable is not set
export KUBECONFIG="${KUBECONFIG:-$HOME/.kube/config}"
export RATE_CPU_MCPU_HOUR=0.001
export RATE_MEM_MIB_HOUR=0.0001
export DASHBOARD_URL=http://localhost:5173

echo "2.5. Building dependencies..."
cd "$PROJECT_ROOT" || exit
./mvnw install -DskipTests

echo "3. Starting API (Backend)..."
cd "$PROJECT_ROOT/chargeback-api" || exit
# Run mvnw from chargeback-api, pointing to the wrapper in the parent (../mvnw)
nohup ../mvnw quarkus:dev > "$LOG_DIR/api.log" 2>&1 &

echo "Waiting for API to start (this may take up to 30s)..."
# Wait for Quarkus to respond
COUNT=0
while ! curl -s http://localhost:8080/q/health/live > /dev/null; do
  echo -n "."
  sleep 2
  COUNT=$((COUNT+1))
  if [ $COUNT -gt 30 ]; then
    echo "Error: Backend did not start within 60 seconds. Check api.log"
    exit 1
  fi
done
echo -e "\nBackend ready!"

echo "4. Starting Dashboard (Frontend)..."
cd "$PROJECT_ROOT/chargeback-ui" || exit
nohup npm run dev -- --host > "$LOG_DIR/ui.log" 2>&1 &

echo "------------------------------------------------"
echo "ALL SERVICES STARTED!"
echo "FRONTEND: http://localhost:5173"
echo "BACKEND API: http://localhost:8080"
echo "------------------------------------------------"
echo "Logs can be found in: $LOG_DIR/api.log and $LOG_DIR/ui.log"
