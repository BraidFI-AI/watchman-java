#!/bin/sh
# Pre-commit: Run Semgrep and Trivy before allowing commit

set -e

# Run Semgrep
if ! command -v semgrep >/dev/null 2>&1; then
  echo "[pre-commit] Installing Semgrep..."
  pip install semgrep
fi
semgrep --config auto --exclude .git --exclude target --exclude build

# Run Trivy (filesystem scan)
if ! command -v trivy >/dev/null 2>&1; then
  echo "[pre-commit] Installing Trivy..."
  sudo apt-get update && sudo apt-get install -y wget
  wget -qO- https://github.com/aquasecurity/trivy/releases/latest/download/trivy_0.50.2_Linux-64bit.deb | sudo dpkg -i -
fi
trivy fs --exit-code 1 --severity HIGH,CRITICAL --ignore-unfixed .
