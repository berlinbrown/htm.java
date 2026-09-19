# Numenta workbench: 20 NAB examples in pure Java

This directory is a standalone Maven project that replays **20 complete NAB
timeseries** through Java ports of three NAB detectors and an actual htm.java
Temporal Memory detector. It requires Java 8+
and Maven. Detection, CSV reading, label evaluation, and reports all run in Java.
No Python interpreter, NumPy, SciPy, native library, or Python service is needed.

Think of a detector as a person watching a thermometer: after seeing many normal
readings, it assigns a high score to a reading that looks unusual. This workbench
lets you inspect those scores against NAB's annotated anomaly windows.

These are **20 dataset scenarios sharing reusable detector implementations**,
not 20 different algorithms. “Top 20” here means a deliberately varied learning
selection, not a ranking published by Numenta.

## Start here

Run these commands from the htm.java repository root:

```sh
# Build, test and install htm.java and its bundled matrix dependency locally.
# install already includes package; "clean package install" also works but repeats phases.
mvn clean install

# Compile and run all tests, including every complete example with all detectors.
mvn -f numentaworkbench/pom.xml clean verify

# List the 20 selectable example IDs.
mvn -q -f numentaworkbench/pom.xml compile exec:java -Dexec.args="list"

# Run one example and write CSV reports.
mvn -q -f numentaworkbench/pom.xml compile exec:java \
  -Dexec.args="machine_temperature_system_failure gaussian numentaworkbench/target/demo"

# Run all 20 examples with the Gaussian detector.
mvn -q -f numentaworkbench/pom.xml compile exec:java \
  -Dexec.args="all gaussian numentaworkbench/target/gaussian"

# Run all 20 with the relative-entropy detector.
mvn -q -f numentaworkbench/pom.xml compile exec:java \
  -Dexec.args="all entropy numentaworkbench/target/entropy"

# Run the intentionally uninformative null baseline.
mvn -q -f numentaworkbench/pom.xml compile exec:java \
  -Dexec.args="all null numentaworkbench/target/null"
```

The parent project's default `mvn test` continues testing htm.java. Use
`-f numentaworkbench/pom.xml` to select this independent workbench build.
It depends on the locally installed artifact org.numenta:htm.java:0.6.14.
Run the root install first and again whenever you change the library.
The root build resolves its custom matrix library from the checked-in
`libs/repository` file repository into the local Maven cache.
This replaces the old nonportable systemPath dependency; both libraries are
then resolved from your local Maven repository.

`mvn clean package install` at the root also works. `mvn clean install` is the
shorter equivalent for this purpose because Maven's install phase already runs
compile, test and package before placing the artifact in the local repository.

## Visual anomaly explorer (Swing)

Start the desktop dashboard from the repository root:

```sh
sh numentaworkbench/scripts/visualize.sh
```

Or run its Java main class directly through Maven:

```sh
mvn -q -f numentaworkbench/pom.xml compile exec:java \
  -Dexec.mainClass=org.numenta.workbench.AnomalySwingVisualizer
```

The visualizer contains all 20 examples and all four detectors. Choose an
example, choose `htm`, `gaussian`, `entropy`, or `null`, set the alert threshold
and warmup rows, then press **Run detector**. Detection runs in a background
worker so the window remains responsive during a long HTM example.

The upper chart is the source measurement. The lower chart is the anomaly
score. Gold background bands are NAB's known anomaly windows, the gray portion
is warmup, the horizontal line is the selected alert threshold, and red marks
are counted alerts. Move the mouse over the chart to inspect timestamp, source
value, score, label, and alert state for a row.

The summary shows total alerts, known windows hit, and alerts outside known
windows. A gold band is evaluation information from NAB; the detector never
receives it as training input. Seeing the red score rise before or inside a gold
band is the useful behavior to investigate. The chart averages multiple points
into each screen pixel, so it remains readable when a dataset has thousands of
rows; CSV output retains every original record.
The existing lowercase directory is retained to match the IDE and repository.

Maven downloads Java dependencies the first time. Subsequent offline runs work
with `mvn -o` once the required plugins and dependencies have been cached.
Jackson parses NAB's label JSON; JUnit is test-only.

## Short scripts

From the repository root:

