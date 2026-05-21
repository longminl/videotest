package com.videocollect.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * yt-dlp 解析器：调用 yt-dlp 命令行工具解析视频真实地址
 * 支持 B站、YouTube、抖音等 1000+ 网站
 */
@Component
public class VideoDlpResolver {

    private static final Logger log = LoggerFactory.getLogger(VideoDlpResolver.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** yt-dlp 可执行文件路径（默认同目录下的 yt-dlp.exe） */
    @Value("${video.ytdlp.path:yt-dlp}")
    private String ytDlpPath;

    /** yt-dlp 命令超时（秒） */
    @Value("${video.ytdlp.timeout:30}")
    private int timeout;

    /**
     * 解析结果
     */
    public static class DlResult {
        private boolean success;
        private String title;
        private String videoUrl;
        private String pageUrl;
        private List<String> allFormats;
        private String errorMsg;

        public boolean isSuccess() { return success; }
        public String getTitle() { return title; }
        public String getVideoUrl() { return videoUrl; }
        public String getPageUrl() { return pageUrl; }
        public List<String> getAllFormats() { return allFormats; }
        public String getErrorMsg() { return errorMsg; }

        public static DlResult ok(String title, String videoUrl, String pageUrl, List<String> allFormats) {
            DlResult r = new DlResult();
            r.success = true;
            r.title = title;
            r.videoUrl = videoUrl;
            r.pageUrl = pageUrl;
            r.allFormats = allFormats;
            return r;
        }

        public static DlResult fail(String errorMsg) {
            DlResult r = new DlResult();
            r.success = false;
            r.errorMsg = errorMsg;
            return r;
        }
    }

    /**
     * 解析视频链接
     *
     * @param url 网页URL（如 B站视频页）
     * @return 解析结果
     */
    public DlResult resolve(String url) {
        log.info("yt-dlp 开始解析: {}", url);

        try {
            // 构建命令：yt-dlp --dump-json --no-warnings --no-download <url>
            List<String> cmd = new ArrayList<>();
            cmd.add(ytDlpPath);
            cmd.add("--dump-json");
            cmd.add("--no-warnings");
            cmd.add("--no-download");
            cmd.add("--format");
            cmd.add("best[ext=mp4]/best");  // 优先 MP4
            cmd.add(url);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);

            Process process = pb.start();
            process.waitFor();

            // 读取标准输出（JSON）
            StringBuilder stdout = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line);
                }
            }

            // 读取错误输出
            StringBuilder stderr = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append(line);
                }
            }

            int exitCode = process.exitValue();

            if (exitCode != 0 || stdout.length() == 0) {
                String err = stderr.length() > 0 ? stderr.toString() : "退出码: " + exitCode;
                log.warn("yt-dlp 解析失败: {}, stderr: {}", url, err);
                return DlResult.fail("yt-dlp 解析失败: " + err);
            }

            // 解析 JSON
            JsonNode json = objectMapper.readTree(stdout.toString());
            String title = json.has("title") ? json.get("title").asText() : "未命名";
            String pageUrl = json.has("webpage_url") ? json.get("webpage_url").asText() : url;

            // 获取最佳格式的视频URL
            String videoUrl = null;
            List<String> allFormats = new ArrayList<>();

            if (json.has("url")) {
                // 单格式视频
                videoUrl = json.get("url").asText();
                allFormats.add(videoUrl);
            } else if (json.has("formats") && json.get("formats").isArray()) {
                // 多格式，取第一个有 url 的
                for (JsonNode fmt : json.get("formats")) {
                    if (fmt.has("url")) {
                        String fmtUrl = fmt.get("url").asText();
                        allFormats.add(fmtUrl);
                        if (videoUrl == null) {
                            videoUrl = fmtUrl;
                        }
                    }
                }
            }

            if (videoUrl == null) {
                log.warn("yt-dlp 解析完成但未找到视频地址: {}", url);
                return DlResult.fail("yt-dlp 解析完成但未找到视频地址");
            }

            log.info("yt-dlp 解析成功: title={}, url={}", title, videoUrl);
            return DlResult.ok(title, videoUrl, pageUrl, allFormats);

        } catch (java.io.IOException e) {
            // 通常是因为 yt-dlp.exe 不存在
            String msg = e.getMessage();
            if (msg != null && msg.contains("CreateProcess") || msg != null && msg.contains("error=2")) {
                log.warn("yt-dlp 未找到，请下载 yt-dlp.exe 放到项目目录或配置 path: {}", e.getMessage());
                return DlResult.fail("yt-dlp 未安装，请下载 yt-dlp.exe");
            }
            log.error("yt-dlp 调用异常: {}", e.getMessage());
            return DlResult.fail("yt-dlp 调用异常: " + e.getMessage());
        } catch (Exception e) {
            log.error("yt-dlp 解析异常: {}", e.getMessage());
            return DlResult.fail("yt-dlp 解析异常: " + e.getMessage());
        }
    }

    /**
     * 检查 yt-dlp 是否可用
     */
    public boolean isAvailable() {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(ytDlpPath);
            cmd.add("--version");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process process = pb.start();
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
