package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 探针分组关联实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("probe_group_relation")
public class ProbeGroupRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 探针ID
     */
    private Long probeId;

    /**
     * 分组ID
     */
    private Long groupId;

    private LocalDateTime createTime;
}
