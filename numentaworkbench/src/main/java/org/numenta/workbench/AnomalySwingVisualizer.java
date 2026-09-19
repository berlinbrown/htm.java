package org.numenta.workbench;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Beginner-friendly desktop view of a NAB series and its anomaly scores.
 * Gold bands are NAB's known anomaly windows; red score spikes are detector alerts.
 */
public final class AnomalySwingVisualizer extends JFrame {
    private final JComboBox<Corpus.Example> exampleBox = new JComboBox<>();
    private final JComboBox<String> detectorBox = new JComboBox<>(
        new String[] { "htm", "gaussian", "entropy", "null" });
    private final JSpinner threshold = new JSpinner(new SpinnerNumberModel(0.8, 0.0, 1.0, 0.05));
    private final JSpinner warmup = new JSpinner(new SpinnerNumberModel(500, 0, Integer.MAX_VALUE, 100));
    private final JButton run = new JButton("Run detector");
    private final JLabel status = new JLabel("Choose an example, then run it.");
    private final JLabel summary = new JLabel(" ");
    private final Chart chart = new Chart();

    public AnomalySwingVisualizer() throws IOException {
        super("NAB + htm.java Anomaly Workbench");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 680));
        for (Corpus.Example example : Corpus.examples()) exampleBox.addItem(example);
        exampleBox.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                Corpus.Example e = (Corpus.Example)value;
                return super.getListCellRendererComponent(list,
                    e == null ? "" : e.id + " — " + e.description, index, selected, focus);
            }
        });

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBorder(new EmptyBorder(10, 10, 8, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 5, 3, 5); c.gridy = 0; c.anchor = GridBagConstraints.WEST;
        controls.add(new JLabel("Example:"), c); c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        controls.add(exampleBox, c); c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        controls.add(new JLabel("Detector:"), c); c.gridx = 3; controls.add(detectorBox, c);
        c.gridx = 4; controls.add(new JLabel("Alert at:"), c); c.gridx = 5; controls.add(threshold, c);
        c.gridx = 6; controls.add(new JLabel("Warmup rows:"), c); c.gridx = 7; controls.add(warmup, c);
        c.gridx = 8; controls.add(run, c);

        JPanel explanation = new JPanel(new GridLayout(3, 1, 2, 2));
        explanation.setBorder(new EmptyBorder(0, 15, 8, 15));
        explanation.add(new JLabel("Top chart: the actual measurement. Gold = a known NAB anomaly window."));
        explanation.add(new JLabel("Bottom chart: the detector's surprise score. Red = score crossed your alert line."));
        explanation.add(new JLabel("Warmup rows teach the detector but are not counted as alerts."));

        JPanel footer = new JPanel(new GridLayout(2, 1, 2, 2));
        footer.setBorder(new EmptyBorder(8, 15, 10, 15)); footer.add(status); footer.add(summary);
        // Group both top sections because BorderLayout has one north position.
        JPanel north = new JPanel(new BorderLayout()); north.add(controls, BorderLayout.NORTH);
        north.add(explanation, BorderLayout.SOUTH); add(north, BorderLayout.NORTH);
        add(chart, BorderLayout.CENTER); add(footer, BorderLayout.SOUTH);
        run.addActionListener(e -> runSelected());
        pack(); setLocationByPlatform(true);
    }

    private void runSelected() {
        final Corpus.Example example = (Corpus.Example)exampleBox.getSelectedItem();
        final String detector = (String)detectorBox.getSelectedItem();
        final double cutoff = ((Number)threshold.getValue()).doubleValue();
        final int ignored = ((Number)warmup.getValue()).intValue();
        run.setEnabled(false); status.setText("Running " + detector + " on " + example.id + "…");
        summary.setText(" ");
        new SwingWorker<PlotData, Void>() {
            protected PlotData doInBackground() throws Exception {
                return evaluate(example, detector, cutoff, ignored);
            }
            protected void done() {
                try {
                    PlotData data = get(); chart.setData(data);
                    summary.setText(data.summary());
                    status.setText("Finished. Move the mouse across either chart to inspect a row.");
                } catch (Exception ex) {
                    status.setText("Could not run: " + rootMessage(ex));
                } finally { run.setEnabled(true); }
            }
        }.execute();
    }

    static String rootMessage(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }

    /** Package-visible so headless tests exercise exactly the data shown by Swing. */
    static PlotData evaluate(Corpus.Example example, String detectorName,
                             double threshold, int warmup) throws IOException {
        if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1 || warmup < 0)
            throw new IllegalArgumentException("Threshold must be in [0,1]; warmup must be nonnegative");
        List<Corpus.Row> rows = Corpus.rows(example);
        List<Corpus.Window> windows = Corpus.windows(example);
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (Corpus.Row row : rows) { min = Math.min(min, row.value); max = Math.max(max, row.value); }
        Detectors.Detector detector = Detectors.create(detectorName, min, max);
        double[] values = new double[rows.size()], scores = new double[rows.size()];
        boolean[] labels = new boolean[rows.size()], alerts = new boolean[rows.size()];
        int alertCount = 0, outside = 0; boolean[] windowHits = new boolean[windows.size()];
        for (int i = 0; i < rows.size(); i++) {
            Corpus.Row row = rows.get(i); values[i] = row.value; scores[i] = detector.score(row.value);
            for (int w = 0; w < windows.size(); w++) if (windows.get(w).contains(row.time)) {
                labels[i] = true; if (i >= warmup && scores[i] >= threshold) windowHits[w] = true;
            }
            alerts[i] = i >= warmup && scores[i] >= threshold;
            if (alerts[i]) { alertCount++; if (!labels[i]) outside++; }
        }
        int hits = 0; for (boolean hit : windowHits) if (hit) hits++;
        return new PlotData(example, detectorName, rows, values, scores, labels, alerts,
            threshold, warmup, alertCount, outside, hits, windows.size());
    }

    static final class PlotData {
        final Corpus.Example example; final String detector;
        final List<Corpus.Row> rows; final double[] values, scores;
        final boolean[] labels, alerts; final double threshold; final int warmup, alertCount,
            outsideAlerts, hitWindows, totalWindows;
        PlotData(Corpus.Example example, String detector, List<Corpus.Row> rows, double[] values,
                 double[] scores, boolean[] labels, boolean[] alerts, double threshold, int warmup,
                 int alertCount, int outsideAlerts, int hitWindows, int totalWindows) {
            this.example=example; this.detector=detector; this.rows=rows; this.values=values;
            this.scores=scores; this.labels=labels; this.alerts=alerts; this.threshold=threshold;
            this.warmup=warmup; this.alertCount=alertCount; this.outsideAlerts=outsideAlerts;
            this.hitWindows=hitWindows; this.totalWindows=totalWindows;
        }
        String summary() { return "Rows: " + rows.size() + "   Alerts: " + alertCount +
            "   Known windows hit: " + hitWindows + "/" + totalWindows +
            "   Alerts outside known windows: " + outsideAlerts; }
    }

    static final class Chart extends JPanel {
        private PlotData data; private int hover = -1;
        private static final Color GOLD = new Color(255, 225, 140, 115);
        Chart() {
            setBackground(Color.WHITE); setToolTipText(""); setPreferredSize(new Dimension(960, 520));
            addMouseMotionListener(new MouseMotionAdapter() { public void mouseMoved(MouseEvent e) {
                hover = data == null ? -1 : indexAt(e.getX()); repaint();
            }});
        }
        void setData(PlotData data) { this.data = data; hover = -1; repaint(); }
        private int indexAt(int x) {
            int width = Math.max(1, getWidth() - 70);
            return Math.max(0, Math.min(data.rows.size()-1, (x - 55) * data.rows.size() / width));
        }
        public String getToolTipText(MouseEvent e) {
            if (data == null) return null; int i = indexAt(e.getX()); Corpus.Row row = data.rows.get(i);
            return "row " + i + " | " + row.time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) +
                " | value " + row.value + " | score " + new DecimalFormat("0.000").format(data.scores[i]) +
                " | labeled " + data.labels[i] + " | alert " + data.alerts[i];
        }
        protected void paintComponent(Graphics raw) {
            super.paintComponent(raw); Graphics2D g=(Graphics2D)raw.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int left=55, right=getWidth()-15, top=25, mid=getHeight()/2, bottom=getHeight()-35;
            g.setColor(new Color(235,238,242)); g.fillRect(left,top,right-left,mid-top-15);
            g.fillRect(left,mid+15,right-left,bottom-mid-15);
            if (data == null) { g.setColor(Color.DARK_GRAY); g.drawString("Run an example to draw it here.",left+20,top+30); g.dispose(); return; }
            shade(g,left,right,top,mid-15); shade(g,left,right,mid+15,bottom);
            drawSeries(g,data.values,left,right,top,mid-15,new Color(25,105,155),false);
            drawSeries(g,data.scores,left,right,mid+15,bottom,new Color(185,45,45),true);
            int thresholdY = bottom-(int)(data.threshold*(bottom-mid-15));
            g.setColor(new Color(150,50,50)); g.drawLine(left,thresholdY,right,thresholdY);
            int warmX=left+(int)((long)Math.min(data.warmup,data.rows.size())*(right-left)/data.rows.size());
            g.setColor(new Color(100,100,100,120)); g.fillRect(left,top,Math.max(0,warmX-left),bottom-top);
            g.setColor(Color.DARK_GRAY); g.drawString("measurement",8,top+14); g.drawString("score",20,mid+29);
            g.drawString("alert " + data.threshold,left+4,thresholdY-3); g.drawString("warmup",left+4,bottom+17);
            if (hover >= 0) { int x=left+(int)((long)hover*(right-left)/Math.max(1,data.rows.size()-1));
                g.setColor(new Color(30,30,30,150)); g.drawLine(x,top,x,bottom); }
            g.dispose();
        }
        private void shade(Graphics2D g,int left,int right,int top,int bottom) {
            g.setColor(GOLD); int n=data.labels.length, start=-1;
            for(int i=0;i<=n;i++){ boolean on=i<n&&data.labels[i]; if(on&&start<0)start=i;
                if(!on&&start>=0){int x1=left+(int)((long)start*(right-left)/n);
                    int x2=left+(int)((long)i*(right-left)/n);g.fillRect(x1,top,Math.max(1,x2-x1),bottom-top);start=-1;}}
        }
        private void drawSeries(Graphics2D g,double[] values,int left,int right,int top,int bottom,Color color,boolean score){
            double min=score?0:Double.POSITIVE_INFINITY,max=score?1:Double.NEGATIVE_INFINITY;
            if(!score)for(double v:values){min=Math.min(min,v);max=Math.max(max,v);} if(max==min)max=min+1;
            g.setColor(color); int lastX=left,lastY=bottom;
            for(int x=left;x<=right;x++){int from=Math.min(values.length-1,
                    (int)((long)(x-left)*values.length/Math.max(1,right-left)));
                int to=Math.max(from+1,(int)((long)(x-left+1)*values.length/Math.max(1,right-left)));
                to=Math.min(values.length,to);double sum=0;for(int i=from;i<to;i++)sum+=values[i];double v=sum/(to-from);
                int y=bottom-(int)((v-min)/(max-min)*(bottom-top)); if(x>left)g.drawLine(lastX,lastY,x,y);lastX=x;lastY=y;}
            if(score){g.setColor(new Color(220,30,30));for(int i=0;i<data.alerts.length;i++)if(data.alerts[i]){
                int x=left+(int)((long)i*(right-left)/data.alerts.length);g.fillOval(x-2,top-2,4,4);}}
        }
    }

    public static void main(String[] args) throws Exception {
        final CountDownLatch closed = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> { try {
                AnomalySwingVisualizer window = new AnomalySwingVisualizer();
                window.addWindowListener(new WindowAdapter() {
                    public void windowClosed(WindowEvent e) { closed.countDown(); }
                });
                window.setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, rootMessage(e), "Could not start", JOptionPane.ERROR_MESSAGE);
                closed.countDown();
            }});
        // exec-maven-plugin ends daemon threads when main returns. Waiting here
        // keeps the desktop app alive until the user closes its window.
        closed.await();
    }
}
