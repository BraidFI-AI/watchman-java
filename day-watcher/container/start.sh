#!/bin/bash
# Start script: Launch Java Watchman, then Python worker
set -e

echo "=== Day Watcher Container Start ==="
echo "Run ID: $RUN_ID"
echo "Input: s3://$INPUT_BUCKET/$INPUT_KEY"
echo "Results: s3://$RESULTS_BUCKET"
echo "Chunk size: $CHUNK_SIZE"

# Start Java Watchman in background
echo "Starting Java Watchman on port $JAVA_WATCHMAN_PORT..."
java -jar /app/watchman.jar \
  --server.port=$JAVA_WATCHMAN_PORT \
  --logging.level.com.moov.watchman=INFO &

JAVA_PID=$!
echo "Java Watchman PID: $JAVA_PID"

# Wait for Java Watchman to be ready
echo "Waiting for Java Watchman to load OFAC lists..."
RETRIES=60
READY=false

for i in $(seq 1 $RETRIES); do
  sleep 2
  if curl -sf http://localhost:$JAVA_WATCHMAN_PORT/health > /dev/null 2>&1; then
    READY=true
    echo "Java Watchman ready after $((i*2)) seconds"
    break
  fi
  echo "Waiting for Watchman... ($i/$RETRIES)"
done

if [ "$READY" = false ]; then
  echo "ERROR: Java Watchman failed to start within $((RETRIES*2)) seconds"
  kill $JAVA_PID 2>/dev/null || true
  exit 1
fi

# Start Python worker (foreground)
echo "Starting Python batch worker..."
python3 /app/batch_worker.py

# Capture worker exit code
WORKER_EXIT=$?

# Shutdown Java Watchman
echo "Shutting down Java Watchman..."
kill $JAVA_PID 2>/dev/null || true
wait $JAVA_PID 2>/dev/null || true

echo "=== Day Watcher Container Complete (exit code: $WORKER_EXIT) ==="
exit $WORKER_EXIT
