package org.numenta.workbench;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Small detection workbench, not an implementation of official NAB scoring. */
public final class Workbench {
    private Workbench() {}
    public static final class Result {
        public int records, alerts, labeledPoints, hitWindows, totalWindows, outsideAlerts;
        public double minScore = 1, maxScore;
        public int evaluatedRecords;
        public double scoreSum;
    }
    public static Result run(Corpus.Example example, String detectorName, Path output) throws IOException {
        return run(example, detectorName, output, 0.99, 0);
    }

    /** Warmup trains the model but excludes initial records from alert counts. */
    public static Result run(Corpus.Example example, String detectorName, Path output,
                             double threshold, int warmup) throws IOException {
        if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1 || warmup < 0)
            throw new IllegalArgumentException("Threshold must be in [0,1]; warmup must be nonnegative");
        List<Corpus.Row> rows = Corpus.rows(example);
        List<Corpus.Window> windows = Corpus.windows(example);
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (Corpus.Row row : rows) { min = Math.min(min, row.value); max = Math.max(max, row.value); }
        // NAB's entropy detector uses full-file min/max. This is benchmark
        // calibration, not a claim of strictly causal live-stream preprocessing.
        Detectors.Detector detector = Detectors.create(detectorName, min, max);
        Result result = new Result();
        boolean[] hits = new boolean[windows.size()];
        result.totalWindows = windows.size();
        if (output != null) Files.createDirectories(output);
        try (BufferedWriter writer = output == null ? null : Files.newBufferedWriter(
                output.resolve(detectorName + "_" + example.id + ".csv"), StandardCharsets.UTF_8)) {
            if (writer != null) writer.write("timestamp,value,anomaly_score,label,alert,evaluated\n");
            for (Corpus.Row row : rows) {
                double score = detector.score(row.value);
                if (!Double.isFinite(score) || score < 0 || score > 1)
                    throw new IllegalStateException("Invalid detector score: " + score);
                boolean evaluated = result.records >= warmup;
                boolean alert = evaluated && score >= threshold;
                boolean labeled = false;
                for (int i = 0; i < windows.size(); i++) {
                    if (windows.get(i).contains(row.time)) {
                        labeled = true;
                        if (alert) hits[i] = true;
                    }
                }
                result.records++;
                if (evaluated) {
                    result.evaluatedRecords++;
                    result.scoreSum += score;
                }
                if (alert) result.alerts++;
                if (labeled) result.labeledPoints++;
                if (alert && !labeled) result.outsideAlerts++;
                result.minScore = Math.min(result.minScore, score);
                result.maxScore = Math.max(result.maxScore, score);
                if (writer != null) writer.write(row.time.toString().replace('T', ' ') + "," +
                    row.value + "," + score + "," + (labeled ? 1 : 0) + "," + (alert ? 1 : 0) +
                    "," + (evaluated ? 1 : 0) + "\n");
            }
        }
        for (boolean hit : hits) if (hit) result.hitWindows++;
        return result;
    }
    public static void main(String[] args) throws Exception {
        String selection = args.length == 0 ? "all" : args[0];
        if (selection.equals("--help")) {
            System.out.println("Usage: Workbench [list|all|EXAMPLE_ID] [gaussian|entropy|null|htm] [output-directory]");
            return;
        }
        if (args.length > 3) throw new IllegalArgumentException("Too many arguments; use --help");
        List<Corpus.Example> selected = new ArrayList<>();
        for (Corpus.Example example : Corpus.examples()) {
            if (selection.equals("list")) System.out.println(example.id + " : " + example.description);
            if (selection.equals("all") || selection.equals(example.id)) selected.add(example);
        }
        if (selection.equals("list")) return;
        if (selected.isEmpty()) throw new IllegalArgumentException("Unknown example: " + selection + "; use list");
        String detector = args.length > 1 ? args[1] : "gaussian";
        double threshold = Double.parseDouble(System.getProperty("wb.threshold", "0.99"));
        int warmup = Integer.parseInt(System.getProperty("wb.warmup", "0"));
        Path output = Paths.get(args.length > 2 ? args[2] : "target/nab-results");
        Files.createDirectories(output);
        try (BufferedWriter summary = Files.newBufferedWriter(output.resolve(detector + "_summary.csv"), StandardCharsets.UTF_8)) {
            summary.write("example,records,alerts,hit_windows,total_windows,outside_alerts,evaluated_records,mean_score,threshold,warmup\n");
            for (Corpus.Example example : selected) {
                Result r = run(example, detector, output, threshold, warmup);
                String line = example.id + "," + r.records + "," + r.alerts + "," +
                    r.hitWindows + "," + r.totalWindows + "," + r.outsideAlerts + "," +
                    r.evaluatedRecords + "," + (r.evaluatedRecords == 0 ? "" : r.scoreSum / r.evaluatedRecords) +
                    "," + threshold + "," + warmup;
                summary.write(line + "\n");
                System.out.println(line);
            }
        }
        System.out.println("Results: " + output.toAbsolutePath() + " (descriptive counts, NOT official NAB scores)");
    }
}
