# Learning HTM.java by Building Small Things

This is a beginner-friendly map of the concepts in this repository and three small
ways to use them. It focuses on `SpatialPooler`, `TemporalMemory`, encoders in
[org.numenta.nupic.encoders](../src/main/java/org/numenta/nupic/encoders), and the
`Network`/`Layer`/`Region` API in
[org.numenta.nupic.network](../src/main/java/org/numenta/nupic/network).

## The five-year-old version

Imagine watching the same four dance moves again and again:

```text
A -> B -> C -> D -> A -> B -> C -> D ...
```

After a while you expect `C` whenever you see `B`. Temporal Memory does a similar
job, except a "move" is represented by a small set of active bits instead of a
letter. If `B` is followed by something new, the prediction is wrong and the
anomaly score rises. "Anomaly" means **surprising**, not necessarily **bad**.

The slightly-more-technical version:

* An **encoder** translates ordinary data, such as temperature or a weekday, into
  a sparse binary pattern.
* An **SDR** (Sparse Distributed Representation) is that large pattern with only a
  few bits switched on. Similar inputs should produce overlapping patterns.
* The **Spatial Pooler** produces a stable sparse set of active columns.
* **Temporal Memory** learns which column patterns tend to follow other patterns.
  Each column contains several cells so it can represent the same input in
  different sequence contexts.
* An **anomaly score** near `0` means the new input was predicted; a score near `1`
  means it was unexpected.

That is the core idea. The full `Network` API is plumbing that helps assemble and
run these pieces; it is not necessary for learning the basic loop.

## Run the visual explanation first

The Swing visualizer is the quickest way to see Temporal Memory learn. From the
repository root, run:

```bash
mvn compile exec:java@visualizer
```

The application simulates 24 hourly room-temperature readings and repeats the
profile over multiple training days:

```text
12 AM 64°F → 1 AM 63°F → ... → Noon 76°F → ... → 11 PM 65°F → next day
```

Hour and temperature are both genuine model inputs. Separate scalar encoders turn
them into SDRs, the SDRs are joined, and the Spatial Pooler creates the internal
column pattern consumed by Temporal Memory:

```text
hour ─────────→ periodic ScalarEncoder ─┐
                                       ├→ Spatial Pooler → Temporal Memory
temperature ──→ ScalarEncoder ─────────┘
```

Read the grid in two directions:

```text
Across: C0 ... C15       = input columns (what pattern is present)
Down:   Context cell 0-3 = sequence contexts (what came before)
```

Each column now has a state banner so the important information is visible before
you inspect individual cells:

* **INPUT** means that column is part of the current input.
* **NEXT** means the model predicts that column for the next input.
* **BOTH** means it is active now and remains predictive.
* A dash means the column is inactive and not predicted.

The circles show the more detailed cell-level state:

* **Cyan cells** are active for the input happening now.
* **Gold cells** are the model's prediction for the next input.
* **Green cells** are both active and predictive.
* **Previous prediction** is what the model expected before it saw the current
  input. **Prediction for next input** is its new guess after processing it.
* **Recent inputs** is a left-to-right timeline. The rightmost reading is happening
  now. **Next predicted reading** appears on a separate row below the
  history so a long input list cannot push the prediction off screen.
* **Segments and synapses** are learned connections. Their counts normally grow
  while the model learns.
* **Anomaly** is `1.0` when none of the active columns were predicted and `0.0`
  when all were predicted.
* **Goal** is a teaching aid for this demo: predict the complete next pattern,
  with no extra guesses, for all 24 hours of one simulated day. It is not an
  objective built into the Temporal Memory algorithm, and one perfect day does not
  guarantee perfect predictions forever.
* **Best-case ETA** appears after the first exact prediction. It uses the remaining
  goal steps and the selected delay. It is not a guarantee: a wrong or extra
  prediction resets the streak, and learning may take longer. Before there is
  enough evidence, the app honestly reports that it is still estimating. The UI
  also shows the real-time playback rate: at the default delay, one simulated day
  takes `24 × 450 ms = 10.8 seconds`.

