package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("http_api_source")
public class HttpApiSource implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String probeKey;
    private String agentCode;
    private String url;
    /** GET, POST */
    private String method;
    /** JSON headers */
    private String headers;
    /** NONE, BASIC, BEARER, API_KEY */
    private String authType;
    /** JSON auth config */
    private String authConfig;
    /** JSONPath to data array */
    private String responsePath;
    /** NONE, OFFSET, CURSOR */
    private String paginationType;
    /** JSON pagination config */
    private String paginationConfig;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
