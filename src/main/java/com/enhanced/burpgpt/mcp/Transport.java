package com.enhanced.burpgpt.mcp;

import java.io.IOException;
import java.util.function.Consumer;

public interface Transport {
    void start() throws IOException;
    void stop();
    void send(String message) throws IOException;
    void setListener(Consumer<String> listener);
}
