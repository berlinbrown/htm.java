package org.numenta.nupic.integration.driver;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.numenta.nupic.Parameters;
import org.numenta.nupic.Parameters.KEY;
import org.numenta.nupic.algorithms.SpatialPooler;
import org.numenta.nupic.algorithms.TemporalMemory;
import org.numenta.nupic.encoders.ScalarEncoder;
import org.numenta.nupic.model.Cell;
import org.numenta.nupic.model.ComputeCycle;
import org.numenta.nupic.model.Connections;
import org.numenta.nupic.util.MersenneTwister;

/**
 * A live visual demonstration of Temporal Memory learning a repeating sequence.
 */
public final class TemporalMemorySwingVisualizer {
    private static final Logger LOGGER = Logger.getLogger(TemporalMemorySwingVisualizer.class.getName());
    private static final int COLUMN_COUNT = 16;
    private static final int CELLS_PER_COLUMN = 4;
    private static final int TEMPERATURE_ENCODER_WIDTH = 64;
    private static final int HOUR_ENCODER_WIDTH = 24;
    private static final int ENCODER_WIDTH = TEMPERATURE_ENCODER_WIDTH + HOUR_ENCODER_WIDTH;
    private static final int GOAL_STREAK = 24;
    private static final int HISTORY_LENGTH = 6;
    private static final double[] HOURLY_TEMPERATURES = {
        64, 63, 62, 61, 60, 60, 61, 63, 66, 69, 72, 74,
        76, 77, 78, 78, 77, 75, 73, 71, 69, 67, 66, 65
    };

    private final SpatialPooler spatialPooler = new SpatialPooler();
    private final TemporalMemory temporalMemory = new TemporalMemory();
    private final ScalarEncoder temperatureEncoder = ScalarEncoder.builder()
        .n(TEMPERATURE_ENCODER_WIDTH)
        .w(5)
        .minVal(55.0)
        .maxVal(95.0)
        .periodic(false)
        .clipInput(true)
        .forced(true)
        .name("room temperature")
        .build();
    private final ScalarEncoder hourEncoder = ScalarEncoder.builder()
        .n(HOUR_ENCODER_WIDTH)
        .w(3)
        .minVal(0.0)
        .maxVal(24.0)
        .periodic(true)
        .forced(true)
        .name("hour of day")
        .build();
    private final int[][] knownColumnPatterns = new int[HOURLY_TEMPERATURES.length][];
    private Connections connections;
    private final CellGrid cellGrid = new CellGrid();
    private final JLabel stepLabel = metricLabel("Step 0");
    private final JLabel inputLabel = metricLabel("Thermostat input: waiting");
    private final JLabel matchLabel = metricLabel("Previous prediction: none yet");
    private final JLabel predictionLabel = metricLabel("Prediction for next input: warming up");
    private final JLabel anomalyLabel = metricLabel("Anomaly: -");
    private final JLabel learningLabel = metricLabel("Segments: 0 | Synapses: 0");
    private final JLabel explanationLabel = new JLabel(
        "<html><b>What is happening?</b> The model is waiting for its first input.</html>");
    private final JLabel qualityLabel = new JLabel("LEARNING", SwingConstants.CENTER);
    private final JLabel timelineLabel = new JLabel("Recent inputs: waiting for the first pattern");
    private final JLabel nextGuessLabel = new JLabel("Next predicted reading: waiting for data");
    private final JLabel goalLabel = new JLabel("Goal: one perfect 24-hour day (0/24)");
    private final JLabel timingLabel = new JLabel("Timing: waiting for data");
    private final Deque<String> recentInputs = new ArrayDeque<String>();
    private final Timer timer;

    private int step;
    private int[] previousPredictions = new int[0];
    private boolean running = true;
    private boolean learningEnabled = true;
    private boolean injectSurpriseOnNextStep;
    private int exactPredictionStreak;

