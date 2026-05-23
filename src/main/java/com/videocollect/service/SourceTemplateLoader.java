package com.videocollect.service;

import com.videocollect.dao.VideoSourceDao;
import com.videocollect.model.VideoSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 预设视频源模板加载器
 * 启动时从 application.yml 读取预设模板，将不存在的模板插入数据库
 */
@Component
@ConfigurationProperties(prefix = "video")
public class SourceTemplateLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SourceTemplateLoader.class);

    private List<Map<String, Object>> sourceTemplates;

    @Autowired
    private VideoSourceDao videoSourceDao;

    @Override
    public void run(String... args) {
        if (sourceTemplates == null || sourceTemplates.isEmpty()) {
            log.info("未配置视频源预设模板");
            return;
        }

        int loaded = 0;
        for (Map<String, Object> tmpl : sourceTemplates) {
            String name = (String) tmpl.get("name");
            if (name == null || name.isEmpty()) continue;

            // 检查是否已存在同名模板
            List<VideoSource> existing = videoSourceDao.findAll();
            boolean exists = existing.stream().anyMatch(s -> name.equals(s.getName()));
            if (exists) continue;

            VideoSource source = new VideoSource();
            source.setName(name);
            source.setHomeUrl((String) tmpl.get("homeUrl"));
            source.setSearchUrl((String) tmpl.get("searchUrl"));
            source.setSearchDataPath((String) tmpl.getOrDefault("searchDataPath", "$"));
            source.setSearchTitleField((String) tmpl.getOrDefault("titleField", "title"));
            source.setSearchUrlField((String) tmpl.getOrDefault("urlField", "url"));
            source.setSearchCoverField((String) tmpl.get("coverField"));
            source.setEpisodePattern((String) tmpl.get("episodePattern"));
            Object group = tmpl.get("episodeGroup");
            source.setEpisodeGroup(group != null ? Integer.valueOf(group.toString()) : 1);
            source.setEncoding("UTF-8");
            source.setSortOrder(0);

            videoSourceDao.insert(source);
            loaded++;
            log.info("已加载视频源预设模板: {}", name);
        }

        if (loaded > 0) {
            log.info("视频源预设模板加载完成，共 {} 个", loaded);
        }
    }

    public List<Map<String, Object>> getSourceTemplates() {
        return sourceTemplates;
    }

    public void setSourceTemplates(List<Map<String, Object>> sourceTemplates) {
        this.sourceTemplates = sourceTemplates;
    }
}
