# htm.java

Unofficial open source htm.java version (Berlin Brown)

## Updates

Currently can build with Maven:

Will not use gradle

```text
Apache Maven 3.8.6

Test Compile with Java 11, compile to Java8

IDE Environment - Tested with Eclipse - Version: 2023-12 (4.30.0)

At project root

$ mvn clean package

or

$ mvn clean compile exec:java


Unit and Integration tests will run

```
<br>

## Swing Temporal Memory Visualizer

Run the visual example from the project root:

```bash
mvn compile exec:java@visualizer
```

The visualizer simulates a room thermostat reporting 24 hourly readings each day:

```text
12 AM 64°F → 1 AM 63°F → ... → Noon 76°F → ... → 11 PM 65°F → next day
```

This example runs the complete learning path:

```text
hour ─────────→ periodic ScalarEncoder ─┐
                                       ├→ Spatial Pooler → active columns → Temporal Memory
temperature ──→ ScalarEncoder ─────────┘
```

The temperatures are simulated sensor readings, but they are ordinary real-world
values processed by the actual encoder and Spatial Pooler. Numbers such as
`[2, 3, 5]` are internal column addresses, not temperatures and not numbers the
model is learning to count.

### How to read the screen, from top to bottom

Suppose the paused screen contains:

```text
Thermostat input: Day 2 · 6 PM · 73°F
Previous guess:    6 PM 73°F (plus internal column IDs)
Next sensor guess: 7 PM 71°F (plus internal column IDs)
Anomaly:            0.000
```

This means the model expected the 6 PM reading, received it, and now expects the
7 PM reading. An anomaly of `0.000` means the current
input was completely expected. An anomaly near `1.000` means it was surprising.

The remaining parts of the screen are:

1. **Step and mode** — shows how many patterns have been processed and whether the
   input belongs to the repeating sequence or is an injected surprise.
2. **Segments and synapses** — counts the internal connections learned by Temporal
   Memory. These are not scores, and a larger number is not automatically better.
3. **Result banner** — summarizes whether the previous prediction matched the
   current input. An exact match contains no missed columns and no extra guesses.
4. **What is happening?** — explains in plain English what the model did during
   the current step.
5. **Recent inputs** — displays the observed sequence from left to right. The
   model's **next predicted reading** is placed on its own row immediately below,
   so it remains visible even when the history is long.
6. **Goal** — a teaching aid that asks the model to predict all 24 hours of one
   complete simulated day exactly. Temporal Memory itself does not have desires
   or an intrinsic goal. Reaching 24/24 describes the most recent day; it does not
   guarantee that every future reading will be predicted forever.
7. **ETA** — a best-case estimate based on the remaining goal steps and playback
   delay. A failed prediction can reset the streak, so the estimate is not a
   guarantee. The display also shows how many real seconds one simulated day takes
   at the selected delay (`10.8` seconds at the default `450 ms` per hour).

### How to read the grid

Read the grid in two directions:

```text
Across: C0 through C15    = input columns (which pattern is present)
Down: Context cell 0–3   = sequence contexts (what came before)
```

Every column has a state label:

* **INPUT** — the column belongs to the current input.
* **NEXT** — the model predicts the column for the next input.
* **BOTH** — the column is active now and also predictive.
* **—** — the column is neither active nor predicted.

The individual circles expose the cell-level state:

* **Cyan** — active now.
* **Gold** — predicted for the next step.
* **Green** — both active and predictive.
* **Gray** — neither active nor predictive at this moment.

For example, a set of cyan columns can be the internal representation of
`6 PM 73°F`, while a set of gold columns can represent the predicted `7 PM 71°F`
reading. The exact column IDs are created by the Spatial Pooler. The top labels
translate these patterns back into human-friendly sensor values whenever a known
pattern matches.

The four cells inside each column do not represent the numbers 0–3 as input data.
They let the same column participate in different sequence contexts. When all four
cells in an active column turn cyan, the column is **bursting**: the input arrived,
but no cell correctly predicted its context. After learning, normally one predicted
cell activates instead.

### Controls and a useful experiment

* **Pause/Resume** controls automatic playback.
* **Next step** advances once and remains paused.
* **Surprise next** adds a `12°F` heat spike to the next normal thermostat reading.
  Surprises are manual; the app does not secretly inject them on a timer.
* **Reset learning** creates a fresh model with no learned sequence connections.
* **Learning ON** allows inputs to change connections. Turning it OFF freezes the
  connections while the model continues making predictions.
* **Delay** controls the time between automatic steps.

A useful experiment is to let the model reach its goal, turn learning OFF, and
then press **Surprise next**. The anomaly should rise without teaching the surprise
to the frozen model.

