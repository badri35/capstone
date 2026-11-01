#!/bin/bash
# Deploy Prometheus, Alertmanager, Loki/Promtail and Grafana into the current cluster.
# Secrets are generated at deploy time and never stored in git.
set -euo pipefail
cd "$(dirname "$0")/.."

kubectl apply -f monitoring/prometheus.yaml

if ! kubectl -n monitoring get secret grafana-admin >/dev/null 2>&1; then
  kubectl -n monitoring create secret generic grafana-admin \
    --from-literal=user=admin \
    --from-literal=password="$(openssl rand -base64 18 | tr -d '/+=')"
fi

# SMTP target for Alertmanager: local Mailpit when reachable on the minikube docker network, else $SMTP_HOST
if [ -z "${SMTP_HOST:-}" ]; then
  docker network connect minikube mailpit >/dev/null 2>&1 || true
  SMTP_HOST="$(docker inspect -f '{{ (index .NetworkSettings.Networks "minikube").IPAddress }}' mailpit)"
fi
sed "s|\${SMTP_HOST}|$SMTP_HOST|g" monitoring/alertmanager.yaml | kubectl apply -f -

kubectl apply -f monitoring/loki-promtail.yaml -f monitoring/grafana.yaml
kubectl -n monitoring rollout status deploy/prometheus deploy/alertmanager deploy/loki deploy/grafana --timeout=240s
kubectl -n monitoring get pods
