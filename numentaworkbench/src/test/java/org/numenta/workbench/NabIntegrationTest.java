package org.numenta.workbench;

import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Every selected dataset is replayed completely with three ports and htm.java. */
@RunWith(Parameterized.class)
public class NabIntegrationTest {
    @Parameterized.Parameters(name="{0}")
    public static Collection<Object[]> examples() throws Exception {
        List<Object[]> cases = new ArrayList<>();
        String selection = System.getProperty("example", "");
        if (selection.startsWith("${")) selection = "";
        for (Corpus.Example e : Corpus.examples())
            if (selection.isEmpty() || selection.equals("all") || selection.equals(e.id)) cases.add(new Object[]{e});
        if (cases.isEmpty()) throw new IllegalArgumentException("Unknown example: " + selection);
        return cases;
    }
    private final Corpus.Example example;
    public NabIntegrationTest(Corpus.Example example) { this.example = example; }
    @Test public void gaussian() throws Exception { verify("gaussian"); }
    @Test public void entropy() throws Exception { verify("entropy"); }
    @Test public void nullBaseline() throws Exception { verify("null"); }
    @Test public void htm() throws Exception { verify("htm"); }
    private void verify(String detector) throws Exception {
        Workbench.Result result = Workbench.run(example, detector, null);
        assertEquals(Corpus.rows(example).size(), result.records);
        assertTrue(result.records > 100);
        assertTrue(result.minScore >= 0 && result.maxScore <= 1);
        assertTrue(result.hitWindows <= result.totalWindows);
        if (result.totalWindows > 0) assertTrue("Labels must overlap the data", result.labeledPoints > 0);
        if (detector.equals("null")) {
            assertEquals(0, result.alerts);
            assertEquals(0.5, result.minScore, 0);
            assertEquals(0.5, result.maxScore, 0);
        }
    }
}
