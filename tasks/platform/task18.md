# T18: Metrics, dashboard, alerts

> Module `platform` · Phase 4: Observability · [All tasks](../todo.md) · [Spec](../../SPEC-platform.md) · [Plan](../plan.md)
> [← T17](task17.md) · [T19 →](task19.md)

`Scope M · Depends on: T17 · Closes: B3`

**Goal:** a single dashboard shows platform health, and the Arch §8 alerts exist.

**Subtasks**
- [ ] **18.1** Enable `/actuator/prometheus` in the starter, with latency histograms for HTTP requests so p95 can be calculated.
- [ ] **18.2** Write `prometheus.yml` scraping the 4 apps, with one target set for `bootRun` (`host.docker.internal`) and one for containers (service names).
- [ ] **18.3** 🙋 Pick a source for Kafka consumer lag in **seconds**. The default Kafka client metric
  counts messages, not seconds, so the Arch's "lag over 30 s" alert probably needs an extra lag
  exporter container. Adding a container is "ask first".
- [ ] **18.4** Write `alerts.yml` with two rules: consumer lag > 30 s, and 5xx rate > 1%.
- [ ] **18.5** Build the "Platform overview" dashboard JSON with these panels: request rate,
  p95 per route, 5xx rate, 429 rate, JVM heap and consumer lag. Add the Grafana dashboard provisioning.
- [ ] **18.6** Run `promtool check rules` in a container. Run `smoke.sh` a few times, then check every panel shows data.

**Done when**
- [ ] The Prometheus targets page shows all 4 apps `UP`
- [ ] Every dashboard panel has data
- [ ] Alert rules load with no errors

**Verify**
- [ ] `promtool check rules infra/prometheus/alerts.yml` exits 0
- [ ] Step 18.6 was observed

**Files:** `infra/prometheus/prometheus.yml`, `infra/prometheus/alerts.yml`, `infra/grafana/dashboards/platform-overview.json`, `infra/grafana/provisioning/dashboards/dashboards.yaml`, `infra/docker-compose.yml`

---

## ◆ Checkpoint D: Observability
- [ ] B1–B3 are ticked, and the `observability` profile's memory use is noted
- [ ] 🙋 **Human review**

---

## Definition of Done (applies to every task)

`./gradlew build` is green (plus the frontend checks, if touched). No new suppressions and no
disabled tests. If a decision changed, the spec was updated first. The spec's success-criterion
ids that this task closes are ticked in [SPEC-platform.md](../../SPEC-platform.md).
