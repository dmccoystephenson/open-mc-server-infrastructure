# Kubernetes Deployment — Potential Improvements

This document captures issues, limitations, and improvement opportunities surfaced during the initial Kubernetes/Helm work in [PR #128](https://github.com/dmccoystephenson/open-mc-server-infrastructure/pull/128). Each item is a candidate for a follow-up issue or PR.

---

## 1. Backup Manager — Docker CLI Dependency ✅ Resolved

**Problem:** The `backup-manager` created backups by shelling out to `docker run` to spin up an Ubuntu container that tars the mcserver volume. In Kubernetes, there is no Docker daemon on the nodes (or the pod doesn't have access to it), so this backup mechanism silently failed.

**Resolution:** The backup-manager now uses `tar` directly on the mounted filesystem. The mcserver PVC is already mounted at `/mcserver` inside the pod (in both Docker Compose and Kubernetes), so the service runs `tar czf` against that directory without requiring Docker CLI. The Docker socket mount has been removed from `compose.yml`, `docker.io` has been removed from the `Dockerfile`, and the Helm chart backup-manager is now fully enabled by default.

**Changes made:**
- `BackupService.java`: replaced `checkVolumeExists()` / `ensureUbuntuImageAvailable()` / `docker run` with a direct `tar` invocation on the `source.directory` mount point.
- `application.properties`: replaced `volume.name` and `host.backup.directory` with `source.directory`.
- `Dockerfile`: removed `docker.io` dependency.
- `compose.yml`: removed Docker socket mount, `HOST_BACKUP_DIRECTORY`, and `VOLUME_NAME` env vars.
- `helm/omcsi/values.yaml`: enabled scheduled backups (`SPRING_TASK_SCHEDULING_ENABLED: "true"`, `BACKUP_SCHEDULE: "0 0 2 * * ?"`), enabled alerts, replaced `VOLUME_NAME` with `SOURCE_DIRECTORY`.
- `helm/omcsi/templates/backup-manager.yaml`: replaced `VOLUME_NAME` env entry with `SOURCE_DIRECTORY`.

---

## 2. Health Probes — Missing for Several Services ✅ Resolved

**Problem:** Only `alert-manager`, `webapp`, and `nginx` had health probes defined. `minecraft-wrapper`, `backup-manager`, and `agent-manager` had no liveness, readiness, or startup probes.

**Resolution:** Health probes added to all services in [PR #153](https://github.com/dmccoystephenson/open-mc-server-infrastructure/pull/153):
- `minecraft-wrapper`: `startupProbe` with `failureThreshold: 60 × periodSeconds: 10` = 10 minutes (accommodates Spigot's build-on-first-run compilation), plus `livenessProbe` and `readinessProbe` on the wrapper API port.
- `backup-manager`: `livenessProbe` and `readinessProbe` on `/actuator/health`.
- `agent-manager`: `livenessProbe` and `readinessProbe` on `/actuator/health` via the dedicated management port (8094). The management port address restriction (`127.0.0.1`) was removed so the kubelet can reach the probe endpoint.

---

## 3. Security Contexts ✅ Resolved

**Problem:** No Deployment in the Helm chart sets `securityContext`, `runAsNonRoot`, or `readOnlyRootFilesystem`. All containers run as root by default, which is a security concern in multi-tenant clusters.

**Resolution:** Security contexts added to all Deployments across [PR #155](https://github.com/dmccoystephenson/open-mc-server-infrastructure/pull/155) and [PR #156](https://github.com/dmccoystephenson/open-mc-server-infrastructure/pull/156):
- **Pod-level** (`spec.securityContext`): `seccompProfile: RuntimeDefault` and `fsGroup: 1000` on every Deployment.
- **Container-level** (`spec.containers[].securityContext`): `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]`, `readOnlyRootFilesystem: true`, `runAsNonRoot: true`, and `runAsUser: 1000` on every container and init container.
- **nginx exception**: the nginx main container adds only `NET_BIND_SERVICE` (to bind ports 80/443 as UID 1000). `CHOWN`, `SETUID`, and `SETGID` are not needed because the container starts as a non-root user. The nginx binary has `CAP_NET_BIND_SERVICE` set at the file level (`setcap`) for Docker Compose compatibility.
- All Java services mount a `/tmp` emptyDir for Spring Boot's embedded Tomcat temp directory; nginx additionally mounts `/var/cache/nginx`. These are the only writable paths at runtime.
- All values are configurable via `podSecurityContext` and `containerSecurityContext` in `values.yaml`; nginx overrides via `nginx.containerSecurityContext`.

---

## 4. Ingress Controller Support

**Problem:** The chart currently exposes services via `NodePort` (Minecraft) and `LoadBalancer` (nginx). There is no Ingress resource, which means operators who prefer an ingress controller (e.g., nginx-ingress, Traefik, ALB Ingress Controller) must manually create their own routing.

**Suggested improvements:**
- Add an optional `Ingress` template (gated by `ingress.enabled`) with configurable annotations, TLS, and host rules.
- Support cert-manager annotations for automatic TLS certificate provisioning (replacing the self-signed init container).
- Document common ingress setups for each supported provider.

**Priority:** Medium — already identified as out of scope in the original issue; natural follow-up.

---

## 5. TLS Certificate Management

**Problem:** The nginx service uses a self-signed TLS certificate generated by an init container. This is fine for development but triggers browser warnings and is not suitable for production.

**Suggested improvements:**
- Document integration with [cert-manager](https://cert-manager.io/) for automatic Let's Encrypt certificates.
- When ingress support is added (item 4), TLS termination can move to the ingress controller, simplifying the nginx configuration.
- Consider adding a `Certificate` CRD template or documented annotations for cert-manager when `tls.selfSigned: false`.

**Priority:** Low — self-signed works for private/development servers; production setups typically have their own TLS strategy.

---

## 6. Shared PVC Scheduling Constraints

**Problem:** The mcserver PVC uses `ReadWriteOnce` (RWO), which means all pods mounting it must run on the same node. The chart uses `preferredDuringSchedulingIgnoredDuringExecution` pod affinity to encourage co-location, but this can fail on multi-node clusters if the node with the PVC has insufficient resources.

**Suggested improvements:**
- Document the option to use a `ReadWriteMany` (RWX) StorageClass (e.g., NFS, EFS, Longhorn) for multi-node flexibility.
- Consider a sidecar or init container pattern where the Minecraft wrapper is the sole PVC owner and exposes data via RCON/API, removing the need for other services to mount the same PVC.
- Evaluate whether the webapp truly needs direct filesystem access to `/mcserver` or could use the wrapper API instead.

**Priority:** Low — the current affinity rules work for typical 2-node clusters. Only relevant for larger or more constrained environments.

---

## 7. Horizontal Pod Autoscaler (HPA)

**Problem:** No HPA is configured for any service. The Minecraft server is intentionally limited to a single replica, but supporting services (webapp, nginx, alert-manager) could benefit from autoscaling under load.

**Suggested improvements:**
- Add optional HPA templates for `webapp`, `nginx`, and `alert-manager` (gated by `autoscaling.enabled` in `values.yaml`).
- Define sensible CPU/memory scaling thresholds.
- Document that `minecraft-wrapper` cannot be horizontally scaled (single-instance constraint).

**Priority:** Low — OMCSI is typically a small-scale deployment; autoscaling adds value for larger communities.

---

## 8. Network Policies ✅ Resolved

**Problem:** No `NetworkPolicy` resources are defined, so all pods can communicate with each other and with external endpoints without restriction.

**Resolution:** Per-service `NetworkPolicy` resources added to `helm/omcsi/templates/networkpolicies.yaml` in [PR #155](https://github.com/dmccoystephenson/open-mc-server-infrastructure/pull/155):
- Each service has a policy with `policyTypes: [Ingress, Egress]`, which implicitly denies any traffic not matched by an explicit allow rule.
- Ingress allow rules use `podSelector` wherever possible. Ports shared between internal callers and kubelet health probes include both a `podSelector` for each known caller and an `ipBlock` using the configurable `networkPolicy.kubeNodeCIDR` value (default `0.0.0.0/0`); set this to your cluster's node CIDR for tighter control. Purely internal ports (RCON 25575) are restricted to specific pod selectors only with no `ipBlock`.
- `alert-manager` ingress is restricted to minecraft-wrapper, webapp, backup-manager, and agent-manager pod selectors (plus `kubeNodeCIDR` for probes). `backup-manager` ingress is restricted to agent-manager (plus `kubeNodeCIDR`).
- `minecraft-wrapper` egress includes webapp (for `WEBAPP_URL` deployment-history notifications) in addition to alert-manager and DNS.
- DNS egress is scoped to CoreDNS pods in `kube-system` via configurable `networkPolicy.dnsNamespaceSelector` and `networkPolicy.dnsPodSelector` (defaults work for kubeadm, GKE, EKS, LKE). Falls back to unrestricted `0.0.0.0/0` when values are absent.
- External HTTPS egress (port 443) is allowed only for services that call Discord or the Anthropic API (alert-manager, agent-manager).
- The `agent-manager` policy is gated behind `agentManager.enabled` (same condition as the Deployment).
- Gated behind `networkPolicy.enabled` (default `true`); set `false` on clusters whose CNI does not support NetworkPolicy (e.g. Flannel without a policy controller).

---

## 9. Pod Disruption Budgets (PDB) ✅ Resolved

**Problem:** No `PodDisruptionBudget` resources were defined. During node maintenance or cluster upgrades, all pods (including the Minecraft server) could be evicted simultaneously.

**Resolution:** PDBs added to `helm/omcsi/templates/pdb.yaml` in [PR #153](https://github.com/dmccoystephenson/open-mc-server-infrastructure/pull/153):
- `minecraft-wrapper`: `minAvailable: 1` — prevents voluntary eviction of the live server.
- `webapp`: `minAvailable: 1` — keeps the admin dashboard available during drains.
- `alert-manager`: `minAvailable: 1` — prevents monitoring from going silent during maintenance.

All three PDBs use `policy/v1` (stable since Kubernetes 1.21).

---

## 10. Helm Chart Publishing

**Problem:** The chart is only available from the Git repository. Operators must clone the repo or reference the chart path directly.

**Suggested improvements:**
- Publish the chart to a Helm repository (e.g., GitHub Pages via `chart-releaser-action`, or an OCI registry).
- Add a CI job that packages and publishes the chart on tagged releases.
- Update README install instructions to support `helm install omcsi oci://ghcr.io/dmccoystephenson/omcsi` or similar.

**Priority:** Low — convenience improvement; the current `./helm/omcsi` path works for all deployment flows.

---

## 11. Monitoring and Observability

**Problem:** No Prometheus `ServiceMonitor` or Grafana dashboard templates are included. Operators must manually configure monitoring.

**Suggested improvements:**
- Add optional `ServiceMonitor` templates for services that expose Prometheus metrics (Spring Boot actuator endpoints).
- Include a sample Grafana dashboard JSON for Minecraft server metrics (TPS, player count, memory usage).
- Document integration with the Prometheus Operator / kube-prometheus-stack.

**Priority:** Low — nice-to-have for production; OMCSI's webapp already provides basic resource metrics.

---

## Summary

| # | Improvement | Priority | Effort |
|---|---|---|---|
| 1 | ~~Backup manager — native filesystem mode~~ ✅ Resolved | ~~High~~ | ~~Medium~~ |
| 2 | ~~Health probes for all services~~ ✅ Resolved | ~~Medium~~ | ~~Low~~ |
| 3 | ~~Security contexts~~ ✅ Resolved | ~~Medium~~ | ~~Low~~ |
| 4 | Ingress controller support | Medium | Medium |
| 5 | TLS certificate management (cert-manager) | Low | Medium |
| 6 | Shared PVC scheduling improvements | Low | Medium |
| 7 | Horizontal Pod Autoscaler | Low | Low |
| 8 | ~~Network policies~~ ✅ Resolved | ~~Low~~ | ~~Medium~~ |
| 9 | ~~Pod Disruption Budgets~~ ✅ Resolved | ~~Low~~ | ~~Low~~ |
| 10 | Helm chart publishing | Low | Low |
| 11 | Monitoring and observability | Low | Medium |
