/* Smart Campus lessons — shared script.
 * - LESSONS is the single list of all lessons (order = reading order).
 * - Each lesson page sets <body data-lesson="NN"> and includes <div id="lesson-end"></div>;
 *   this script fills in the "I finished this" box and the prev/next links.
 * - Progress is kept in this browser only (localStorage). If storage is blocked, pages still work.
 */
(function () {
  "use strict";

  var LESSONS = [
    // Part 1 — Foundations (platform module)
    { id: "01", file: "01-java-21.html", title: "Modern Java 21 for this project", part: 1 },
    { id: "02", file: "02-maven.html", title: "Maven multi-module builds", part: 1 },
    { id: "03", file: "03-spring-boot.html", title: "Spring Boot 4 basics", part: 1 },
    { id: "04", file: "04-postgres-flyway.html", title: "PostgreSQL, schemas and Flyway", part: 1 },
    { id: "05", file: "05-docker-compose.html", title: "Docker, Compose and Dockerfiles", part: 1 },
    { id: "06", file: "06-spring-modulith.html", title: "Modular monolith with Spring Modulith", part: 1 },
    { id: "07", file: "07-kafka.html", title: "Apache Kafka fundamentals", part: 1 },
    { id: "08", file: "08-outbox-idempotency.html", title: "Reliable events: outbox, dedupe, replay", part: 1 },
    { id: "09", file: "09-rest-problem-openapi.html", title: "REST APIs, problem JSON and OpenAPI", part: 1 },
    { id: "10", file: "10-testing.html", title: "Testing: JUnit, Testcontainers, ArchUnit", part: 1 },
    { id: "11", file: "11-gateway-rate-limit.html", title: "API gateway and rate limiting", part: 1 },
    { id: "12", file: "12-observability.html", title: "Observability: traces, logs, metrics", part: 1 },
    { id: "13", file: "13-typescript-react.html", title: "TypeScript, React and Vite", part: 1 },
    { id: "14", file: "14-frontend-api-testing.html", title: "Frontend data, generated types and tests", part: 1 },
    { id: "15", file: "15-git-ci-adr.html", title: "Git, GitHub Actions CI and ADRs", part: 1 },
    // Part 2 — Feature modules
    { id: "16", file: "16-oauth-oidc-keycloak.html", title: "OAuth 2, OpenID Connect and Keycloak", part: 2 },
    { id: "17", file: "17-spring-security.html", title: "Spring Security with JWT and roles", part: 2 },
    { id: "18", file: "18-shell-frontend.html", title: "App shell: routing, lazy modules, i18n", part: 2 },
    { id: "19", file: "19-domain-modeling.html", title: "Domain modeling: aggregates and rules", part: 2 },
    { id: "20", file: "20-redis-concurrency.html", title: "Redis, Lua and the registration rush", part: 2 },
    { id: "21", file: "21-load-testing-k6.html", title: "Load testing with k6", part: 2 },
    { id: "22", file: "22-read-models-cqrs.html", title: "Read models, CQRS and iCal", part: 2 },
    { id: "23", file: "23-grades-audit.html", title: "Grades: audit trails, locking, imports", part: 2 },
    { id: "24", file: "24-realtime-notifications.html", title: "Real-time: WebSocket, push and email", part: 2 },
    { id: "25", file: "25-scheduled-jobs-feeds.html", title: "Scheduled jobs and targeted feeds", part: 2 },
    { id: "26", file: "26-payments-webhooks.html", title: "Payments, webhooks and idempotency", part: 2 },
    { id: "27", file: "27-qr-tickets.html", title: "QR tickets and signed tokens", part: 2 },
    { id: "28", file: "28-file-storage-s3.html", title: "File uploads and S3 storage", part: 2 },
    { id: "29", file: "29-opensearch.html", title: "Search with OpenSearch", part: 2 },
    { id: "30", file: "30-security-hardening.html", title: "Security hardening (OWASP)", part: 2 },
    { id: "31", file: "31-kubernetes-shipping.html", title: "Shipping: Kubernetes and Helm", part: 2 }
  ];
  window.CAMPUS_LESSONS = LESSONS;

  var KEY = "smart-campus-lessons-done";

  function readDone() {
    try { return JSON.parse(localStorage.getItem(KEY) || "{}") || {}; } catch (e) { return {}; }
  }
  function writeDone(map) {
    try { localStorage.setItem(KEY, JSON.stringify(map)); } catch (e) { /* storage blocked: ignore */ }
  }
  window.CAMPUS_DONE = { read: readDone, write: writeDone };

  /* ----- theme toggle ----- */
  function applyTheme(t) {
    if (t === "light" || t === "dark") document.documentElement.setAttribute("data-theme", t);
    else document.documentElement.removeAttribute("data-theme");
  }
  try { applyTheme(localStorage.getItem("smart-campus-theme")); } catch (e) {}

  function setupTopbar() {
    var btn = document.getElementById("theme-btn");
    if (!btn) return;
    btn.addEventListener("click", function () {
      var cur = document.documentElement.getAttribute("data-theme");
      var dark = cur ? cur === "dark" : window.matchMedia("(prefers-color-scheme: dark)").matches;
      var next = dark ? "light" : "dark";
      applyTheme(next);
      try { localStorage.setItem("smart-campus-theme", next); } catch (e) {}
    });
  }

  /* ----- copy buttons on code blocks ----- */
  function setupCopy() {
    var pres = document.querySelectorAll("pre:not(.diagram)");
    Array.prototype.forEach.call(pres, function (pre) {
      var b = document.createElement("button");
      b.className = "copy-btn"; b.type = "button"; b.textContent = "Copy";
      b.addEventListener("click", function () {
        var text = (pre.querySelector("code") || pre).innerText;
        var done = function () { b.textContent = "Copied"; setTimeout(function () { b.textContent = "Copy"; }, 1200); };
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text).then(done, function () {});
        }
      });
      pre.appendChild(b);
    });
  }

  /* ----- end of lesson: done box + prev/next ----- */
  function setupLessonEnd() {
    var id = document.body.getAttribute("data-lesson");
    var host = document.getElementById("lesson-end");
    if (!id || !host) return;
    var idx = -1;
    for (var i = 0; i < LESSONS.length; i++) if (LESSONS[i].id === id) idx = i;
    if (idx < 0) return;

    var done = readDone();
    var box = document.createElement("label");
    box.className = "done-box";
    box.innerHTML = '<input type="checkbox"> I finished this lesson and can answer the quiz';
    var cb = box.querySelector("input");
    cb.checked = !!done[id];
    cb.addEventListener("change", function () {
      var d = readDone();
      if (cb.checked) d[id] = new Date().toISOString().slice(0, 10); else delete d[id];
      writeDone(d);
    });
    host.appendChild(box);

    var nav = document.createElement("nav");
    nav.className = "lesson-nav";
    var prev = LESSONS[idx - 1], next = LESSONS[idx + 1];
    nav.innerHTML =
      (prev ? '<a class="prev" href="' + prev.file + '"><small>← Previous</small>' + prev.id + ". " + prev.title + "</a>"
            : '<a class="prev" href="index.html"><small>← Back</small>All lessons</a>') +
      (next ? '<a class="next" href="' + next.file + '"><small>Next →</small>' + next.id + ". " + next.title + "</a>"
            : '<a class="next" href="index.html"><small>Finished →</small>All lessons</a>');
    host.appendChild(nav);
  }

  document.addEventListener("DOMContentLoaded", function () {
    setupTopbar();
    setupCopy();
    setupLessonEnd();
  });
})();
