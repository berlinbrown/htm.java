package org.numenta.workbench;

import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class DetectorTest {
    @Test public void warmupTrainsButExcludesAlerts() throws Exception {
        Corpus.Example e = Corpus.examples().get(0);
        Workbench.Result r = Workbench.run(e, "null", null, 0.5, 100);
        assertEquals(r.records - 100, r.evaluatedRecords);
        assertEquals(r.evaluatedRecords, r.alerts);
        assertEquals(0.5, r.scoreSum / r.evaluatedRecords, 0);
    }

    @Test(expected=IllegalArgumentException.class)
    public void invalidThresholdFails() throws Exception {
        Workbench.run(Corpus.examples().get(0), "null", null, Double.NaN, 0);
    }
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    @Test public void gaussianReferenceSequence() {
        Detectors.Detector detector = new Detectors.Gaussian();
        // Python source scores BEFORE updating, uses population std, and floors
        // zero std at 1e-6. These values follow directly from that recurrence.
        assertEquals(0, detector.score(0), 0);
        assertEquals(1, detector.score(1), 1e-7);
        assertEquals(0.9986501019683699, detector.score(2), 1e-7);
        assertEquals(0.5, detector.score(1), 1e-7);
    }
    @Test public void gaussianSlidesAfterOneHundredBufferedRecords() {
        Detectors.Detector d = new Detectors.Gaussian();
        for (int i = 0; i < 6400; i++) d.score(0);
        for (int i = 0; i < 100; i++) assertEquals(1, d.score(1), 1e-7);
        // Updated window contains 6300 zeros and 100 ones.
        double z = (0.1 - 1.0 / 64) / Math.sqrt(63.0 / 4096);
        assertEquals(1 - Detectors.tail(z), d.score(0.1), 1e-12);
    }
    @Test public void entropyLearnsNewRegimeAndFlatline() {
        Detectors.Detector d = new Detectors.Entropy(0, 10);
        for (int i = 0; i < 52; i++) assertEquals(0, d.score(0), 0);
        assertEquals(1, d.score(10), 0); // New bin has no support in the old hypothesis.
        assertEquals(0, d.score(10), 0); // New hypothesis now explains this window.
        Detectors.Detector flat = new Detectors.Entropy(5, 5);
        for (int i = 0; i < 100; i++) assertEquals(0, flat.score(5), 0);
    }
    @Test public void catalogueIsTwentyUniqueExamples() throws Exception {
        Set<String> ids = new HashSet<>();
        for (Corpus.Example e : Corpus.examples()) assertTrue(ids.add(e.id));
        assertEquals(20, ids.size());
    }
    @Test public void labelsIncludeBothEndpoints() {
        Corpus.Window w = new Corpus.Window("2014-01-01 00:00:00.000000", "2014-01-01 01:00:00");
        assertTrue(w.contains(w.start));
        assertTrue(w.contains(w.end));
        assertFalse(w.contains(w.end.plusSeconds(1)));
    }
    @Test public void csvHasOneOutputPerInputAndRepeatableScores() throws Exception {
        Corpus.Example example = Corpus.examples().get(0);
        Path out = temp.newFolder().toPath();
        Workbench.Result first = Workbench.run(example, "gaussian", out);
        Path csv = out.resolve("gaussian_" + example.id + ".csv");
        byte[] before = Files.readAllBytes(csv);
        Workbench.run(example, "gaussian", out);
        assertArrayEquals(before, Files.readAllBytes(csv));
        assertEquals(first.records + 1, Files.readAllLines(csv).size());
    }
    @Test(expected=IllegalArgumentException.class) public void unknownDetectorFails() {
        Detectors.create("typo", 0, 1);
    }
    @Test(expected=IllegalArgumentException.class) public void unknownExampleFails() throws Exception {
        Workbench.main(new String[]{"typo"});
    }
}