    private TemporalMemorySwingVisualizer() {
        configureLogging();
        resetModel();
        timer = new Timer(450, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                advance();
            }
        });
        advance();
        timer.start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new TemporalMemorySwingVisualizer().show();
            }
        });
    }

    private void show() {
        JFrame frame = new JFrame("HTM Temporal Memory Visualizer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(12, 12));
        frame.add(createHeader(), BorderLayout.NORTH);
        frame.add(cellGrid, BorderLayout.CENTER);
        frame.add(createControls(), BorderLayout.SOUTH);
        frame.setMinimumSize(new Dimension(900, 640));
        frame.setSize(1040, 720);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(10, 8));
        header.setBorder(BorderFactory.createEmptyBorder(14, 16, 0, 16));

        JPanel metrics = new JPanel(new GridLayout(2, 3, 10, 8));
        metrics.add(stepLabel);
        metrics.add(inputLabel);
        metrics.add(matchLabel);
        metrics.add(anomalyLabel);
        metrics.add(learningLabel);
        metrics.add(predictionLabel);
        header.add(metrics, BorderLayout.NORTH);

        JPanel guide = new JPanel(new BorderLayout(10, 4));
        JLabel legend = new JLabel(
            "Read across: columns/input patterns   Read down: cells/sequence contexts");
        legend.setForeground(new Color(80, 90, 105));
        guide.add(legend, BorderLayout.NORTH);
        qualityLabel.setOpaque(true);
        qualityLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        qualityLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        updateQuality(0, 0, 0, false);
        guide.add(qualityLabel, BorderLayout.WEST);
        explanationLabel.setForeground(new Color(39, 66, 91));
        guide.add(explanationLabel, BorderLayout.CENTER);
        header.add(guide, BorderLayout.CENTER);

        JPanel progress = new JPanel(new GridLayout(4, 1, 0, 3));
        progress.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(205, 214, 223)),
            BorderFactory.createEmptyBorder(7, 0, 0, 0)));
        timelineLabel.setFont(new Font("Monospaced", Font.BOLD, 13));
        timelineLabel.setForeground(new Color(39, 66, 91));
        progress.add(timelineLabel);
        nextGuessLabel.setFont(new Font("Monospaced", Font.BOLD, 13));
        nextGuessLabel.setForeground(new Color(132, 82, 16));
        progress.add(nextGuessLabel);
        goalLabel.setForeground(new Color(80, 90, 105));
        progress.add(goalLabel);
        timingLabel.setForeground(new Color(80, 90, 105));
        progress.add(timingLabel);
        header.add(progress, BorderLayout.SOUTH);
        return header;
    }

    private JPanel createControls() {
        JPanel controls = new JPanel(new BorderLayout(10, 0));
        controls.setBorder(BorderFactory.createEmptyBorder(0, 16, 14, 16));

        final JButton pauseButton = new JButton("Pause");
        pauseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                running = !running;
                if (running) {
                    timer.start();
                    pauseButton.setText("Pause");
                } else {
                    timer.stop();
                    pauseButton.setText("Resume");
                }
            }
        });

        JButton nextButton = new JButton("Next step");
        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                running = false;
                timer.stop();
                pauseButton.setText("Resume");
                advance();
            }
        });

        JButton surpriseButton = new JButton("Surprise next");
        surpriseButton.setToolTipText("Use unexpected columns on the next step so you can watch anomaly rise");
        surpriseButton.addActionListener(event -> {
            injectSurpriseOnNextStep = true;
            surpriseButton.setText("Surprise queued");
            surpriseButton.setEnabled(false);
            Timer restoreButton = new Timer(timer.getDelay() + 100, restoreEvent -> {
                surpriseButton.setText("Surprise next");
                surpriseButton.setEnabled(true);
            });
            restoreButton.setRepeats(false);
            restoreButton.start();
        });

        JButton resetButton = new JButton("Reset learning");
        resetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                resetModel();
                updateDisplay("waiting", new int[0], new int[0], new HashSet<Cell>(), new HashSet<Cell>(),
                    false, 0.0, 0);
            }
        });

        JCheckBox learningToggle = new JCheckBox("Learning ON", true);
        learningToggle.setToolTipText("OFF freezes learned connections but keeps making predictions");
        learningToggle.addActionListener(event -> {
            learningEnabled = learningToggle.isSelected();
            learningToggle.setText(learningEnabled ? "Learning ON" : "Learning OFF");
            updateProgress(step == 0);
        });

        JPanel buttons = new JPanel();
        buttons.add(pauseButton);
        buttons.add(nextButton);
        buttons.add(surpriseButton);
        buttons.add(resetButton);
        buttons.add(learningToggle);
        controls.add(buttons, BorderLayout.WEST);

        JSlider speed = new JSlider(100, 1200, 450);
        speed.setToolTipText("Milliseconds between input steps");
        JLabel speedLabel = new JLabel("Delay: 450 ms", SwingConstants.RIGHT);
        speed.addChangeListener(event -> {
            timer.setDelay(speed.getValue());
            speedLabel.setText("Delay: " + speed.getValue() + " ms");
            updateProgress(step == 0);
        });
        JPanel speedControls = new JPanel(new BorderLayout(8, 0));
        speedControls.add(new JLabel("Faster"), BorderLayout.WEST);
        speedControls.add(speed, BorderLayout.CENTER);
        speedControls.add(speedLabel, BorderLayout.EAST);
        controls.add(speedControls, BorderLayout.CENTER);
        return controls;
    }

    private void resetModel() {
        connections = new Connections();
        Parameters parameters = Parameters.getAllDefaultParameters();
        parameters.set(KEY.INPUT_DIMENSIONS, new int[] { ENCODER_WIDTH });
        parameters.set(KEY.COLUMN_DIMENSIONS, new int[] { COLUMN_COUNT });
        parameters.set(KEY.CELLS_PER_COLUMN, CELLS_PER_COLUMN);
        parameters.set(KEY.POTENTIAL_RADIUS, ENCODER_WIDTH);
        parameters.set(KEY.POTENTIAL_PCT, 1.0);
        parameters.set(KEY.GLOBAL_INHIBITION, true);
        parameters.set(KEY.LOCAL_AREA_DENSITY, -1.0);
        parameters.set(KEY.NUM_ACTIVE_COLUMNS_PER_INH_AREA, 3.0);
        parameters.set(KEY.STIMULUS_THRESHOLD, 1.0);
        parameters.set(KEY.SYN_PERM_CONNECTED, 0.1);
        parameters.set(KEY.SYN_PERM_ACTIVE_INC, 0.05);
        parameters.set(KEY.SYN_PERM_INACTIVE_DEC, 0.008);
        parameters.set(KEY.ACTIVATION_THRESHOLD, 3);
        parameters.set(KEY.MIN_THRESHOLD, 2);
        parameters.set(KEY.MAX_NEW_SYNAPSE_COUNT, 4);
        parameters.set(KEY.RANDOM, new MersenneTwister(42));
        parameters.set(KEY.SEED, 42);
        parameters.apply(connections);
        spatialPooler.init(connections);
        TemporalMemory.init(connections);
        step = 0;
        previousPredictions = new int[0];
        injectSurpriseOnNextStep = false;
        exactPredictionStreak = 0;
        recentInputs.clear();
        Arrays.fill(knownColumnPatterns, null);
        warmUpSpatialPooler();
        LOGGER.info("Reset model: " + COLUMN_COUNT + " columns, " + CELLS_PER_COLUMN + " cells per column");
    }

    private void warmUpSpatialPooler() {
        int[] denseColumns = new int[COLUMN_COUNT];
        for (int pass = 0; pass < 40; pass++) {
            for (int hour = 0; hour < HOURLY_TEMPERATURES.length; hour++) {
                int[] input = concatenate(temperatureEncoder.encode(HOURLY_TEMPERATURES[hour]),
                    hourEncoder.encode((double) hour));
                Arrays.fill(denseColumns, 0);
                spatialPooler.compute(connections, input, denseColumns, true);
            }
        }
        for (int hour = 0; hour < HOURLY_TEMPERATURES.length; hour++) {
            int[] input = concatenate(temperatureEncoder.encode(HOURLY_TEMPERATURES[hour]),
                hourEncoder.encode((double) hour));
            Arrays.fill(denseColumns, 0);
            spatialPooler.compute(connections, input, denseColumns, false);
            knownColumnPatterns[hour] = activeIndices(denseColumns);
        }
    }

    private void advance() {
        boolean anomalyInput = injectSurpriseOnNextStep;
        injectSurpriseOnNextStep = false;
        int readingIndex = step % HOURLY_TEMPERATURES.length;
        int day = step / HOURLY_TEMPERATURES.length + 1;
        double normalTemperature = HOURLY_TEMPERATURES[readingIndex];
        double temperature = anomalyInput ? normalTemperature + 12.0 : normalTemperature;
        String readingLabel = "Day " + day + " · " + formatHour(readingIndex) + " · "
            + formatTemperature(temperature) + (anomalyInput ? " HEAT SPIKE" : "");
        int[] encodedInput = concatenate(temperatureEncoder.encode(temperature),
            hourEncoder.encode((double) readingIndex));
        int[] denseColumns = new int[COLUMN_COUNT];
        spatialPooler.compute(connections, encodedInput, denseColumns, false);
        int[] activeColumns = activeIndices(denseColumns);
        if (!anomalyInput) {
            knownColumnPatterns[readingIndex] = Arrays.copyOf(activeColumns, activeColumns.length);
        }
        int[] predictionForThisInput = previousPredictions;
        double anomalyScore = anomaly(activeColumns, previousPredictions);
        int correctPredictions = matchingColumns(activeColumns, previousPredictions);
        ComputeCycle cycle = temporalMemory.compute(connections, activeColumns, learningEnabled);
        previousPredictions = columnsOf(cycle.predictiveCells());
        step++;
        boolean exactPrediction = Arrays.equals(activeColumns, predictionForThisInput);
        exactPredictionStreak = exactPrediction ? exactPredictionStreak + 1 : 0;
        addToHistory(readingLabel, anomalyInput);
        updateDisplay(readingLabel, activeColumns, predictionForThisInput, cycle.activeCells(),
            cycle.predictiveCells(), anomalyInput, anomalyScore, correctPredictions);

        LOGGER.info(String.format(
            "Step %d: %s Input columns %s. %s The model now expects columns %s next. "
                + "[match=%d/%d, anomaly=%.3f, activeCells=%d, segments=%d, synapses=%d]",
            step, learningNarrative(activeColumns.length, correctPredictions, anomalyInput),
            Arrays.toString(activeColumns), predictionNarrative(activeColumns.length, correctPredictions),
            Arrays.toString(previousPredictions), correctPredictions, activeColumns.length, anomalyScore,
            cycle.activeCells().size(), connections.numSegments(), connections.numSynapses()));
    }

    private void updateDisplay(String readingLabel, int[] activeColumns, int[] predictionForThisInput,
            Set<Cell> activeCells, Set<Cell> predictiveCells,
            boolean anomalyInput, double anomalyScore, int correctPredictions) {
        cellGrid.setStates(activeCells, predictiveCells);
        boolean waiting = activeColumns.length == 0;
        stepLabel.setText(waiting ? "Step 0 - ready"
            : "Step " + step + (anomalyInput ? "  - injected surprise" : "  - repeating sequence"));
        inputLabel.setText(waiting ? "Thermostat input: waiting"
            : "Thermostat input: " + readingLabel);
        matchLabel.setText(waiting ? "Previous prediction: none yet"
            : "Previous guess: " + meaningOf(predictionForThisInput));
        predictionLabel.setText(waiting ? "Prediction for next input: warming up"
            : "Next sensor guess: " + meaningOf(previousPredictions));
        anomalyLabel.setText(String.format("Anomaly: %.3f", anomalyScore));
        learningLabel.setText("Segments: " + connections.numSegments() + " | Synapses: " + connections.numSynapses());
        explanationLabel.setText("<html><b>What is happening?</b> "
            + explanation(activeColumns.length, correctPredictions, predictionForThisInput.length, anomalyInput)
            + "</html>");
        updateQuality(activeColumns.length, correctPredictions, predictionForThisInput.length, anomalyInput);
        updateProgress(waiting);
    }

    private void addToHistory(String readingLabel, boolean anomalyInput) {
        String entry = readingLabel + (anomalyInput ? " ⚠" : "");
        recentInputs.addLast(entry);
        while (recentInputs.size() > HISTORY_LENGTH) {
            recentInputs.removeFirst();
        }
    }

    private void updateProgress(boolean waiting) {
        if (waiting) {
            timelineLabel.setText("Recent inputs: waiting for the first pattern");
            nextGuessLabel.setText("Next predicted reading: waiting for data");
            goalLabel.setText("Goal: one perfect 24-hour day (0/24) | Training: "
                + (learningEnabled ? "ON" : "OFF"));
            timingLabel.setText("Timing: " + playbackRate() + " | ETA: waiting for data");
            return;
        }
        timelineLabel.setText("Recent inputs: " + String.join("  →  ", recentInputs));
        nextGuessLabel.setText("NEXT PREDICTED READING: " + meaningOf(previousPredictions));
        if (exactPredictionStreak >= GOAL_STREAK) {
            goalLabel.setText("Goal reached: " + exactPredictionStreak
                + " exact hourly predictions in a row | Training: " + (learningEnabled ? "ON" : "OFF"));
            timingLabel.setText("Timing: " + playbackRate() + " | ETA: reached");
            goalLabel.setForeground(new Color(0, 105, 76));
        } else {
            goalLabel.setText("Goal: one perfect 24-hour day (" + exactPredictionStreak
                + "/" + GOAL_STREAK + ") | Training: " + (learningEnabled ? "ON" : "OFF"));
            timingLabel.setText("Timing: " + playbackRate() + " | " + goalEstimate());
            goalLabel.setForeground(new Color(80, 90, 105));
        }
    }

    private String meaningOf(int[] columns) {
        if (columns.length == 0) {
            return "none yet";
        }
        for (int i = 0; i < knownColumnPatterns.length; i++) {
            if (knownColumnPatterns[i] != null && Arrays.equals(columns, knownColumnPatterns[i])) {
            return formatHour(i) + " " + formatTemperature(HOURLY_TEMPERATURES[i])
                + " (internal columns " + Arrays.toString(columns) + ")";
            }
        }
        return "unknown pattern (internal columns " + Arrays.toString(columns) + ")";
    }

    private static String formatTemperature(double temperature) {
        return String.format("%.0f°F", temperature);
    }

    private static String formatHour(int hour) {
        if (hour == 0) {
            return "12 AM";
        }
        if (hour == 12) {
            return "12 PM";
        }
        return hour < 12 ? hour + " AM" : (hour - 12) + " PM";
    }

    private static int[] concatenate(int[] first, int[] second) {
        int[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static int[] activeIndices(int[] denseColumns) {
        int count = 0;
        for (int value : denseColumns) {
            if (value != 0) {
                count++;
            }
        }
        int[] result = new int[count];
        int resultIndex = 0;
        for (int i = 0; i < denseColumns.length; i++) {
            if (denseColumns[i] != 0) {
                result[resultIndex++] = i;
            }
        }
        return result;
    }

    private String goalEstimate() {
        if (!learningEnabled) {
            return "ETA paused (learning is OFF)";
        }
        if (exactPredictionStreak == 0) {
            return "ETA: estimating until the first exact prediction";
        }
        long milliseconds = (long) (GOAL_STREAK - exactPredictionStreak) * timer.getDelay();
        double seconds = milliseconds / 1000.0;
        return String.format("Best-case ETA: %.1f seconds", seconds);
    }

    private String playbackRate() {
        return String.format("1 simulated day = %.1f sec", HOURLY_TEMPERATURES.length * timer.getDelay() / 1000.0);
    }

    private static int[] columnsOf(Set<Cell> cells) {
        Set<Integer> columns = new HashSet<Integer>();
        for (Cell cell : cells) {
            columns.add(cell.getColumn().getIndex());
        }
        int[] result = new int[columns.size()];
        int index = 0;
        for (Integer column : columns) {
            result[index++] = column;
        }
        Arrays.sort(result);
        return result;
    }

    private static double anomaly(int[] actual, int[] predicted) {
        if (actual.length == 0) {
            return 0.0;
        }
        Set<Integer> expected = new HashSet<Integer>();
        for (int column : predicted) {
            expected.add(column);
        }
        int matches = 0;
        for (int column : actual) {
            if (expected.contains(column)) {
                matches++;
            }
        }
        return 1.0 - ((double) matches / actual.length);
    }

    private static int matchingColumns(int[] actual, int[] predicted) {
        Set<Integer> expected = new HashSet<Integer>();
        for (int column : predicted) {
            expected.add(column);
        }
        int matches = 0;
        for (int column : actual) {
            if (expected.contains(column)) {
                matches++;
            }
        }
        return matches;
    }

    private static String explanation(int inputSize, int correctPredictions, int predictionCount,
            boolean anomalyInput) {
        if (inputSize == 0) {
            return "The model is waiting for its first input.";
        }
        if (anomalyInput) {
            return "Surprise input: the learned sequence changed, so the anomaly score rises.";
        }
        if (correctPredictions == 0) {
            return "Learning: no previous prediction matched this input yet; active cells are forming new segments.";
        }
        if (correctPredictions == inputSize && predictionCount == inputSize) {
            return "Correct prediction: gold cells from the previous step became cyan; their segments are reinforced.";
        }
        if (correctPredictions == inputSize) {
            return "All active columns were predicted, but the model also predicted extra possibilities.";
        }
        return "Partial prediction: some gold cells became cyan; unmatched active columns are still learning.";
    }

    private void updateQuality(int inputSize, int correctPredictions, int predictionCount, boolean anomalyInput) {
        if (inputSize == 0) {
            setQuality("LEARNING", new Color(225, 232, 240), new Color(55, 72, 88));
            return;
        }
        double accuracy = (double) correctPredictions / inputSize;
        if (anomalyInput || accuracy == 0.0) {
            setQuality("BAD: UNEXPECTED", new Color(255, 220, 220), new Color(145, 35, 35));
        } else if (accuracy < 0.67) {
            setQuality("BETTER: PARTIAL", new Color(255, 235, 192), new Color(132, 82, 16));
        } else if (accuracy < 1.0) {
            setQuality("GOOD: MOSTLY RIGHT", new Color(219, 241, 207), new Color(42, 111, 41));
        } else if (predictionCount > inputSize) {
            setQuality("GOOD: RIGHT + EXTRA GUESSES", new Color(219, 241, 207), new Color(42, 111, 41));
        } else {
            setQuality("GREAT: EXACT MATCH", new Color(190, 238, 221), new Color(0, 105, 76));
        }
    }

    private void setQuality(String text, Color background, Color foreground) {
        qualityLabel.setText(text);
        qualityLabel.setBackground(background);
        qualityLabel.setForeground(foreground);
    }

    private static String learningNarrative(int inputSize, int correctPredictions, boolean anomalyInput) {
        if (anomalyInput) {
            return "A deliberate surprise was injected.";
        }
        if (correctPredictions == inputSize) {
            return "The previous prediction was completely correct.";
        }
        if (correctPredictions == 0) {
            return "This input was not predicted, so the model is learning its transition.";
        }
        return "The previous prediction was only partly correct, so the model is refining its transition.";
    }

    private static String predictionNarrative(int inputSize, int correctPredictions) {
        if (correctPredictions == inputSize) {
            return "Gold cells that matched this input became cyan and their connections were reinforced.";
        }
        if (correctPredictions == 0) {
            return "No gold cells matched this input; the active cells are building a new prediction.";
        }
        return "Matching gold cells were reinforced; the unmatched input is forming new connections.";
    }

    private static JLabel metricLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 14));
        return label;
    }

    private static void configureLogging() {
        LOGGER.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.ALL);
    }

    private static final class CellGrid extends JPanel {
        private static final int LEFT_GUTTER = 112;
        private static final int TOP_GUTTER = 54;
        private Set<Integer> active = new HashSet<Integer>();
        private Set<Integer> predictive = new HashSet<Integer>();

        private CellGrid() {
            setBackground(new Color(246, 248, 250));
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            setToolTipText("C0-C15 are input columns; rows 0-3 are learned sequence-context cells");
        }

        private void setStates(Set<Cell> activeCells, Set<Cell> predictiveCells) {
            active = indicesOf(activeCells);
            predictive = indicesOf(predictiveCells);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int availableWidth = Math.max(COLUMN_COUNT, getWidth() - LEFT_GUTTER);
            int availableHeight = Math.max(CELLS_PER_COLUMN, getHeight() - TOP_GUTTER);
            int columnWidth = availableWidth / COLUMN_COUNT;
            int cellHeight = availableHeight / CELLS_PER_COLUMN;
            int diameter = Math.max(12, Math.min(columnWidth - 14, cellHeight - 18));

            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.setColor(new Color(55, 72, 88));
            g.drawString("INTERNAL COLUMN", 12, 17);
            g.drawString("STATE", 12, 39);

            for (int cell = 0; cell < CELLS_PER_COLUMN; cell++) {
                int centerY = TOP_GUTTER + cell * cellHeight + cellHeight / 2;
                g.setColor(new Color(70, 82, 96));
                g.drawString("Context cell " + cell, 12, centerY + 5);
            }

            for (int column = 0; column < COLUMN_COUNT; column++) {
                int x = LEFT_GUTTER + column * columnWidth;
                boolean activeColumn = containsColumn(active, column);
                boolean predictiveColumn = containsColumn(predictive, column);
                Color columnTint = new Color(246, 248, 250);
                Color stateColor = new Color(205, 214, 223);
                String state = "—";
                if (activeColumn && predictiveColumn) {
                    columnTint = new Color(225, 244, 236);
                    stateColor = new Color(53, 151, 112);
                    state = "BOTH";
                } else if (activeColumn) {
                    columnTint = new Color(222, 244, 247);
                    stateColor = new Color(33, 170, 186);
                    state = "INPUT";
                } else if (predictiveColumn) {
                    columnTint = new Color(255, 244, 211);
                    stateColor = new Color(231, 179, 55);
                    state = "NEXT";
                }

                g.setColor(columnTint);
                g.fillRect(x + 2, 0, columnWidth - 4, getHeight());
                g.setColor(new Color(205, 214, 223));
                g.drawRect(x + 2, 0, columnWidth - 4, getHeight() - 1);
                g.setColor(new Color(70, 82, 96));
                drawCentered(g, "C" + column, x, columnWidth, 17);
                g.setFont(new Font("SansSerif", Font.BOLD, 9));
                g.setColor(stateColor);
                drawCentered(g, state, x, columnWidth, 39);
                g.setFont(new Font("SansSerif", Font.BOLD, 12));

                for (int cell = 0; cell < CELLS_PER_COLUMN; cell++) {
                    int cellIndex = column * CELLS_PER_COLUMN + cell;
                    int y = TOP_GUTTER + cell * cellHeight + (cellHeight - diameter) / 2;
                    Color color = new Color(188, 198, 208);
                    boolean activeCell = active.contains(cellIndex);
                    boolean predictiveCell = predictive.contains(cellIndex);
                    if (activeCell && predictiveCell) {
                        color = new Color(53, 151, 112);
                    } else if (predictiveCell) {
                        color = new Color(231, 179, 55);
                    } else if (activeCell) {
                        color = new Color(33, 170, 186);
                    }
                    g.setColor(color);
                    g.fillOval(x + (columnWidth - diameter) / 2, y, diameter, diameter);
                }
            }
            g.dispose();
        }

        private static boolean containsColumn(Set<Integer> cellIndices, int column) {
            for (Integer cellIndex : cellIndices) {
                if (cellIndex / CELLS_PER_COLUMN == column) {
                    return true;
                }
            }
            return false;
        }

        private static void drawCentered(Graphics2D g, String text, int x, int width, int baseline) {
            int textWidth = g.getFontMetrics().stringWidth(text);
            g.drawString(text, x + (width - textWidth) / 2, baseline);
        }

        private static Set<Integer> indicesOf(Set<Cell> cells) {
            Set<Integer> indices = new HashSet<Integer>();
            for (Cell cell : cells) {
                indices.add(cell.getIndex());
            }
            return indices;
        }
    }
}
