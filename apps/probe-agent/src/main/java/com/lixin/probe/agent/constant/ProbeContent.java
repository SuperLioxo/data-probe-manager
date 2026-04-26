package com.lixin.probe.agent.constant;


import java.util.*;

/**
 * 探查内容枚举
 * 用于位运算组合多个探查内容
 */
public enum ProbeContent {
    METADATA("metadata", "元数据", 1, Set.of(1)),
    DATA_SIZE("dataSize", "数据量", 2, Set.of(2)),
    DATA_CONTENT("exampleData", "示例数据", 4, Set.of(4)),
    DATA_FILE("dataFile", "数据文件", 8, Set.of(8)),
    DATA_FILE_DATA("fileTransferPayload", "数据文件", 16, Set.of(16)),

    THREE("metadata & dataSize", "元数据 & 数据量", 3, Set.of(3, 1, 2)),
    FIVE("metadata & dataSize & exampleData", "元数据 & 示例数据", 5, Set.of(5, 1, 4)),
    SIX("dataSize & exampleData", "数据量 & 示例数据", 6, Set.of(6, 2, 4)),
    SEVEN("metadata & dataSize & exampleData", "元数据 & 数据量 & 示例数据", 7, Set.of(7, 1, 2, 4)),
    FIFTEEN("metadata & dataSize & exampleData & dataFile", "元数据 & 数据量 & 示例数据 & 数据文件", 15, Set.of(9, 1, 2, 4, 8));

    private final String content;
    private final String description;
    private final Integer value;
    private final Set<Integer> set;

    // Explicit constructor for enum constants
    ProbeContent(String content, String description, Integer value, Set<Integer> set) {
        this.content = content;
        this.description = description;
        this.value = value;
        this.set = set;
    }

    /**
     * 判断当前枚举值是否等于指定值
     *
     * @param value 指定值
     * @return 结果
     */
    public boolean equals(Integer value) {
        return Objects.equals(this.value, value);
    }

    /**
     * 判断当前枚举值是否包含指定值
     *
     * @param value 值
     * @return 结果
     */
    public boolean contains(Integer value) {
        return this.set.contains(value);
    }

    /**
     * 获取枚举值
     *
     * @return 值
     */
    public Integer getValue() {
        return value;
    }

    /**
     * 获取指定值对应的枚举
     *
     * @param value 值
     * @return 枚举
     */
    public static ProbeContent get(Integer value) {
        return Arrays.stream(values())
                .filter(probeContent -> probeContent.equals(value))
                .findFirst()
                .orElse(null);
    }

    /**
     * 判断源枚举是否包含目标枚举
     *
     * @param source 源枚举值
     * @param target 目标枚举值
     * @return 结果
     */
    public static boolean contains(Integer source, Integer target) {
        return Optional.ofNullable(get(source)).map(e -> e.contains(target)).orElse(false);
    }
}