```sh
sh numentaworkbench/scripts/run.sh list
sh numentaworkbench/scripts/run.sh all
sh numentaworkbench/scripts/run.sh nyc_taxi gaussian
sh numentaworkbench/scripts/run.sh art_daily_jumpsup entropy
sh numentaworkbench/scripts/run.sh machine_temperature_system_failure htm
sh numentaworkbench/scripts/test.sh
sh numentaworkbench/scripts/test.sh machine_temperature_system_failure
```

Scripts switch to the workbench directory, so their default output is always
`numentaworkbench/target/nab-results/`. Invoking them with `sh` requires no
executable-bit setup. They propagate Maven failures as nonzero exit statuses.
For a custom output path, use the direct Maven command.

## The 20 examples

| # | Example ID | Why it is useful |
|---|---|---|
| 1 | `art_daily_no_noise` | Normal repeating daily curve; watch for false alarms at predictable transitions. |
| 2 | `art_daily_perfect_square_wave` | Perfect square wave; abrupt changes are normal here, showing why a jump is not always a fault. |
| 3 | `art_daily_small_noise` | Daily pattern plus small noise; compare with the noise-free control. |
| 4 | `art_flatline` | Constant signal; confirms zero-variance handling without NaN or division by zero. |
| 5 | `art_noisy` | Random-looking normal variation; stresses false-positive behavior. |
| 6 | `art_daily_flatmiddle` | Daily pattern interrupted by a flat middle; missing variation can be anomalous. |
| 7 | `art_daily_jumpsdown` | A downward jump in a daily signal. |
| 8 | `art_daily_jumpsup` | An upward jump in a daily signal. |
| 9 | `art_daily_nojump` | An expected daily jump is missing; tests sensitivity to an absent event. |
| 10 | `art_increase_spike_density` | Spikes become more frequent; a distribution-change example. |
| 11 | `art_load_balancer_spikes` | Load-balancer spikes; contrast brief extremes with sustained shifts. |
| 12 | `ambient_temperature_system_failure` | Office ambient temperature associated with system failure. |
| 13 | `cpu_utilization_asg_misconfiguration` | Cloud CPU behavior affected by autoscaling misconfiguration. |
| 14 | `ec2_request_latency_system_failure` | Request latency around a documented service failure. |
| 15 | `machine_temperature_system_failure` | Industrial machine temperature with shutdown and failure events. |
| 16 | `nyc_taxi` | NYC taxi passenger counts, with holidays and other unusual demand events. |
| 17 | `rogue_agent_key_hold` | Keyboard hold durations associated with a change in user. |
| 18 | `rogue_agent_key_updown` | Keyboard up/down timing associated with a change in user. |
| 19 | `speed_7578` | Road traffic speed; another periodic real-world metric. |
| 20 | `ec2_cpu_utilization_825cc2` | Cloud CPU utilization; operational monitoring example. |

The first five deliberately have no labeled anomalies. A detector that raises
many alarms there is not necessarily “learning better”; it may be too sensitive.
The remaining examples contain labeled windows, but a simple statistical detector
will not necessarily detect all of them. An integration-test pass means the
program ran correctly, not that its anomaly detection was perfect.

## What was converted from Python?

| Python source in NAB | Java implementation | Behavior |
|---|---|---|
| `nab/detectors/gaussian/windowedGaussian_detector.py` | `Detectors.Gaussian` | Sliding Gaussian, 6,400-point window, 100-point update buffer |
| `nab/detectors/relative_entropy/relative_entropy_detector.py` | `Detectors.Entropy` | Five-bin histograms over 52 records; compare with learned hypotheses |
| `nab/detectors/null/null_detector.py` | `Detectors.create("null", ...)` | Always returns 0.5 |
| `nab/detectors/base.py` record loop/output | `Workbench.run` | Replay file order, enforce scores in [0,1], attach labels, write CSV |

Gaussian asks: “How far is this reading from the recent average, relative to
normal variation?” It scores the reading **before** learning it. The first score
is zero. A reading at the mean scores 0.5, rather than zero; unusually high
**or low** readings approach one. A score is a statistical unusualness measure,
not a guaranteed probability of equipment failure.

The port uses population standard deviation, replaces a zero standard deviation
with 0.000001, grows the initial window to 6,400 records, and then updates in
batches of 100. Java 8 lacks `math.erfc`, so the implementation uses a documented
normal-tail approximation. Floating-point values need not be bit-for-bit equal
to Python. The saved upstream NYC taxi results are checked with 1e-6 tolerance.