Start by letting it run for a few cycles. Then pause it and use **Next step** to
inspect one transition at a time. Press **Surprise next** to replace the next
normal pattern with an unexpected one; watch the anomaly score jump. **Reset
learning** creates a fresh model, so the early guesses will be poor again.

**Learning ON** means each new input may strengthen, weaken, or create connections.
Turn it OFF to freeze those learned connections and test what the model currently
knows. It will continue processing inputs and making predictions, but it will not
train on them. This is useful after the goal is reached: freeze learning, inject a
surprise, and compare the anomaly without teaching the surprise to the model.
The ETA pauses while learning is OFF.

The sensor stream is simulated rather than read from physical hardware, but both
fields travel through the complete HTM pipeline. The grid's column IDs are the
Spatial Pooler's internal representation; the top labels and timeline translate
them back into temperatures and times of day. Repeating the hourly profile lets
you watch early unpredicted readings gradually turn into learned next-hour
predictions.

## 1. "Toy Numenta" — a minimal HTM you can actually read

Goal: strip HTM down to the smallest complete pipeline so the core loop is easy to
follow, instead of starting with the full `Network` API (which wires together
`Sensor`s, `Layer`s, `Region`s and encoders declaratively).

Minimal loop to reproduce by hand:

```
input value -> Encoder -> SDR (binary array)
SDR -> SpatialPooler.compute() -> active columns
active columns -> TemporalMemory.compute() -> predictive/active cells
predictive cells -> compare next input -> anomaly score
```

Concretely, a toy version can be built directly from these classes without the
`Network` builder:

* `org.numenta.nupic.encoders.ScalarEncoder` (or `RandomDistributedScalarEncoder`) —
  turn a number into a sparse binary array.
* `org.numenta.nupic.algorithms.SpatialPooler` — call `compute(inputArray, learn, activeColumns)`.
* `org.numenta.nupic.algorithms.TemporalMemory` — call `compute(activeColumns, learn)`.
* `org.numenta.nupic.algorithms.Anomaly` / `AnomalyLikelihood` — turn TM output into a
  single anomaly score.

Suggested toy project shape:

* A single `main()` that feeds a small stream of numbers (for example, a sine wave
  or the bundled `src/main/resources/rec-center-hourly.csv` and
  `days-of-week-stream.csv` files) through the four steps above.
* Print the active column count, number of predicted columns, and anomaly score per
  step, so the "why did HTM notice something odd" question has a visible answer.
* Skip `Network`, `Region`, `Layer`, serialization, and multi-region hierarchies —
  those exist to support production pipelines, not learning the algorithm.

This is the fastest path to an intuition for how SDRs, columns, and cells relate.

One important detail: train and infer in time order. HTM learns transitions, so
shuffling a time series destroys the thing Temporal Memory is meant to learn. Also
keep a reset boundary between unrelated sequences; otherwise the last item in one
sequence is accidentally taught as the predecessor of the first item in the next.

## 2. Robotics on a Raspberry Pi — HTM reacting to sensors, driving actions

Idea: use HTM as an anomaly/pattern detector over one or more sensor streams (e.g.
distance sensor, IMU, light/sound level, GPIO button) running on a Pi, and trigger a
robot action (say a phrase, move a servo, blink an LED) when HTM flags something
unusual or recognizes a learned pattern.

Suggested architecture:

* **Sensor read loop (Pi side, Java or a small bridge process):** poll GPIO/I2C
  sensors on an interval and produce a numeric or categorical reading per tick.
* **Encoder:** map each reading to an SDR with `ScalarEncoder` /
  `CategoryEncoder` / `DateEncoder` depending on the sensor type — the encoders
  package already covers most common sensor value shapes.
