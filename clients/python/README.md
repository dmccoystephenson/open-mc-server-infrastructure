# omcsi-client

A Python client for [Open Minecraft Server Infrastructure](https://github.com/Stephenson-Software/open-mc-server-infrastructure).

OMCSI's services speak HTTP, but until now every caller hand-rolled `curl` or
`requests` against them — re-deriving which port answers what, which endpoints
need a Bearer token, which return `202` rather than `200`, and how long to wait
before giving up. This package is that knowledge, written down once.

**No runtime dependencies.** Standard library only (`urllib`), because OMCSI is
usually driven from operator shells, CI jobs and Claude Code skills where
installing a dependency tree is more friction than the request being made.

## Install

```bash
pip install omcsi-client
```

Or from a checkout of this repository:

```bash
pip install ./clients/python
```

Requires Python 3.9 or newer.

## Which URL do I point it at?

One client per service, because OMCSI is several services rather than one API:

| Client | Service | Default port | What it does |
|---|---|---|---|
| `MinecraftWrapperClient` | `minecraft-wrapper` | 8092 | owns the Minecraft server process |
| `BackupManagerClient` | `backup-manager` | 8091 | takes and reports on backups |
| `AlertManagerClient` | `alert-manager` | 8090 | fans alerts out to Discord and in-game |

A caveat that has caught people out: **OMCSI's nginx proxy mostly does not
front these services.** Its catch-all `location /` forwards to the *web app*,
whose admin API is a separate surface — an admin username and password in a
JSON body — that proxies through to the wrapper. Point this client at the
wrapper itself: `http://minecraft-wrapper:8092` inside the compose network,
`http://<release>-minecraft-wrapper-internal:8092` inside the cluster, or a
published or port-forwarded port from outside.

One route is proxied straight through to the wrapper — and which one differs
between deployments:

- On **Kubernetes**, the chart's nginx sends `/api/plugins/` to the wrapper, so
  `https://<host>/api/plugins/deploy` reaches `deploy_plugin()` unchanged.
- On **Docker Compose**, nginx has no such rule. `/api/plugins/deploy` lands on
  the web app, which has no route of that name, and answers 404.

`/api/world/upload` is the other way round on both: nginx routes `/api/world/`
to the web app, whose *own* upload endpoint takes an admin username and
password and forwards to the wrapper using the deploy token. So a proxy base
URL will not work for `replace_world()` on either deployment — give that the
wrapper's address.

## Example

```python
from omcsi_client import Omcsi, AlertLevel, OmcsiConflictError, OmcsiTransportError

omcsi = Omcsi(
    wrapper_url="http://localhost:8092",
    backup_url="http://localhost:8091",
    alert_url="http://localhost:8090",
    deploy_token="...",              # DEPLOY_AUTH_TOKEN on the wrapper
)

# --- read state -------------------------------------------------------
status = omcsi.wrapper.status()
print("running:" , status.running, "pid:", status.pid)
print("TPS:", omcsi.wrapper.metrics().tps)

# --- ship a plugin ----------------------------------------------------
omcsi.wrapper.deploy_plugin(
    "target/MyPlugin-1.2.3.jar",
    plugin_name="MyPlugin.jar",      # stable name, so the next version replaces it
)

# A deploy does not load the plugin. Restarting does — and a restart
# disconnects players, so it needs saying out loud:
omcsi.wrapper.disruptive.restart(confirm=True)

# --- back up ----------------------------------------------------------
result = omcsi.backups.trigger()     # synchronous; scales with world size
print(result.backup_path)

# --- tell people ------------------------------------------------------
omcsi.alerts.send(
    "Maintenance finished",
    "The server is back up.",
    level=AlertLevel.INFO,
    source="deploy-script",
)

# --- and handle the two kinds of failure differently -------------------
try:
    omcsi.wrapper.start_server()
except OmcsiConflictError:
    print("already running — nothing to do")   # the service answered: no
except OmcsiTransportError:
    print("the wrapper is unreachable")        # nothing answered at all
```

`Omcsi.from_env()` reads `OMCSI_WRAPPER_URL` (or `OMCSI_API_BASE`, which
OMCSI's existing integration harness already sets), `OMCSI_BACKUP_URL`,
`OMCSI_ALERT_URL`, `OMCSI_DEPLOY_TOKEN`, `OMCSI_DEPOSIT_BOX` and
`OMCSI_VERIFY_TLS`.

## Dangerous operations are behind `.disruptive`

Four wrapper endpoints interrupt a live game, and one of them replaces the
world outright. They live behind `client.disruptive` and each takes a mandatory
`confirm=True`:

```python
omcsi.wrapper.disruptive.stop(confirm=True)
omcsi.wrapper.disruptive.restart(confirm=True)
omcsi.wrapper.disruptive.graceful_shutdown(confirm=True)
omcsi.wrapper.disruptive.send_console_command("save-all", confirm=True)
omcsi.wrapper.disruptive.replace_world("world.zip", confirm=True)
```

Without `confirm=True` they raise `ConfirmationRequired` **before any network
call**, so a mistake costs nothing. Making these the longest things in the
package to type is the design, not an oversight: `send_console_command` is
unauthenticated and unrestricted — `stop`, `op`, `ban` and `/kill @a` all work
exactly as typed.

Two things the responses do *not* tell you:

- `stop`, `restart`, `shutdown` and `start` answer `202 Accepted` as soon as
  the work is scheduled. A graceful shutdown takes 30+ seconds *after* that,
  and failures during the operation are logged server-side rather than
  returned. Poll `status()` to learn what actually happened.
- `send_console_command` reports only that the command was written to the
  server's stdin — never its output. Read `logs()` or the container log.

## Timeouts

A single timeout would be wrong in both directions: a status poll that hangs
for ten minutes is a bug, and a world upload killed after thirty seconds never
completes. Each category has its own budget, and every call takes a per-call
override.

| Field | Default | Covers |
|---|---|---|
| `status` | 8s | `status`, `metrics`, `logs`, `latest`, health checks |
| `control` | 30s | `start`/`stop`/`restart`/`shutdown`/`command` (`202`-and-return) and `messages` (forwarded synchronously) |
| `deploy` | 300s | plugin JAR upload |
| `upload` | 3600s | world archive upload: transfer *plus* validate, extract, swap, restart |
| `backup` | 1800s | backup trigger, which copies the whole server directory synchronously |

```python
from omcsi_client import Omcsi, Timeouts

omcsi = Omcsi(timeouts=Timeouts(status=3, upload=8 * 3600))
omcsi.backups.trigger(timeout=4 * 3600)   # or override one call
```

A timeout raises `OmcsiTimeoutError`, which means *this client stopped
waiting* — on the long operations the work is very likely still running
server-side.

## Errors

```
OmcsiError
├── OmcsiResponseError      the service answered and said no; has .status
│   ├── OmcsiAuthError      401 — bad token, or no token configured server-side
│   └── OmcsiConflictError  409 — start when running, stop when stopped
├── OmcsiTransportError     nothing answered; no .status, by design
│   └── OmcsiTimeoutError   we gave up waiting
└── ConfirmationRequired    a disruptive call without confirm=True
```

The split that matters is `OmcsiResponseError` versus `OmcsiTransportError`:
retrying the second is reasonable, retrying the first usually is not.

`OmcsiResponseError.message` is the service's own explanation. OMCSI answers in
two shapes — a JSON body from each service's `GlobalExceptionHandler`, and a
bare string from the hand-written branches in the controllers — and both are
unwrapped into `message`. The raw bytes stay on `.body`.

Note what a 401 does *not* prove: the wrapper rejects every deploy and upload
when its own `DEPLOY_AUTH_TOKEN` is blank, so an `OmcsiAuthError` can mean the
server has no token configured rather than that yours is wrong.

## Large files: use the deposit box

`replace_world()` is the right tool for a small world and the wrong one for a
large one. The upload is not resumable, it is buffered to disk on the way
through, and it must clear nginx's `client_max_body_size`, the web app's
multipart limit and the wrapper's own size cap in series — the smallest wins,
and if it is nginx's the rejection is a bare `413` with no message, because
nginx refused the body before the application ever saw it.

OMCSI's `deposit-box/` directory sidesteps all of that. It is bind-mounted into
the server container at `/deposit-box`; nothing serves it and no endpoint reads
from it, which is exactly the point — the bytes never traverse the HTTP stack.

```python
from omcsi_client import DepositBox

box = DepositBox("/srv/omcsi/deposit-box")
staged = box.stage("huge-world.zip")
print(staged.host_path)       # /srv/omcsi/deposit-box/huge-world.zip
print(staged.container_path)  # /deposit-box/huge-world.zip
```

`stage()` deliberately stops there and hands you both paths, because the second
half of the job is a shell command this package has no business running for
you:

```bash
docker exec open-mc-server unzip /deposit-box/huge-world.zip -d /mcserver/staging
```

It copies rather than moves, so a failed transfer never costs the original.
`retrieve()` goes the other way, for collecting a world or a plugins directory
that was copied into `/deposit-box` from inside the container.

The deposit box is a Docker Compose feature; the Helm chart does not mount it,
so on Kubernetes the equivalent is `kubectl cp`.

## Self-signed certificates

A stock OMCSI deployment terminates TLS with a self-signed nginx certificate,
which no HTTPS client will accept by default. `verify_tls=False` is the
programmatic equivalent of the `curl -k` OMCSI's own docs use, and carries the
same caveat — it disables authentication of the server, so keep it for
deployments you reach over a trusted network and give anything internet-facing
a real certificate.

```python
Omcsi(wrapper_url="https://192.0.2.10", verify_tls=False)
```

## API coverage

Everything the three services expose over HTTP:

**minecraft-wrapper**

| Endpoint | Method |
|---|---|
| `GET /api/server/status` | `status()` |
| `GET /api/server/metrics` | `metrics()` |
| `GET /api/server/logs` | `logs(lines=...)` |
| `POST /api/server/start` | `start_server()` |
| `POST /api/server/stop` | `disruptive.stop(confirm=True)` |
| `POST /api/server/restart` | `disruptive.restart(confirm=True)` |
| `POST /api/server/shutdown` | `disruptive.graceful_shutdown(confirm=True)` |
| `POST /api/server/command` | `disruptive.send_console_command(cmd, confirm=True)` |
| `POST /api/plugins/deploy` | `deploy_plugin(path, ...)` |
| `POST /api/world/upload` | `disruptive.replace_world(path, confirm=True)` |
| `POST /api/messages` | `send_message(text, destination)` |
| `GET /actuator/health` | `health()` |

**backup-manager**

| Endpoint | Method |
|---|---|
| `POST /api/backups/trigger` | `trigger()` |
| `GET /api/backups/latest` | `latest()` |
| `GET /actuator/health` | `health()` |

**alert-manager**

| Endpoint | Method |
|---|---|
| `POST /api/alerts` | `send(title, message, ...)` |
| `GET /api/alerts` | `recent(limit=...)` |
| `GET /api/alerts/health` | `service_health()` |
| `GET /actuator/health` | `health()` |

Deliberately not covered: the **web app's** admin API (`POST /api/command`,
`/api/server/start`, `/api/plugins/*`, `/api/world/*`, and the dashboard
routes). That is a separate surface with different semantics — it authenticates
with an admin username and password in a JSON body, and its `/api/status` is an
RCON-derived view that can report a server as online for up to
`WEB_REFRESH_INTERVAL_MS` after it has stopped. Anything scripted is better off
against the wrapper, which reports on the process it owns.

## Development

```bash
cd clients/python
pip install -e .
python -m unittest discover -s tests -t tests -v
```

Or without installing:

```bash
PYTHONPATH=src python -m unittest discover -s tests -t tests
```

The tests run a real HTTP server on localhost rather than mocking `urllib`, so
the transport — headers, multipart framing, status handling, timeouts — is
genuinely exercised. No OMCSI deployment is required.

## License

MIT, same as OMCSI itself.
