package com.enhanced.burpgpt.model;

public class AnalysisResult {
    private String time;
    private String url;
    private String response;
    private long responseTime;

    public AnalysisResult(String time, String url, String response, long responseTime) {
        this.time = time;
        this.url = url;
        this.response = response;
        this.responseTime = responseTime;
    }

    public String getResponse() {
        return response;
    }
}
