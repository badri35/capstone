# FinSecure Pay – Cloud-Native DevOps Pipeline

Transaction validation API (Spring Boot 4, Java 17) delivered through Jenkins → Docker → Kubernetes with security gates,
Prometheus/Grafana monitoring, Loki logging and email alerting.

Repository: https://github.com/badri35/capstone | Design document: [docs/DESIGN.md](docs/DESIGN.md) | Evidence: [evidence/](evidence/)

## API

```bash
curl localhost:8080/health
curl -X POST localhost:8080/validate -H 'Content-Type: application/json' \
  -d '{"transactionId":"TX-123456","sourceAccount":"ACC10000001","destinationAccount":"ACC20000002","amount":50,"currency":"USD"}'
```

`200 APPROVED` for valid input, `422 REJECTED` with every failed rule, `400` for malformed JSON.

## Build, test, run

```bash
mvn clean verify                 # 19 JUnit 5 tests + JaCoCo
mvn -Pquality clean verify       # adds SpotBugs gate
docker build --build-arg APP_VERSION=1.0.0 -t docker.io/badri3590/finsecure-pay:1.0.0 .
docker run -p 8080:8080 docker.io/badri3590/finsecure-pay:1.0.0
```

## Kubernetes

```bash
kubectl apply -f k8s/namespace.yaml -f k8s/        # Deployment (2 replicas), Service (LoadBalancer), ConfigMap, PDB
kubectl -n finsecure get pods,svc
kubectl -n finsecure delete pod <name>             # controller recreates it
kubectl -n finsecure rollout undo deploy/finsecure-pay
```

AWS EKS: `eksctl create cluster -f aws/eks-cluster.yaml`, then run the pipeline with `TARGET=cloud PUSH_IMAGE=true`. The image is published multi-arch (amd64 + arm64).

`Service` is `LoadBalancer`: on EKS/AKS/GKE it receives a public address; locally use `kubectl port-forward svc/finsecure-pay 18080:80`.

## CI/CD (Jenkinsfile)

Checkout → Build → Test → Package → Security Checks (SpotBugs ‖ Trivy fs) → Container Build → Image Scan (Trivy) → Push → Deploy.

* Parameters: `ACTION` (`deploy`/`rollback`), `TARGET` (`minikube`/`cloud`), `PUSH_IMAGE`, `NOTIFY_EMAIL`.
* Any HIGH/CRITICAL fixable finding or failing test stops the pipeline before Push/Deploy.
* A failed rollout runs `kubectl rollout undo` automatically; `ACTION=rollback` does it on demand.
* Registry credentials: Jenkins credential id `dockerhub-credentials` (never in git).

## Monitoring and logging

```bash
scripts/deploy-monitoring.sh                       # Prometheus, Alertmanager, Loki/Promtail, Grafana (random admin password in a Secret)
scripts/load-test.sh http://localhost:18080 bad 90 # trigger FinSecureHighRejectionRate
kubectl -n finsecure logs -l app=finsecure-pay -f  # application logs (JSON)
```

## Secrets

No secrets in git. Grafana password is generated into a Kubernetes Secret at deploy time; `PAYMENT_API_KEY` is created
with `scripts/create-secrets.sh`; registry credentials live in the Jenkins credential store.
