#!/bin/zsh
set -euo pipefail

MODEL_ROOT="${NUS_AI_MODEL_ROOT:-$HOME/.local/share/nus-ai}"
MODEL_ZIP="${MODEL_ROOT}/vosk-model-small-cn-0.22.zip"
MODEL_DIR="${MODEL_ROOT}/vosk-model-small-cn-0.22"
MODEL_URL="https://hf-mirror.com/localstack/vosk-models/resolve/main/vosk-model-small-cn-0.22.zip"

mkdir -p "${MODEL_ROOT}"

while [ ! -d "${MODEL_DIR}" ]; do
  curl --fail --retry 20 --retry-delay 5 -C - -L -o "${MODEL_ZIP}" "${MODEL_URL}" || true
  if unzip -tq "${MODEL_ZIP}" >/dev/null 2>&1; then
    rm -rf "${MODEL_DIR}"
    unzip -q -o "${MODEL_ZIP}" -d "${MODEL_ROOT}"
    rm -f "${MODEL_ZIP}"
    break
  fi
  sleep 15
done

echo "vosk model ready: ${MODEL_DIR}"