For a gentler introduction to HTM concepts and possible projects, see the
[learning and integration notes](docs/README.md).

## Where HTM Fits in the AI Landscape

HTM is one part of an intelligent system rather than a complete artificial brain.
Its clearest question is:

> Given the recent sequence of observations, what pattern will probably happen
> next, and how surprising is the actual next observation?

That makes it useful for continuously changing data such as temperatures, machine
readings, movement, user activity, and robot sensors.

### HTM.java and the Thousand Brains Project

The projects are historically and philosophically related, but HTM.java is **not**
a Java implementation of the current Thousand Brains Project.

```text
Earlier HTM / NuPIC research
        │
        ├── HTM.java
        │   Spatial Pooler + Temporal Memory
        │   Streaming sequence prediction and anomaly detection
        │
        └── Thousand Brains Project / Monty
            Sensorimotor object and pose learning
            Sensors, movement, locations, reference frames, and voting
```

HTM.java is a Java port of the older NuPIC algorithms in this repository. It is a
good environment for exploring SDRs, encoders, Spatial Pooling, Temporal Memory,
and anomaly detection.

[Monty](https://github.com/thousandbrainsproject/tbp.monty) is the newer Python
sensorimotor framework. Its current focus includes learning and recognizing objects
and their poses while a sensor moves through an environment. See the
[Monty implementation overview](https://docs.thousandbrains.org/docs/implementation-overview)
and [robotics tutorials](https://docs.thousandbrains.org/docs/tutorials).

In short:

```text
HTM.java: “Given this stream, what pattern probably comes next?”
Monty:    “As my sensor moves, what object is this and where is it?”
```

### HTM, language models, and reinforcement learning

All three involve learning and prediction, but they answer different questions:

| Technology | Main question | Typical output |
| --- | --- | --- |
| HTM | What sensory pattern probably happens next? | Prediction and anomaly |
| LLM | What token probably comes next in this context? | Text or structured language |
| Reinforcement learning | Which action should maximize future reward? | An action policy |

HTM learns small streaming patterns online using cells, segments, synapses, and
permanence values. It does not contain broad world knowledge or generate language
like an LLM. It also has no built-in reward, goal, or action-selection policy like
reinforcement learning.

These approaches can complement one another:

```text
Camera and sensors
        ↓
Vision model: “What is visible?”
        ↓
HTM: “What is likely to happen next?”
        ↓
Controller or reinforcement learning: “What should the robot do?”
        ↓
LLM: “How should it understand or explain the situation?”
```

### Webcam and robotics example

HTM should not be expected to locate a banana, apple, face, or bear directly in
raw camera pixels. A conventional vision detector should first convert the image
into observations:

```text
Webcam frame
    ↓
OpenCV + an object detector
    ↓
object=banana, x=0.35, y=0.52, size=0.18
    ↓
HTM learns the sequence of those observations
```

The detector answers **what is visible now**. HTM can learn **what normally follows**
or **how an object normally moves**. For example, repeated training might teach:

```text
banana → empty → empty → empty → empty → apple
```

With one observation per second, seeing a banana again could cause the model to
expect four empty observations and then an apple. If a face appears instead, the
sequence is anomalous. HTM learns the order; the fixed sampling interval gives the
application its approximate timing.

For a moving object:

```text
banana at left → banana at center → banana at right
```

an object detector draws the box, a tracker follows it, and HTM can predict its
next position or flag an unexpected reversal. In Java, a practical stack is:

* OpenCV for webcam capture, preprocessing, boxes, and tracking.
* ONNX Runtime, OpenCV DNN, or DJL for object detection/classification.
* HTM.java for temporal prediction and anomaly detection.
* Ordinary control logic or reinforcement learning for actions.
* Pi4J later, when Java needs to operate Raspberry Pi GPIO, motors, or servos.

Safety-critical robot behavior must remain in explicit, tested control logic. For
example, animal or person detection should trigger a conservative safety rule
without waiting for HTM to learn whether the situation is dangerous.

#### [Official is Here](https://github.com/numenta/htm.java/issues/193)  **Java&trade;** version of...
## Hierarchical Temporal Memory [(HTM)](http://numenta.com/learn/principles-of-hierarchical-temporal-memory.html)

Based on official version, fork for simplicity

**Community-supported & ported from the** [Numenta Platform for Intelligent Computing (NuPIC) ](https://github.com/numenta/nupic) python project.

_**NOTE: Minimum JavaSE version is 8**_

<br>

## Versioning
(Tracked according to core algorithms)  

| Core Algorithm  | NuPIC Date    |HTM.Java Date | Latest NuPIC SHA | Latest HTM.Java SHA | Status|
| --------------- |:-------------:|:------------:|:----------------:|:-------------------:|:-----:|
| SpatialPooler   | 2016-12-11    | 2016-10-07   |[commit](https://github.com/numenta/nupic/commit/5c3edead9526d3b5fb6a4f37ad9d38cdcf32f5ff)|[commit](https://github.com/numenta/htm.java/commit/2cdcee1fcc5f6c18c2c48b4b553c49879c1256bb#diff-22f96ea06fd0c2b3593c755cbccf0a8b)| [*Behind NuPIC Merge #3411](https://github.com/numenta/nupic/pull/3411)
| TemporalMemory  | 2017-06-02    | 2016-10-13   |[commit](https://github.com/numenta/nupic/commit/b1f35fe15a1cbed689d1173cfcecddfab781baab)|[commit](https://github.com/numenta/htm.java/commit/7f4d8f2e2c910dd662909442546516e36adfc7cc)| [*Behind NuPIC Merge #3654](https://github.com/numenta/nupic/pull/3654)

\* May be one of: "Sync'd" or "Behind". "Behind" expresses a temporary lapse in synchronization while devs are implementing new changes.

<sub><sup>**NOTE:** "Behind" status does not imply _**any**_ lack of operational ability. The ```master``` branch of HTM.Java will always be _**fully**_ operational. 
<br>
Any fully critical feature addition to NuPIC will _**always**_ be matched (sync'd) ASAP, however due to ongoing updates, the algortithms within NuPIC may reach ahead for a short period of time while the HTM Community is busy porting the difference(s). We are committed to keeping HTM.Java up to date with NuPIC at _**all**_ times.</sup></sub>
***

## Project Goals

The primary goal of this library development is to provide a Java version of NuPIC that has a 1-to-1 correspondence to all systems, functionality and tests provided by Numenta's open source implementation; while observing the tenets, standards and conventions of Java language best practices and development.

By working closely with Numenta and receiving their enthusiastic support and guidance, it is intended that this library be maintained as a viable Java language alternative to Numenta's C++ and Python offerings. However it must be understood that "official" support is (for the time being) currently limited to community resources such as the maintainers of this library and Numenta Forums / Message Lists and IRC:

 * [NuPIC Community](http://numenta.org/)
 * [New HTM Forum](http://discourse.numenta.org)

***
## How Do I Get The Code? 

* **(A)** Instructions for developers who would like to contribute code back to the community.  (Fork)  
* **(B)** Instructions for those who would like to "fiddle around" with the code in thier own github repo.  (Clone)  
* **(C)** How to download Zipped or Tar'd Tagged Releases  (Download Zip or Tar)  

**A.** Developers who wish to make contributions are required to [Fork the htm.java repo](https://help.github.com/articles/fork-a-repo/) and then clone from their personal "Fork" of htm.java...
```
your_git_directory% git clone https://github.com/<your_github_username>/htm.java.git
```

**B.** Anybody who just wants to "play" with the code...
```
your_git_directory% git clone https://github.com/numenta/htm.java.git
```

**C.** [Proceed here](https://github.com/numenta/htm.java/releases) to download the latest tagged release (or older if you like)

_The instructions on the above link **(Fork the htm.java repo)** provide detail about how to fork github repos..._  

**In addition:** [a video is provided](https://www.youtube.com/watch?v=Yc3PKaT1knU) that explains Numenta's contributor rules and plenty of helpful tips on using git and other commands.

***

[license]:LICENSE.txt
[license img]:https://img.shields.io/badge/License-GNU%20Affero-blue.svg

[docs-badge]:https://img.shields.io/badge/API-docs-blue.svg?style=flat-square
[docs]:http://numenta.org/docs/htm.java/

## About This Fork

This repository is a personal research and development fork of [Numenta's htm.java](https://github.com/numenta/htm.java), a Java implementation of Hierarchical Temporal Memory (HTM).

The goals of this fork include:

* Studying HTM concepts through working Java code
* Modernizing and documenting parts of the project
* Creating visualizations that explain how HTM operates
* Experimenting with alternative implementations and applications
* Making the code easier to explore from a software-engineering perspective

Some changes may be developed with assistance from AI coding tools. All AI-assisted changes are reviewed, tested, and maintained by Berlin Brown.

The original project and applicable source files are copyright Numenta, Inc. Modifications made in this fork are identified through the repository's Git history.

This project remains licensed under the [GNU Affero General Public License version 3](LICENSE). The original copyright notices and license terms remain in effect.