* **SpatialPooler + TemporalMemory:** run the toy loop from section 1 per tick.
* **Action trigger:** when `Anomaly`/`AnomalyLikelihood` crosses a threshold (or a
  specific, previously-learned column pattern appears), call out to whatever "do
  something" action is wanted — e.g. text-to-speech ("something changed"), a motor
  command, or a simple GPIO write.

Practical notes for Pi deployment:

* HTM.java targets Java 8 (see main [README](../README.md)); a Raspberry Pi running a
  recent Raspberry Pi OS with a JVM (Zulu/Temurin builds for ARM) can run this
  library directly — no native rebuild needed since it's pure Java.
* Start at a slow sensor-polling rate and measure on the actual Pi. Model size,
  sensor count, and JVM configuration all affect cost, so do not assume a specific
  real-time rate before profiling.
* For "say something," the simplest bridge is shelling out to `espeak`/`festival` or
  a Python TTS helper process from Java, keyed off the anomaly signal.
* For motor/GPIO control, use a small native bridge (Pi4J, or a serial link to an
  Arduino/microcontroller handling the actuators) triggered from the same Java
  process that runs HTM.

## 3. Game integration — HTM as a lightweight "notices patterns" NPC/system

Idea: feed a stream of game-state numbers (player position deltas, score deltas,
input timing, enemy spawn intervals) into the toy HTM loop and use the anomaly score
or predicted-column overlap as a signal inside the game.

Example uses:

* **Adaptive difficulty:** if HTM stops flagging the player's input pattern as
  anomalous (i.e., it has learned the player's rhythm), raise difficulty; a fresh
  anomaly spike (player changed strategy) resets to a calmer ramp.
* **NPC "learns your habits":** encode the player's recent action sequence
  (categorical) with `CategoryEncoder`, run it through `TemporalMemory`, and have an
  NPC react differently once its predictions about the player start succeeding
  consistently.
* **Procedural event pacing:** treat spawn/loot timing as a scalar stream; use
  `AnomalyLikelihood` to decide when the game world should introduce a surprise
  event, rather than a fixed random timer.

Integration mechanics:

* Games in Java (e.g. LibGDX) can embed HTM.java directly as a dependency and call
  `SpatialPooler`/`TemporalMemory` each frame or tick.
* For games in other engines/languages (Unity/C#, Godot, etc.), run the HTM loop as
  a small local Java service and communicate over a local socket or simple HTTP
  endpoint, sending game-state numbers in and reading back an anomaly score /
  predicted-pattern flag.
* Usually feed meaningful game events or fixed-rate samples, not every render
  frame. Keep the encoder resolution tuned to that input rate: too fine and small
  changes look unrelated; too coarse and distinct situations collapse together.

## Common beginner traps

* **Expecting useful predictions immediately.** The model needs repeated examples
  before its connections become useful.
* **Treating anomaly as a verdict.** A high score says "I did not expect this." It
  does not say whether the event is dangerous, fraudulent, or broken.
* **Changing several things at once.** Begin with one input stream and one known
  repeating pattern. Add fields only after the simple case makes sense.
* **Using dense inputs.** SDRs depend on sparsity; only a small fraction of bits or
  columns should be active at once.
* **Learning forever without control.** In an application, decide when `learn`
  should be true. Otherwise unusual events may eventually become normal simply
  because the model keeps training on them.

## Suggested next steps

1. Run the visualizer and watch a normal cycle, a queued surprise, and a reset.
2. Build the toy loop from section 1 as a small standalone class/example under
   `src/main/java` (or a new `examples/` package) using `ScalarEncoder` +
   `SpatialPooler` + `TemporalMemory` + `Anomaly`, printing per-step diagnostics.
3. Validate it against one of the bundled sample CSVs already in the repo
   (`rec-center-hourly.csv`, `days-of-week-stream.csv`) before wiring up real
   sensors or a game.
4. Once the toy loop is understood, decide whether the Pi/robotics path or the game
   path is the next target, and reuse the same encoder → SpatialPooler →
   TemporalMemory → Anomaly pipeline as the common core.