Relative entropy asks: “Does the recent mix of values look like any mix I have
seen before?” An unfamiliar mix scores one and becomes a new remembered
hypothesis. Familiar mixes score zero. The first complete window establishes
the first hypothesis. Flat input scores zero throughout. The chi-squared
threshold is the fixed SciPy value for four degrees of freedom at 99%.

NAB supplies this detector with **whole-file minimum and maximum**. The Java port
preserves that behavior. It uses future range information for calibration and
should not be described as fully causal live-stream preprocessing. Labels are
never passed to either detector. A production live-stream version would need
a separately designed range-calibration policy.

Null asks nothing and always returns 0.5. It is a control for the plumbing,
not a useful fault detector.

## Read the output

Each run creates:

- `DETECTOR_EXAMPLE.csv`: one result for every source record.
- `DETECTOR_summary.csv`: one summary row per selected example.

Columns in each detailed report:

| Column | Plain meaning |
|---|---|
| timestamp | Source timestamp, with no invented timezone |
| value | Actual observed measurement |
| anomaly_score | Detector output from zero to one |
| label | 1 when timestamp falls inside an upstream labeled anomaly window |
| alert | 1 when evaluated and score meets the configured threshold (default 0.99) |
| evaluated | 0 during warmup; 1 afterward |

Summary counts include total records, alerting records, windows hit at least
once, total windows, and alerting records outside every window. Both window
endpoints count as inside. Multiple alarms in one window count as one hit
window but multiple alerting records.

For example, `hit_windows=2,total_windows=3` means at least one alert occurred
inside two of three known windows. It does **not** mean 67% prediction accuracy.
Outside-window alarms may indicate false positives or limitations in the labels.

The default 0.99 threshold is explanatory, not optimized. With default warmup=0,
initial learning alarms remain visible; use wb.warmup to exclude them.
Existing files with the same report name are overwritten;
use separate output directories to retain experiments. Summaries describe only
the selected examples in that invocation.

## Official NAB scoring is a separate scope

This is a **detection workbench**, not a complete Java port of NAB v1.1.
It does not implement official early-detection reward curves, probation rules,
application profiles, threshold optimization, or score normalization.
Do not compare its hit counts to NAB's published scoreboard numbers.

It does not port every detector or build a Swing GUI. The existing htm.java
Swing demonstration remains a separate program. The statistical baselines
remain available for comparison with the new HTM detector.

## Actual HTM learning

`htm` uses the installed library's ScalarEncoder, TemporalMemory, Connections
and raw anomaly calculation. Each numeric value becomes nine active bits among
128 columns, with eight context cells per column. Temporal Memory learns which
columns follow previous activity. The detector compares today's active columns
with the previous step's predicted columns **before** updating the model.

The output is the fraction of current active columns that were unpredicted:
zero means fully expected and one means entirely unexpected. It is not a
calibrated failure probability. The first observation scores one because the
model has no prediction yet.

This compact configuration maps encoder bits directly to Temporal Memory
columns. It does not use a Spatial Pooler, time-of-day encoder, or anomaly
likelihood calibration, and does not reproduce NAB's published HTM configuration.
It is a runnable sequence-learning baseline with tests that demonstrate learning,
not a claim that HTM will beat Gaussian on these datasets.

The encoder uses full-file min/max, like the existing entropy calibration.
That is future range information. Out-of-range inputs clip to the fixed range.
All records are processed once in source order with online learning enabled;
labels are not used for training. A fresh model and seed 42 are used per run.

```sh
# Run all HTM examples.
sh numentaworkbench/scripts/run.sh all htm

# Compare the same machine-temperature data with two detectors.
# Warmup still trains, but its alerts are excluded from reported counts.
mvn -q -f numentaworkbench/pom.xml compile exec:java \
  -Dexec.args="machine_temperature_system_failure htm numentaworkbench/target/comparison" \
  -Dwb.threshold=0.8 -Dwb.warmup=500
mvn -q -f numentaworkbench/pom.xml compile exec:java \
  -Dexec.args="machine_temperature_system_failure gaussian numentaworkbench/target/comparison" \
  -Dwb.threshold=0.99 -Dwb.warmup=500
```

Scores have different meanings, so equal thresholds are not automatically fair.
These comparisons are exploratory; a rigorous benchmark needs separate
calibration data and an evaluation protocol.

`wb.threshold` defaults to 0.99 and must be finite and within [0,1].
`wb.warmup` defaults to zero and must be a nonnegative record count. CSV output
adds an `evaluated` flag. The summary records both settings, evaluated record
count, and mean score over evaluated records. If warmup covers the entire file,
there are zero evaluated records and mean score is blank.
The total-window denominator still includes all labeled windows, including
those inside warmup; it is descriptive and is not official NAB recall/scoring.

