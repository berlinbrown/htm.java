package org.numenta.workbench;

import java.util.*;

/**
 * Java ports of NAB's windowedGaussian, relative_entropy and null detectors,
 * plus the workbench's htm.java sequence-learning configuration.
 * See NOTICE.md for upstream attribution. Each instance belongs to ONE series.
 */
public final class Detectors {
    private Detectors() {}
    public interface Detector { double score(double value); }

    public static Detector create(String name, double min, double max) {
        switch (name) {
            case "htm": return new HtmDetector(min, max);
            case "gaussian": return new Gaussian();
            case "entropy": return new Entropy(min, max);
            case "null": return value -> 0.5;
            default: throw new IllegalArgumentException("Unknown detector: " + name);
        }
    }

    /** Compare a reading with the recent bell curve BEFORE learning that reading. */
    public static final class Gaussian implements Detector {
        private final List<Double> window = new ArrayList<>();
        private final List<Double> buffer = new ArrayList<>();
        private double mean, std = 1;
        public double score(double value) {
            double result = window.isEmpty() ? 0 : 1 - tail(Math.abs(value - mean) / std);
            if (window.size() < 6400) {
                window.add(value);
                update();
            } else {
                buffer.add(value);
                if (buffer.size() == 100) {
                    window.subList(0, 100).clear();
                    window.addAll(buffer);
                    buffer.clear();
                    update();
                }
            }
            return result;
        }
        private void update() {
            mean = 0;
            for (double v : window) mean += v;
            mean /= window.size();
            double variance = 0;
            for (double v : window) variance += (v - mean) * (v - mean);
            std = Math.sqrt(variance / window.size()); // Population, not sample, std.
            if (std == 0) std = 0.000001;
        }
    }

    // Abramowitz-Stegun normal CDF approximation; absolute error about 7.5e-8.
    // Java 8 has no erfc. This replaces Python math.erfc without native libraries.
    static double tail(double z) {
        if (z == 0) return 0.5;
        double t = 1 / (1 + 0.2316419 * z);
        double poly = t * (0.319381530 + t * (-0.356563782 +
            t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
        return Math.exp(-z * z / 2) / Math.sqrt(2 * Math.PI) * poly;
    }

    /** Remember familiar five-bin histograms; flag a window that matches none. */
    public static final class Entropy implements Detector {
        private final double min, step;
        private final Deque<Double> window = new ArrayDeque<>();
        private final List<double[]> hypotheses = new ArrayList<>();
        public Entropy(double min, double max) {
            this.min = min;
            this.step = (max - min) / 5;
        }
        public double score(double value) {
            window.addLast(value);
            if (window.size() > 52) window.removeFirst();
            if (step == 0 || window.size() < 52) return 0;
            double[] histogram = new double[5];
            for (double v : window) {
                // NumPy histogram includes the rightmost endpoint in its last bin.
                int bin = (int)Math.ceil((v - min) / step);
                histogram[Math.max(0, Math.min(4, bin))] += 1.0 / 52;
            }
            if (hypotheses.isEmpty()) {
                hypotheses.add(histogram);
                return 0;
            }
            for (double[] hypothesis : hypotheses) {
                double kl = 0;
                for (int i = 0; i < 5; i++) {
                    if (histogram[i] > 0) {
                        kl += histogram[i] * Math.log(histogram[i] / hypothesis[i]);
                    }
                }
                // scipy.stats.chi2.isf(0.01, 4). Upstream c_th=1 means a
                // previously seen hypothesis is always frequent enough.
                if (104 * kl < 13.276704135987622) return 0;
            }
            hypotheses.add(histogram);
            return 1;
        }
    }
}
