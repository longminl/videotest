package com.videocollect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 视频收藏工具 - 启动类
 */
@SpringBootApplication
public class VideoCollectApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideoCollectApplication.class, args);
        System.out.println("======================================================");
        System.out.println("  视频收藏工具已启动");
        System.out.println("  访问地址: http://localhost:8080");
        System.out.println("======================================================");
    }
}
