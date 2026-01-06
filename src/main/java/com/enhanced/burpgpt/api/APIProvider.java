package com.enhanced.burpgpt.api;

import okhttp3.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public abstract class APIProvider {
    protected String apiKey;
    protected String apiUrl;
    protected String model;
    protected OkHttpClient client;
    protected Gson gson;

    public APIProvider(String apiKey, String apiUrl, String model) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.gson = new Gson();
        this.client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    public abstract Request buildRequest(String prompt, int maxTokens);
    public abstract String parseResponse(String responseBody);

    public String sendRequest(String prompt, int maxTokens) throws IOException {
        Request request = buildRequest(prompt, maxTokens);
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            return parseResponse(response.body().string());
        }
    }
}
