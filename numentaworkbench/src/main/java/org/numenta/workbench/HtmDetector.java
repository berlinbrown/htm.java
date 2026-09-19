package org.numenta.workbench;

import org.numenta.nupic.Parameters;
import org.numenta.nupic.Parameters.KEY;
import org.numenta.nupic.algorithms.Anomaly;
import org.numenta.nupic.algorithms.TemporalMemory;
import org.numenta.nupic.encoders.ScalarEncoder;
import org.numenta.nupic.model.Connections;
import org.numenta.nupic.util.ArrayUtils;
import org.numenta.nupic.util.MersenneTwister;

/**
 * Actual htm.java sequence learning: scalar SDR -> Temporal Memory -> raw surprise.
 * Encoder bits map directly to columns; no Spatial Pooler is used in this baseline.
 * This is an original workbench configuration, not NAB's official HTM detector.
 */
public final class HtmDetector implements Detectors.Detector {
    private final ScalarEncoder encoder;
    private final TemporalMemory memory = new TemporalMemory();
    private final Connections connections = new Connections();
    private boolean learning = true;

    public HtmDetector(double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || max < min)
            throw new IllegalArgumentException("HTM needs a finite ordered input range");
        // A flat series still needs a nonzero encoder range.
        if (max == min) max = min + 1;
        encoder = ScalarEncoder.builder().n(128).w(9).minVal(min).maxVal(max)
            .periodic(false).clipInput(true).forced(true).build();
        Parameters p = Parameters.getTemporalDefaultParameters();
        p.set(KEY.COLUMN_DIMENSIONS, new int[]{128});
        p.set(KEY.CELLS_PER_COLUMN, 8);
        p.set(KEY.ACTIVATION_THRESHOLD, 6);
        p.set(KEY.MIN_THRESHOLD, 4);
        p.set(KEY.MAX_NEW_SYNAPSE_COUNT, 12);
        p.set(KEY.INITIAL_PERMANENCE, 0.21);
        p.set(KEY.CONNECTED_PERMANENCE, 0.2);
        p.set(KEY.PERMANENCE_INCREMENT, 0.1);
        p.set(KEY.PERMANENCE_DECREMENT, 0.1);
        p.set(KEY.SEED, 42);
        p.set(KEY.RANDOM, new MersenneTwister(42));
        p.apply(connections);
        TemporalMemory.init(connections);
    }

    /** Freeze learned links while continuing to advance the sequence context. */
    public void setLearning(boolean enabled) { learning = enabled; }

    public double score(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite input");
        int[] active = ArrayUtils.where(encoder.encode(value), ArrayUtils.WHERE_1);
        // Compare with yesterday's guess BEFORE today's observation updates it.
        int[] predicted = connections.getPredictiveCells().stream()
            .mapToInt(cell -> cell.getColumn().getIndex()).distinct().sorted().toArray();
        double surprise = Anomaly.computeRawAnomalyScore(active, predicted);
        memory.compute(connections, active, learning);
        return surprise;
    }
}
