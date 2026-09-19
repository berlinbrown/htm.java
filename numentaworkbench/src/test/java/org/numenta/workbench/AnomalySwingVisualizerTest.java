package org.numenta.workbench;

import java.awt.*;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

/** Tests the visualizer's real evaluation and rendering without opening a window. */
public class AnomalySwingVisualizerTest {
    @Test public void evaluatesHtmExampleAndRendersChartHeadlessly() throws Exception {
        Corpus.Example example = Corpus.examples().stream()
            .filter(e -> e.id.equals("art_daily_jumpsup")).findFirst().get();
        AnomalySwingVisualizer.PlotData data =
            AnomalySwingVisualizer.evaluate(example, "htm", 0.8, 100);

        assertEquals(Corpus.rows(example).size(), data.values.length);
        assertEquals(data.values.length, data.scores.length);
        assertEquals(data.values.length, data.labels.length);
        assertEquals(data.values.length, data.alerts.length);
        assertEquals(1, data.totalWindows);
        assertTrue(data.summary().contains("Known windows hit:"));
        for (double score : data.scores)
            assertTrue(Double.isFinite(score) && score >= 0 && score <= 1);

        AnomalySwingVisualizer.Chart chart = new AnomalySwingVisualizer.Chart();
        chart.setSize(1000, 600); chart.setData(data);
        BufferedImage image = new BufferedImage(1000, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        chart.paint(graphics); graphics.dispose();
        assertNotEquals("Chart should draw more than its white background",
            Color.WHITE.getRGB(), image.getRGB(100, 100));
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectsNegativeWarmup() throws Exception {
        AnomalySwingVisualizer.evaluate(Corpus.examples().get(0), "htm", 0.8, -1);
    }
}
