#!/bin/bash
# Create runtime secrets directly in the cluster / Jenkins; nothing is written to git.
# usage: PAYMENT_API_KEY=... create-secrets.sh   (reads from the environment, never from a file in the repo)
set -euo pipefail
: "${PAYMENT_API_KEY:?set PAYMENT_API_KEY in the environment}"
kubectl -n finsecure create secret generic finsecure-pay-secrets \
  --from-literal=payment-api-key="$PAYMENT_API_KEY" \
  --dry-run=client -o yaml | kubectl apply -f -
