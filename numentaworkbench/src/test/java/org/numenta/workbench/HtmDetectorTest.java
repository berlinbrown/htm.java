package org.numenta.workbench;

import org.junit.Test;
import static org.junit.Assert.*;

/** Behavioral tests: demonstrate learning rather than merely valid score bounds. */
public class HtmDetectorTest {
    @Test public void learnsCycleAndDetectsUnexpectedTransition() {
        HtmDetector d = new HtmDetector(0, 100);
        double[] cycle = {10, 30, 50, 70};
        assertEquals(1, d.score(70), 0); // No learned prediction on the first record.
        for (int epoch = 0; epoch < 40; epoch++)
            for (double value : cycle) d.score(value);
        d.setLearning(false);
        for (int epoch = 0; epoch < 5; epoch++)
            for (double value : cycle) assertEquals("Familiar cycle", 0, d.score(value), 0);
        assertTrue("Unseen value should surprise the trained model", d.score(100) > 0.9);
    }

    @Test public void independentInstancesAreDeterministic() {
        HtmDetector a = new HtmDetector(0, 100), b = new HtmDetector(0, 100);
        for (int i = 0; i < 200; i++) {
            double value = (i % 5) * 20;
            assertEquals(a.score(value), b.score(value), 0);
        }
        HtmDetector fresh = new HtmDetector(0, 100);
        assertEquals("Training must not leak into another run", 1, fresh.score(0), 0);
    }

    @Test public void flatlineBecomesPredictable() {
        HtmDetector d = new HtmDetector(5, 5);
        for (int i = 0; i < 30; i++) d.score(5);
        assertEquals(0, d.score(5), 0);
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectsMissingNumericInput() { new HtmDetector(0, 1).score(Double.NaN); }
}
