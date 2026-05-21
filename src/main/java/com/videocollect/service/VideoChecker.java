package com.videocollect.service;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
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

    /** 用于内容嗅探的短超时客户端 */
    private final OkHttpClient sniffClient = new OkHttpClient.Builder()
            .connectTimeout(3000, TimeUnit.MILLISECONDS)
            .readTimeout(3000, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

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
                        // Content-Type 非视频类型 → 嗅探内容是否为 m3u8
                        playable = sniffIsM3u8(videoUrl);
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

    /**
     * 发起短 GET 请求，嗅探内容是否为 m3u8 格式（以 #EXTM3U 开头）
     */
    private boolean sniffIsM3u8(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build();
            try (Response response = sniffClient.newCall(request).execute()) {
                if (response.code() != 200) return false;
                ResponseBody body = response.body();
                if (body == null) return false;
                byte[] header = new byte[20];
                int read = body.byteStream().read(header);
                if (read <= 0) return false;
                String prefix = new String(header, 0, read, "UTF-8").trim();
                boolean isM3u8 = prefix.startsWith("#EXTM3U");
                log.info("内容嗅探: {} → {}", isM3u8 ? "是 m3u8" : "非 m3u8", url);
                return isM3u8;
            }
        } catch (Exception e) {
            log.warn("内容嗅探失败: {}", url, e);
            return false;
        }
    }
}
