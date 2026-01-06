package com.enhanced.burpgpt.mcp;

import java.io.*;
import java.util.function.Consumer;

public class StdioTransport implements Transport {
    private final String command;
    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean isRunning = false;
    private Thread listenerThread;
    private Consumer<String> listener;

    public StdioTransport(String command) {
        this.command = command;
    }

    @Override
    public void start() throws IOException {
        if (isRunning) return;

        ProcessBuilder pb = new ProcessBuilder();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            pb.command("cmd.exe", "/c", command);
        } else {
            pb.command("sh", "-c", command);
        }
        
        this.process = pb.start();
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        this.isRunning = true;

        this.listenerThread = new Thread(this::listen);
        this.listenerThread.setDaemon(true);
        this.listenerThread.start();
    }

    @Override
    public void stop() {
        isRunning = false;
        if (process != null) {
            process.destroy();
        }
    }

    @Override
    public void send(String message) throws IOException {
        if (!isRunning) throw new IOException("Transport is not running");
        writer.write(message);
        writer.newLine();
        writer.flush();
    }

    @Override
    public void setListener(Consumer<String> listener) {
        this.listener = listener;
    }

    private void listen() {
        try {
            String line;
            while (isRunning && (line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                if (listener != null) {
                    listener.accept(line);
                }
            }
        } catch (IOException e) {
            if (isRunning) {
                System.err.println("StdioTransport disconnected: " + e.getMessage());
            }
        }
    }
}
