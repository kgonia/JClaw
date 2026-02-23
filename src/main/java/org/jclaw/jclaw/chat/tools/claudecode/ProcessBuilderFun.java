package org.jclaw.jclaw.chat.tools.claudecode;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ProcessBuilderFun {

    private static final JsonFactory jfactory = new JsonFactory();
    private static final ObjectMapper mapper = new ObjectMapper(jfactory);

    static void main() throws IOException, InterruptedException {

        ProcessBuilder processBuilder = new ProcessBuilder("java", "-version");
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        List<String> results = readOutput(process.getInputStream());
        results.forEach(IO::println);

        int exitCode = process.waitFor();
        IO.println("Exit code: " + exitCode);

        // -p is headless/print mode - should NOT require TTY
        processBuilder = new ProcessBuilder("claude", "--verbose", "-p", "--output-format", "stream-json",
                                           "read files in current project");
        // Do NOT merge stderr; read separately

        Process claudeProcess = processBuilder.start();
        // Crucial: close stdin + read stdout/stderr concurrently to prevent blocking/deadlock
        claudeProcess.getOutputStream().close(); // close stdin to avoid blocking

        Thread outReader = startParser("OUT", claudeProcess.getInputStream());
        Thread errReader = startReader("ERR", claudeProcess.getErrorStream());

        boolean finished = claudeProcess.waitFor(300, TimeUnit.SECONDS);
        if (!finished) {
            IO.println("Timeout waiting for claude; destroying process");
            claudeProcess.destroyForcibly();
        }

        outReader.join();
        errReader.join();
        IO.println("Exit code: " + claudeProcess.exitValue());
    }


    private static Thread startParser(String tag, InputStream inputStream){
        Thread t = Thread.ofVirtual().unstarted(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseLine(tag, line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        t.start();
        return t;
    }

    private static void parseLine(String tag, String line) throws IOException {
        JsonNode root = mapper.readTree(line);
        if (root == null) return;

        if (root.isArray()) {
            for (JsonNode node : root) {
                handleNode(tag, node);
            }
        } else if (root.isObject()) {
            handleNode(tag, root);
        }
    }

    private static void handleNode(String tag, JsonNode node) {
        String type = text(node, "type");
        String subtype = text(node, "subtype");
        String result = text(node, "result");
        String message = extractMessage(node.get("message"));
        String sessionId = text(node, "session_id");

        // Full object is still available as "node"
        // e.g. store it, or log it:
        // IO.println(tag + " raw=" + node.toString());

        StringBuilder sb = new StringBuilder(tag);
        if (type != null) sb.append(" type=").append(type);
        if (subtype != null) sb.append(" subtype=").append(subtype);
        if (result != null) sb.append(" result=").append(result);
        if (message != null) sb.append(" message=").append(message);
        if (sessionId != null) sb.append(" session_id=").append(sessionId);
        IO.println(sb.toString());
    }

    private static String extractMessage(JsonNode messageNode) {
        if (messageNode == null || messageNode.isNull()) return null;
        if (messageNode.isTextual()) return messageNode.asText();

        JsonNode content = messageNode.get("content");
        if (content == null || !content.isArray()) return null;

        StringBuilder out = new StringBuilder();
        for (JsonNode item : content) {
            String itemType = text(item, "type");
            String text = text(item, "text");
            String thinking = text(item, "thinking");

            appendLine(out, itemType != null ? itemType : "text", text);
            appendLine(out, itemType != null ? itemType + ".thinking" : "thinking", thinking);
        }
        return out.length() == 0 ? null : out.toString();
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return (v != null && v.isTextual()) ? v.asText() : null;
    }

    private static void appendLine(StringBuilder out, String label, String value) {
        if (value == null) return;
        if (out.length() > 0) out.append('\n');
        if (label != null) out.append(label).append(": ");
        out.append(value);
    }

    private static Thread startReader(String tag, InputStream inputStream) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    IO.println(tag + ": " + line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        t.start();
        return t;
    }

    private static List<String> readOutput(InputStream inputStream) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}
