package com.videocollect.dto;

/**
 * 批量检测进度DTO，用于SSE推送进度
 */
public class CheckProgress {

    private int total;
    private int completed;
    private int successCount;
    private int failCount;
    private String currentUrl;
    private boolean finished;

    public CheckProgress() {}

    public CheckProgress(int total) {
        this.total = total;
        this.completed = 0;
        this.successCount = 0;
        this.failCount = 0;
        this.finished = false;
    }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getCompleted() { return completed; }
    public void setCompleted(int completed) { this.completed = completed; }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public int getFailCount() { return failCount; }
    public void setFailCount(int failCount) { this.failCount = failCount; }

    public String getCurrentUrl() { return currentUrl; }
    public void setCurrentUrl(String currentUrl) { this.currentUrl = currentUrl; }

    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }

    public int getPercent() {
        if (total == 0) return 100;
        return completed * 100 / total;
    }
}
