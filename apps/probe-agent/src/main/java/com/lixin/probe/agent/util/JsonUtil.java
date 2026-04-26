package com.lixin.probe.agent.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON 工具类
 * 基于 FastJSON2 实现 JSON 序列化和反序列化
 */
public class JsonUtil {

    private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);
    /**
     * 将对象转换为 JSON 字符串
     *
     * @param obj 对象
     * @return JSON 字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return JSON.toJSONString(obj);
        } catch (Exception e) {
            log.error("对象序列化为 JSON 失败: {}", obj.getClass().getName(), e);
            return null;
        }
    }

    /**
     * 将对象转换为格式化的 JSON 字符串
     *
     * @param obj 对象
     * @return 格式化的 JSON 字符串
     */
    public static String toPrettyJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return JSON.toJSONString(obj, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat);
        } catch (Exception e) {
            log.error("对象序列化为格式化 JSON 失败: {}", obj.getClass().getName(), e);
            return null;
        }
    }

    /**
     * 将 JSON 字符串解析为对象
     *
     * @param json JSON 字符串
     * @param clazz 目标类型
     * @return 对象实例
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return JSON.parseObject(json, clazz);
        } catch (JSONException e) {
            log.error("JSON 反序列化失败，目标类型: {}, JSON: {}", clazz.getName(), json, e);
            return null;
        }
    }

    /**
     * 将 JSON 字符串解析为对象（支持泛型）
     *
     * @param json       JSON 字符串
     * @param typeReference 类型引用（例如：new TypeReference<List<T>>(){}）
     * @return 对象实例
     */
    public static <T> T fromJson(String json, com.alibaba.fastjson2.TypeReference<T> typeReference) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return JSON.parseObject(json, typeReference);
        } catch (JSONException e) {
            log.error("JSON 反序列化失败，JSON: {}", json, e);
            return null;
        }
    }

    /**
     * 验证 JSON 字符串是否有效
     *
     * @param json JSON 字符串
     * @return 是否有效
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.isEmpty()) {
            return false;
        }
        try {
            JSON.parse(json);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    /**
     * 格式化 JSON 字符串
     *
     * @param json JSON 字符串
     * @return 格式化后的 JSON 字符串
     */
    public static String formatJson(String json) {
        if (!isValidJson(json)) {
            return json;
        }
        try {
            Object obj = JSON.parse(json);
            return JSON.toJSONString(obj, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat);
        } catch (Exception e) {
            log.error("格式化 JSON 失败", e);
            return json;
        }
    }

    /**
     * 压缩 JSON 字符串（移除空格和换行）
     *
     * @param json JSON 字符串
     * @return 压缩后的 JSON 字符串
     */
    public static String compressJson(String json) {
        if (!isValidJson(json)) {
            return json;
        }
        try {
            Object obj = JSON.parse(json);
            return JSON.toJSONString(obj);
        } catch (Exception e) {
            log.error("压缩 JSON 失败", e);
            return json;
        }
    }
}
