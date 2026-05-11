# Open MC Server Infrastructure — Claude Code Guide

## What This Repo Is

**OMCSI** is a community-agnostic Minecraft server infrastructure project. It provides everything needed to run a Spigot-based Minecraft server, including web dashboard, backups, alerts, and an AI bot. The project ships two first-class deployment targets that must always work in parallel:

| Target | Entry point | Config source |
|---|---|---|
| **Docker Compose** | `./up.sh` / `compose.yml` | `.env` (copied from `sample.env`) |
| **Kubernetes (Helm)** | `terraform apply` or `helm upgrade` | Kubernetes Secrets + `values.yaml` / `values-override.yaml` |

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
| `terraform/linode/` | Terraform config that provisions LKE and deploys the Helm chart |
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
| `agent-manager` | 8093 / 8094 | Discord AI bot (disabled by default) |

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
# Lint the chart
helm lint helm/omcsi

# Run unit tests (validates templates, NetworkPolicies, etc.)
helm unittest helm/omcsi

# Dry-run render to catch template errors
helm template omcsi helm/omcsi --values values-override.yaml

# Apply to a cluster
source ./.env
terraform apply   # or: helm upgrade --install omcsi helm/omcsi ...
kubectl get pods -n omcsi   # all pods should reach Running/Ready
kubectl logs -n omcsi <pod>  # check for startup errors
```

### CI
```bash
./scripts/ci-local.sh
```

All three must pass before a PR is ready for review.

## Networking (Kubernetes)

NetworkPolicies are enforced on every service. Any new traffic path requires explicit rules in `helm/omcsi/templates/networkpolicies.yaml`. Without a matching rule, traffic is silently dropped.

Common cases requiring policy updates:
- New service calling another service → egress rule on caller + ingress rule on target
- Prometheus scraping a service → ingress rule gated by `monitoring.enabled` with cross-namespace `namespaceSelector`
- External HTTPS egress (webhooks, APIs) → egress to port 443 / `ipBlock: 0.0.0.0/0`

After editing, run `helm unittest helm/omcsi` to validate.

## Secrets and Credentials

- **Docker**: credentials live in `.env` (gitignored), documented in `sample.env`. Never hardcode in `compose.yml`.
- **Kubernetes**: credentials live in Kubernetes Secrets managed by the Helm chart. Never put real credentials in `values.yaml` or any committed file. Pass them via `TF_VAR_*` env vars or a gitignored `values-override.yaml`.

## Adding a New Service

1. Create the service directory with a `Dockerfile`.
2. Add the service to `compose.yml` with appropriate `depends_on`, healthcheck, and volume mounts.
3. Add any new env vars to `sample.env`.
4. Add a Deployment, Service, and (if needed) PVC template under `helm/omcsi/templates/<service>/`.
5. Add NetworkPolicy ingress/egress rules for the new service.
6. Expose any new config knobs in `values.yaml`.
7. Verify both deployment targets (see Verification section above).
