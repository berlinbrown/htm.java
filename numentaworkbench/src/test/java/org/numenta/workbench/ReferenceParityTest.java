package org.numenta.workbench;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Compare every score against the saved Python-run results shipped by NAB. */
public class ReferenceParityTest {
    @Test public void gaussianMatchesNabTaxiReference() throws Exception {
        compare("nyc_taxi", "gaussian", 1e-6);
    }
    @Test public void entropyMatchesNabJumpReference() throws Exception {
        compare("art_daily_jumpsup", "entropy", 0);
    }
    private void compare(String id, String name, double tolerance) throws Exception {
        Corpus.Example example = Corpus.examples().stream().filter(e -> e.id.equals(id)).findFirst().get();
        List<Corpus.Row> rows = Corpus.rows(example);
        double min = rows.stream().mapToDouble(r -> r.value).min().getAsDouble();
        double max = rows.stream().mapToDouble(r -> r.value).max().getAsDouble();
        Detectors.Detector detector = Detectors.create(name, min, max);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/reference/" + name + "_" + id + ".csv"), StandardCharsets.UTF_8))) {
            reader.readLine();
            for (Corpus.Row row : rows) {
                String line = reader.readLine();
                assertNotNull("Reference ended early", line);
                String[] fields = line.split(",");
                assertEquals(Corpus.timestamp(fields[0]), row.time);
                assertEquals(Double.parseDouble(fields[1]), row.value, 1e-5);
                assertEquals("score at " + row.time, Double.parseDouble(fields[2]),
                    detector.score(row.value), tolerance);
            }
            assertNull("Unexpected extra reference records", reader.readLine());
        }
    }
}
