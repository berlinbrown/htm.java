package org.numenta.workbench;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Reads the exact bundled NAB records. Labels are evaluation-only, never inputs. */
public final class Corpus {
    private Corpus() {}
    public static final class Example {
        public final String id, path, description;
        Example(String[] fields) { id = fields[0]; path = fields[1]; description = fields[2]; }
        public String toString() { return id; }
    }
    public static final class Row {
        public final LocalDateTime time;
        public final double value;
        Row(LocalDateTime time, double value) { this.time = time; this.value = value; }
    }
    public static final class Window {
        public final LocalDateTime start, end;
        Window(String start, String end) {
            this.start = timestamp(start); this.end = timestamp(end);
            if (this.end.isBefore(this.start)) throw new IllegalArgumentException("Reversed label window");
        }
        public boolean contains(LocalDateTime time) {
            return !time.isBefore(start) && !time.isAfter(end);
        }
    }
    static LocalDateTime timestamp(String text) {
        return LocalDateTime.parse(text.trim().replace(' ', 'T'));
    }
    static InputStream resource(String path) throws IOException {
        InputStream stream = Corpus.class.getResourceAsStream("/nab/" + path);
        if (stream == null) throw new FileNotFoundException("Bundled NAB resource missing: " + path);
        return stream;
    }
    public static List<Example> examples() throws IOException {
        List<Example> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource("examples.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty() && !line.startsWith("#")) result.add(new Example(line.split("\t", 3)));
            }
        }
        return result;
    }
    public static List<Row> rows(Example example) throws IOException {
        List<Row> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource("data/" + example.path), StandardCharsets.UTF_8))) {
            if (!"timestamp,value".equals(reader.readLine())) throw new IOException("Unexpected CSV header: " + example.path);
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    String[] fields = line.split(",", -1);
                    if (fields.length != 2) throw new IllegalArgumentException("Expected two fields");
                    Row row = new Row(timestamp(fields[0]), Double.parseDouble(fields[1]));
                    if (!Double.isFinite(row.value)) throw new IllegalArgumentException("Non-finite value");
                    // NAB contains duplicate/out-of-order timestamps. Replay
                    // file order exactly, as pandas iterrows does upstream.
                    result.add(row);
                } catch (RuntimeException e) {
                    throw new IOException(example.path + " row " + (result.size()+2) + ": " + line, e);
                }
            }
        }
        if (result.isEmpty()) throw new IOException("Empty series: " + example.path);
        return result;
    }
    public static List<Window> windows(Example example) throws IOException {
        JsonNode root;
        try (InputStream stream = resource("combined_windows.json")) {
            root = new ObjectMapper().readTree(stream);
        }
        JsonNode node = root.get(example.path);
        if (node == null || !node.isArray()) throw new IOException("Missing labels: " + example.path);
        List<Window> result = new ArrayList<>();
        for (JsonNode pair : node) result.add(new Window(pair.get(0).asText(), pair.get(1).asText()));
        return result;
    }
}
