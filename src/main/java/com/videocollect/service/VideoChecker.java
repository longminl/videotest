package com.videocollect.service;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 视频检测器：验证视频链接可播放性并测量延迟
 */
@Component
public class VideoChecker {

    private static final Logger log = LoggerFactory.getLogger(VideoChecker.class);

    private final OkHttpClient client;

    @Value("${video.check.connect-timeout}")
    private int connectTimeout;

    @Value("${video.check.read-timeout}")
    private int readTimeout;

    public VideoChecker() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout > 0 ? connectTimeout : 5000, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout > 0 ? readTimeout : 5000, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    /**
     * 检测结果
     */
    public static class CheckResult {
        /** 是否可播放 */
        private boolean playable;
        /** 延迟（毫秒） */
        private Integer latencyMs;
        /** 状态码 */
        private int statusCode;
        /** 错误信息 */
        private String errorMsg;

        public CheckResult(boolean playable, Integer latencyMs, int statusCode, String errorMsg) {
            this.playable = playable;
            this.latencyMs = latencyMs;
            this.statusCode = statusCode;
            this.errorMsg = errorMsg;
        }

        public boolean isPlayable() { return playable; }
        public Integer getLatencyMs() { return latencyMs; }
        public int getStatusCode() { return statusCode; }
        public String getErrorMsg() { return errorMsg; }
    }

    /**
     * 检测视频链接
     *
     * @param videoUrl 视频URL
     * @return 检测结果
     */
    public CheckResult check(String videoUrl) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            return new CheckResult(false, null, 0, "视频地址为空");
        }

        log.info("开始检测视频链接: {}", videoUrl);

        long startTime = System.currentTimeMillis();

        try {
            Request request = new Request.Builder()
                    .url(videoUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .head()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                long elapsed = System.currentTimeMillis() - startTime;
                int code = response.code();

                // 获取Content-Type
                String contentType = response.header("Content-Type");
                log.info("检测完成: {} → HTTP {} ({}) Content-Type={}",
                        videoUrl, code, elapsed + "ms", contentType);

                // 判断是否可播放
                boolean playable = false;
                if (code == 200 || code == 206) {
                    // 如果有Content-Type且包含video/media，认为是视频
                    if (contentType != null && (contentType.startsWith("video/")
                            || contentType.startsWith("media/"))) {
                        playable = true;
                    } else if (isVideoExtension(videoUrl)) {
                        // 即使Content-Type不明确，URL以视频扩展名结尾也可播放
                        playable = true;
                    } else {
                        // Content-Type不明确，尝试GET请求读前几个字节判断
                        playable = false;
                    }
                }

                return new CheckResult(playable, (int) elapsed, code,
                        playable ? null : "HTTP " + code + " 或 Content-Type 非视频类型");
            }
        } catch (java.net.SocketTimeoutException e) {
            log.warn("检测超时: {}", videoUrl);
            return new CheckResult(false, null, 0, "连接超时");
        } catch (Exception e) {
            log.error("检测失败: {}, 原因: {}", videoUrl, e.getMessage());
            return new CheckResult(false, null, 0, "检测异常: " + e.getMessage());
        }
    }

    /**
     * 判断URL是否以视频扩展名结尾
     */
    private boolean isVideoExtension(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".webm")
                || lower.contains(".m3u8") || lower.endsWith(".ts")
                || lower.endsWith(".flv") || lower.endsWith(".mov")
                || lower.endsWith(".avi") || lower.endsWith(".mkv");
    }
}
