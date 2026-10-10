# B7 performance report

Local Windows loopback only. Proposed staging target remains unaccepted.

| Operation | Samples | p50 / p95 / p99 seconds | Requests/sec | Statuses |
|---|---|---|---|---|
| generate | 120 | 0.0243 / 0.0312 / 0.0340 | 4.00 | {'200': 120} |
| export | 120 | 0.0248 / 0.0304 / 0.0313 | 4.00 | {'200': 120} |

Exact host/CPU/memory/connection boundary samples: `.b7\20261006T051951458194Z\local\performance-results.json`. These are not peak measurements.
ETL runtime and generator resource/recovery details: local-results.json. Java/Python saturation and recovery are in the current B0–B6 chain; no generated-auth throughput SLO is claimed.

## Generated authentication workloads

Actual HTTPS; each workload has10 warmups and60 measured requests over30 seconds at2 arrivals/second, at most2 in flight. All measured responses200. These remain local measurements without accepted staging SLOs.

| Runtime / variant | p50 / p95 / p99 milliseconds |
|---|---|
| Java / postgresql-off-profile | 64.7 / 73.3 / 171.9 |
| Java / sqlite-off-profile | 26.1 / 33.6 / 34.6 |
| Python / sqlite-off-profile | 29.8 / 31.4 / 32.2 |
| Python / postgresql-off-profile | 57.0 / 162.6 / 177.4 |

Exact input result paths and SHA256 values are in the final-review.json addendum.
