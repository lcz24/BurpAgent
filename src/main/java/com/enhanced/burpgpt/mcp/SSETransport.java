package com.enhanced.burpgpt.mcp;

import okhttp3.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SSETransport implements Transport {
    private final String sseUrl;
    private final OkHttpClient client;
    private volatile String postUrl;
    private volatile boolean isRunning = false;
    private Consumer<String> listener;
    private Thread listenerThread;
    private Call sseCall;

    public SSETransport(String url) {
        this.sseUrl = url;
        this.client = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // Infinite read timeout for SSE
            .connectTimeout(10, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public void start() throws IOException {
        if (isRunning) return;
        isRunning = true;

        Request request = new Request.Builder()
            .url(sseUrl)
            .header("Accept", "text/event-stream")
            .build();

        sseCall = client.newCall(request);
        
        // Start listener thread to read the stream
        listenerThread = new Thread(() -> {
            try (Response response = sseCall.execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("SSE Connection failed: " + response.code());
                    isRunning = false;
                    return;
                }
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                    String line;
                    while (isRunning && (line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) continue;
                        
                        // Parse SSE
                        if (line.startsWith("event: endpoint")) {
                            // Read next line for data
                            String dataLine = reader.readLine();
                            if (dataLine != null && dataLine.trim().startsWith("data: ")) {
                                String uri = dataLine.trim().substring(6).trim();
                                // Handle relative or absolute URI
                                if (uri.startsWith("http")) {
                                    postUrl = uri;
                                } else {
                                    // Resolve relative to sseUrl
                                    HttpUrl base = HttpUrl.parse(sseUrl);
                                    if (base != null) {
                                        postUrl = base.resolve(uri).toString();
                                    }
                                }
                                System.out.println("MCP SSE Endpoint discovered: " + postUrl);
                            }
                        } else if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if (listener != null) {
                                listener.accept(data);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("SSE Transport Error: " + e.getMessage());
                }
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    @Override
    public void stop() {
        isRunning = false;
        if (sseCall != null) {
            sseCall.cancel();
        }
    }

    @Override
    public void send(String message) throws IOException {
        // Simple wait for postUrl discovery
        int retries = 0;
        while (postUrl == null && retries < 20) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for endpoint discovery");
            }
            retries++;
        }

        if (postUrl == null) {
            throw new IOException("MCP Post Endpoint not yet discovered via SSE.");
        }

        Request request = new Request.Builder()
            .url(postUrl)
            .post(RequestBody.create(message, MediaType.parse("application/json")))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to send message: " + response.code());
            }
        }
    }

    @Override
    public void setListener(Consumer<String> listener) {
        this.listener = listener;
    }
}
