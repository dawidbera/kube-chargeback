#!/bin/bash
# start-app.sh
# Script starts Collector, API, and Frontend.

# Determine the script directory and the project root (relative)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Logs will be saved in the project root
LOG_DIR="$PROJECT_ROOT"

echo "--- Stopping existing processes ---"
pkill -f "chargeback-api" || true
pkill -f "vite" || true

echo "--- Preparing Environment ---"
export KUBECONFIG="${KUBECONFIG:-$HOME/.kube/config}"
export RATE_CPU_MCPU_HOUR=0.001
export RATE_MEM_MIB_HOUR=0.0001
export DASHBOARD_URL=http://localhost:5173

echo "--- Running Collector (Populating Data) ---"
cd "$PROJECT_ROOT" || exit
./mvnw -f chargeback-collector/pom.xml quarkus:run

echo "--- Starting API (Backend) on port 8080 ---"
# Run in background and redirect logs
nohup ./mvnw -f chargeback-api/pom.xml quarkus:dev > "$LOG_DIR/api.log" 2>&1 &

echo "--- Starting Dashboard (Frontend) on port 5173 ---"
cd chargeback-ui || exit
# Run in background and redirect logs
nohup npm run dev > "$LOG_DIR/ui.log" 2>&1 &

echo "------------------------------------------------"
echo "Application started!"
echo "Backend: http://localhost:8080"
echo "Frontend: http://localhost:5173"
echo "------------------------------------------------"
echo "Waiting for backend to be ready..."
sleep 10
echo "Done. Please refresh your browser."
