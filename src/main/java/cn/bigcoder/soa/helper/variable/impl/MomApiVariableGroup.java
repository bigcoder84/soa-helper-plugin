package cn.bigcoder.soa.helper.variable.impl;

import cn.bigcoder.soa.helper.settings.SoaHelperSettings;
import cn.bigcoder.soa.helper.variable.VariableGroup;
import cn.bigcoder.soa.helper.variable.VariableResolveException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MOM 契约平台变量组。
 * 通过 HTTP API 获取 momProjectId、momVersion、serviceCode。
 * 内置按 appId 的 TTL 缓存，避免重复请求。
 */
public class MomApiVariableGroup implements VariableGroup {

    private static final Set<String> VARIABLE_NAMES = Set.of(
            "momProjectId", "momVersion", "serviceCode"
    );

    /** 缓存：appId → CacheEntry */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public Set<String> getVariableNames() {
        return VARIABLE_NAMES;
    }

    @Override
    public boolean isAvailable() {
        SoaHelperSettings settings = SoaHelperSettings.getInstance();
        return settings.isExtendedFieldsEnabled()
                && settings.getMomBaseUrl() != null && !settings.getMomBaseUrl().isEmpty()
                && settings.getMomAccessToken() != null && !settings.getMomAccessToken().isEmpty();
    }

    @Override
    public boolean needsAsyncResolve(Map<String, String> context) {
        String appId = context.get("appId");
        if (appId == null || appId.isEmpty()) {
            return true; // 没有 appId，无法检查缓存，保守返回 true
        }
        CacheEntry cached = cache.get(appId);
        return cached == null || cached.isExpired();
    }

    @Override
    public Map<String, String> resolve(Map<String, String> context) throws VariableResolveException {
        String appId = context.get("appId");
        if (appId == null || appId.isEmpty()) {
            throw new VariableResolveException("缺少 appId，无法查询契约平台");
        }

        // 检查缓存
        CacheEntry cached = cache.get(appId);
        if (cached != null && !cached.isExpired()) {
            return cached.getVariables();
        }

        // 调用 API
        Map<String, String> result = fetchFromApi(appId);

        // 写入缓存
        int ttl = SoaHelperSettings.getInstance().getMomCacheTtl();
        cache.put(appId, new CacheEntry(result, ttl));

        return result;
    }

    /**
     * 调用 MOM 契约平台 API
     */
    private Map<String, String> fetchFromApi(String appId) throws VariableResolveException {
        SoaHelperSettings settings = SoaHelperSettings.getInstance();
        String baseUrl = settings.getMomBaseUrl();
        String token = settings.getMomAccessToken();
        int timeout = settings.getMomTimeout();

        // 去除 baseUrl 末尾的斜杠
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String urlStr = baseUrl + "/api/osg/project";

        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("access-token", token);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setDoOutput(true);

            // 写入请求体
            String requestBody = "{\"term\":\"" + appId + "\"}";
            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            // 检查响应状态码
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new VariableResolveException(
                        "契约平台返回错误，HTTP状态码：" + responseCode);
            }

            // 读取响应
            String responseBody;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                responseBody = sb.toString();
            }

            // 解析 JSON
            return parseResponse(responseBody, appId);

        } catch (VariableResolveException e) {
            throw e;
        } catch (java.net.SocketTimeoutException e) {
            throw new VariableResolveException("契约平台请求超时，请检查网络或增大超时时间", e);
        } catch (IOException e) {
            throw new VariableResolveException("无法连接契约平台：" + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 解析 API 响应 JSON，按 appId 精确匹配。
     * 响应格式：{"status": {"code": 0, "message": "OK"}, "body": [...]}
     * 也兼容直接返回 JSON 数组的情况。
     */
    private Map<String, String> parseResponse(String responseBody, String appId)
            throws VariableResolveException {
        try {
            JsonElement root = JsonParser.parseString(responseBody);

            // 解包：支持 {"status":..., "body":[...]} 和裸数组两种格式
            JsonArray array;
            if (root.isJsonObject()) {
                JsonObject rootObj = root.getAsJsonObject();
                // 检查业务状态码
                if (rootObj.has("status")) {
                    JsonObject status = rootObj.getAsJsonObject("status");
                    int code = status.has("code") ? status.get("code").getAsInt() : -1;
                    if (code != 0) {
                        String message = status.has("message") ? status.get("message").getAsString() : "未知错误";
                        throw new VariableResolveException("契约平台业务错误：" + message);
                    }
                }
                if (!rootObj.has("body") || !rootObj.get("body").isJsonArray()) {
                    throw new VariableResolveException("契约平台响应缺少 body 数组字段");
                }
                array = rootObj.getAsJsonArray("body");
            } else if (root.isJsonArray()) {
                array = root.getAsJsonArray();
            } else {
                throw new VariableResolveException("契约平台响应格式异常：既非对象也非数组");
            }

            JsonObject matched = null;
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                if (obj.has("appId") && appId.equals(obj.get("appId").getAsString())) {
                    matched = obj;
                    break;
                }
            }

            if (matched == null) {
                throw new VariableResolveException(
                        "契约平台返回 " + array.size() + " 条结果，但未找到 appId=" + appId + " 的精确匹配");
            }

            Map<String, String> result = new HashMap<>();
            result.put("momProjectId", String.valueOf(matched.get("id").getAsInt()));
            result.put("momVersion", String.valueOf(matched.get("version").getAsInt()));
            result.put("serviceCode", matched.get("serviceCode").getAsString());
            return result;

        } catch (JsonSyntaxException e) {
            throw new VariableResolveException("契约平台响应格式异常", e);
        } catch (VariableResolveException e) {
            throw e;
        } catch (Exception e) {
            throw new VariableResolveException("解析契约平台响应失败：" + e.getMessage(), e);
        }
    }

    /**
     * 缓存条目 —— 带过期时间
     */
    private static class CacheEntry {
        private final Map<String, String> variables;
        private final long expireTimeMillis;

        CacheEntry(Map<String, String> variables, int ttlSeconds) {
            this.variables = Collections.unmodifiableMap(new HashMap<>(variables));
            this.expireTimeMillis = System.currentTimeMillis() + ttlSeconds * 1000L;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTimeMillis;
        }

        Map<String, String> getVariables() {
            return variables;
        }
    }
}
