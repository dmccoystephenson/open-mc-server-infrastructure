# Open MC Server Infrastructure — Claude Code Guide

## What This Repo Is

**OMCSI** is a community-agnostic Minecraft server infrastructure project. It provides everything needed to run a Spigot-based Minecraft server, including web dashboard, backups, alerts, and an AI bot. The project ships two first-class deployment targets that must always work in parallel:

| Target | Entry point | Config source |
|---|---|---|
| **Docker Compose** | `./up.sh` / `compose.yml` | `.env` (copied from `sample.env`) |
| **Kubernetes (Helm)** | `terraform apply` or `helm upgrade` | Kubernetes Secrets + `values.yaml` / `values-override.yaml` |

## Branching and PRs

Branch protection is enforced on `main` for all users — direct pushes are blocked. Always work on a feature/fix branch and open a PR. Never attempt to push directly to `main`.

## Critical Rule: Both Deployment Targets Must Always Work

Every change to this repo — new service, new config, dependency update, persistence change, networking change — **must be implemented and verified for both Docker Compose and Kubernetes**. Never leave one target behind.

Checklist for any PR touching services, config, or infrastructure:
- [ ] `compose.yml` updated (new service, volume, env var, `depends_on`, etc.)
- [ ] `sample.env` updated with any new environment variables
- [ ] Helm chart updated (`helm/omcsi/templates/` and `values.yaml`) with equivalent changes
- [ ] NetworkPolicies updated if new inter-service traffic paths are introduced (see below)
- [ ] Both deployment paths verified functional (see Verification section)

## Project Structure

| Path | Purpose |
|---|---|
| `compose.yml` | Docker Compose service definitions |
| `sample.env` | Template for all environment variables (Docker) |
| `up.sh` / `down.sh` | Docker Compose lifecycle scripts |
| `helm/omcsi/` | Helm chart for Kubernetes deployment |
| `terraform/hetzner/` | Terraform config that provisions one Hetzner server, self-manages a single-node kubeadm cluster on it (cloud-init), and deploys the Helm chart — the cheapest cloud target (~$14/mo). Deploys via SSH from the node (not the Helm provider) since a kubeadm node has no API credentials as resource attributes at plan time. |
| `terraform/linode/` | Terraform config that provisions LKE and deploys the Helm chart |
| `terraform/aws/` | Terraform config that provisions a VPC and EKS cluster and deploys the Helm chart |
| `terraform/existing-cluster/` | Terraform config that deploys the Helm chart into a cluster you already run, configured from a supplied kubeconfig path/context |
| `terraform/modules/` | Shared modules (`omcsi-helm`, `traefik`) consumed by the targets above |
| `<service>/` | One directory per service (source code, Dockerfile) |
| `scripts/ci-local.sh` | Local CI validation script |

### Services

| Service | Port | Description |
|---|---|---|
| `minecraft-wrapper` | 8092 | Spigot process manager and REST API |
| `web-app` | 8080 | Admin dashboard (Spring Boot) |
| `nginx` | 80 / 443 | Reverse proxy |
| `alert-manager` | 8090 | Discord webhook notifications |
| `backup-manager` | 8091 | Scheduled backups |
| `agent-manager` | 8093 (API), 8094 (Spring management/actuator, container-internal) | Discord AI bot (disabled by default) |

## Verification Before Marking Work Done

Do not mark a task complete until both targets have been verified. Use the following checks:

### Docker Compose
```bash
# Validate config parses correctly
docker compose config

# Bring the stack up and confirm services are healthy
./up.sh
docker compose ps        # all services should show "healthy" or "running"
docker compose logs --tail=50 <service>   # check for startup errors
```

### Kubernetes / Helm
```bash
# Lint the chart. secrets.rconPassword and secrets.adminPassword are `required`
# in templates/secret.yaml and default to "", so lint fails without them —
# this is the exact invocation CI uses.
helm lint helm/omcsi --set secrets.rconPassword=ci --set secrets.adminPassword=ci

# Run unit tests (validates templates, NetworkPolicies, etc.)
helm unittest helm/omcsi

# Dry-run render to catch template errors. values-override.yaml is a local,
# gitignored file — drop the flag (and pass the two --set values above) if you
# do not have one.
helm template omcsi helm/omcsi --values values-override.yaml

# Apply to a cluster
source ./.env
terraform apply   # or: helm upgrade --install omcsi helm/omcsi ...
kubectl get pods -n omcsi   # all pods should reach Running/Ready
kubectl logs -n omcsi <pod>  # check for startup errors
```

