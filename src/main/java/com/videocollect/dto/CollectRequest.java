package com.videocollect.dto;

/**
 * 提交收藏请求DTO
 */
public class CollectRequest {

    /** 网页URL 或 视频直链 */
    private String url;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