## Tests: all or individual

```sh
# Complete suite.
mvn -f numentaworkbench/pom.xml test

# Only the four complete-data runs for one example.
mvn -f numentaworkbench/pom.xml test \
  -Dtest=NabIntegrationTest -Dexample=nyc_taxi

# Detector math, reference comparisons, or all dataset replays separately.
mvn -f numentaworkbench/pom.xml test -Dtest=DetectorTest
mvn -f numentaworkbench/pom.xml test -Dtest=ReferenceParityTest
mvn -f numentaworkbench/pom.xml test -Dtest=NabIntegrationTest
```

`NabIntegrationTest` is parameterized with the 20 named examples. Each example
gets Gaussian, entropy, null and HTM runs: 80 full-series integration cases.
They execute in Maven's test phase, so both `test` and `verify` include them.
`-Dexample=ID` filters these cases only; other test classes still run unless
you also specify `-Dtest=NabIntegrationTest`. A misspelled ID fails explicitly.

Unit tests cover Gaussian reference values, the buffered-window transition,
entropy regime changes and flatlines, label boundaries, catalogue uniqueness,
report completeness, deterministic repeatability and invalid names.
HTM behavioral tests verify learning a repeating cycle, retaining predictions
with learning disabled, detecting an unseen value, learning a flatline, and
isolation and determinism across model instances. Warmup and threshold tests
check that reporting excludes warmup alerts while still processing records.
Reference tests compare every Java score with NAB's checked-in Gaussian taxi
and relative-entropy upward-jump output. They do not launch Python.

Reports live under `numentaworkbench/target/surefire-reports/`.
The data is small enough to run as ordinary integration tests; these runs replay
historical timestamps quickly rather than sleeping for each five-minute interval.

## Design and data fidelity

```text
examples.tsv selects a CSV
    → Corpus reads timestamp/value records in file order
    → a fresh detector scores each record
    → label windows are attached for evaluation
    → Workbench writes detailed and summary CSV
```

A fresh detector per dataset prevents training history leaking from one example
into another. All bundled datasets are processed completely, with no row limit.
NAB contains duplicate or out-of-order timestamps in some files. The reader
preserves those records and their order, matching the Python iteration behavior.
It rejects malformed CSV records and non-finite numeric values with file/row
context. This reader is intentionally for NAB's two-column, unquoted numeric CSV
format, not a general-purpose spreadsheet importer.

The current runner loads one series into memory, and Gaussian recomputes mean
and variance directly for numerical clarity. Entropy remembers novel hypotheses,
whose count can grow with changing data. These choices suit the bundled examples;
they are not claims of constant-memory, unlimited production streaming.

## Where to edit

- `src/main/resources/nab/examples.tsv`: example names, paths and descriptions.
- `src/main/resources/nab/data/`: the 20 original CSV files.
- `src/main/resources/nab/combined_windows.json`: upstream labels.
- `Corpus.java`: input and label handling.
- `Detectors.java`: the ported statistical algorithms.
- `Workbench.java`: command line, replay, reports and descriptive counts.
- `src/test/resources/reference/`: two upstream result files used as golden references.
- `scripts/`: short Maven wrappers.

To add a detector, implement `Detectors.Detector`, register its name in the
factory, add synthetic tests where expected behavior is known, then replay
the complete examples. Keep scoring separate from labels to avoid cheating.

## Troubleshooting and provenance

Unknown ID: run `list` and copy the exact ID without `.csv`.
Unknown detector: use `gaussian`, `entropy`, `null`, or `htm`.
Missing resource: run `mvn clean test` to rebuild copied resources.
No alerts: the detector may be insensitive at 0.99; this is not necessarily a
program failure. Null is expected to produce none.
Too many alerts: inspect normal control examples and initial learning behavior.
Maven network/cache errors: allow the initial dependency download, then retry.
`mvn clean` deletes generated reports under target; move reports you want to keep.

Source: local NAB checkout at commit
`ea702d75cc2258d9d7dd35ca8e5e2539d71f3140`.
See [NOTICE.md](NOTICE.md) and [NAB-LICENSE.txt](NAB-LICENSE.txt) for attribution.
Dataset selection is fixed and bundled; neither build nor execution needs
`/Users/berlinbrown/src/NAB` to exist.