### Terraform

CI runs `fmt -check -diff`, `init -backend=false`, and `validate` against **all four**
targets — `linode`, `aws`, `existing-cluster`, `hetzner` — on every PR. Because
`terraform/modules/` is shared, editing one target can break another, so check each:

```bash
for t in linode aws existing-cluster hetzner; do
  terraform -chdir=terraform/$t fmt -check -diff
  terraform -chdir=terraform/$t init -backend=false
  terraform -chdir=terraform/$t validate
done
```

### CI
```bash
./scripts/ci-local.sh
```

All of the above must pass before a PR is ready for review.

## Networking (Kubernetes)

NetworkPolicies are enforced on every service. Any new traffic path requires explicit rules in `helm/omcsi/templates/networkpolicies.yaml`. Without a matching rule, traffic is silently dropped.

Common cases requiring policy updates:
- New service calling another service → egress rule on caller + ingress rule on target
- Prometheus scraping a service → ingress rule gated by `monitoring.enabled` with cross-namespace `namespaceSelector`
- External HTTPS egress (webhooks, APIs) → egress to port 443 / `ipBlock: 0.0.0.0/0`

After editing, run `helm unittest helm/omcsi` to validate.

## Monitoring (Kubernetes only)

Prometheus scraping and a Grafana dashboard are optional and gated by `monitoring.enabled` (default `false`). Enabling requires the Prometheus Operator CRDs to be installed first (e.g. via `kube-prometheus-stack`).

**Enable monitoring on an existing release:**
```bash
helm upgrade omcsi helm/omcsi --namespace omcsi --reuse-values \
  --set-json 'monitoring={"enabled":true,"serviceMonitor":{"labels":{"release":"kube-prometheus-stack"},"interval":"30s","scrapeTimeout":"10s"},"grafanaDashboard":{"enabled":true,"labels":{"grafana_dashboard":"1"}},"prometheusNamespace":"monitoring"}'
```

The `--set-json` form is required for the `monitoring` map because `--reuse-values` does not inherit new map-type keys from `values.yaml` defaults.

**`imagePullPolicy` gotcha:** The initial Helm install bakes `imagePullPolicy: IfNotPresent` into the stored release. Subsequent `helm upgrade --reuse-values` calls preserve it, so new image pushes to Docker Hub are never pulled even after `kubectl rollout restart`. When deploying freshly built images, always add `--set '*.image.pullPolicy=Always'` (or ensure `values.yaml` defaults to `Always`).

**Grafana dashboard provisioning gotcha:** The dashboard ConfigMap is picked up by the Grafana sidecar. The sidecar does **not** process `__inputs` / `__requires` blocks or resolve `${DS_PROMETHEUS}` placeholders — those only work on manual import. Dashboard JSON must use the literal datasource UID (e.g. `"uid": "prometheus"`) and omit `__inputs`/`__requires`.

**Grafana access via Traefik:** Set `TF_VAR_enable_grafana_route=true` in `.env` to expose Grafana on port 3000 through the Traefik LoadBalancer. Also set `TF_VAR_grafana_service_address` if Grafana is deployed with a non-default release name or namespace.

## Secrets and Credentials

- **Docker**: credentials live in `.env` (gitignored), documented in `sample.env`. Never hardcode in `compose.yml`.
- **Kubernetes**: credentials live in Kubernetes Secrets managed by the Helm chart. Never put real credentials in `values.yaml` or any committed file. Pass them via `TF_VAR_*` env vars or a gitignored `values-override.yaml`.

## Adding a New Service

1. Create the service directory with a `Dockerfile`.
2. Add the service to `compose.yml` with appropriate `depends_on`, healthcheck, and volume mounts.
3. Add any new env vars to `sample.env`.
4. Add a Deployment, Service, and (if needed) PVC as a single flat template file `helm/omcsi/templates/<service>.yaml`, matching the sibling services — the chart has no per-service template subdirectories.
5. Add NetworkPolicy ingress/egress rules for the new service.
6. Expose any new config knobs in `values.yaml`.
7. Verify both deployment targets (see Verification section above).
