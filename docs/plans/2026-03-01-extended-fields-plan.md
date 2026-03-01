# 扩展字段支持实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 SOA Helper 插件的 URL 模板跳转功能新增 `${projectId}`、`${momVersion}`、`${serviceCode}` 扩展变量，数据通过 MOM 契约平台 API 按需懒加载。

**Architecture:** 引入 VariableGroup 分组变量接口 + TemplateResolver 编排层，实现"模板驱动懒加载"——只有模板实际使用扩展变量时才触发 API 调用，同一 Group 的变量只调用一次。通过 TTL 缓存避免重复请求，通过 IntelliJ ProgressManager 实现非阻塞异步加载。

**Tech Stack:** Java 17, IntelliJ Platform SDK 2023.2, java.net.HttpURLConnection, Gson (IntelliJ 内置)

---

### Task 1: VariableGroup 接口与异常类

**Files:**
- Create: `src/main/java/cn/bigcoder/soa/helper/variable/VariableGroup.java`
- Create: `src/main/java/cn/bigcoder/soa/helper/variable/VariableResolveException.java`

**Step 1: 创建 VariableResolveException**

```java
package cn.bigcoder.soa.helper.variable;

/**
 * 变量解析异常 —— 用于区分网络错误、超时、配置缺失等情况
 */
public class VariableResolveException extends Exception {
    public VariableResolveException(String message) {
        super(message);
    }

    public VariableResolveException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**Step 2: 创建 VariableGroup 接口**

```java
package cn.bigcoder.soa.helper.variable;

import java.util.Map;
import java.util.Set;

/**
 * 变量组接口 —— 一组由同一数据源提供的模板变量。
 * 一个 Group 对应一个数据源（一次 API 调用 / 一次本地解析），
 * Group 内的变量要么整组加载，要么整组不加载。
 */
public interface VariableGroup {

    /**
     * 该组提供的所有变量名（如 {"projectId", "momVersion", "serviceCode"}）
     */
    Set<String> getVariableNames();

    /**
     * 一次性解析该组所有变量。
     *
     * @param context 上下文变量（已解析的变量，如 appId），供本组解析时参考
     * @return 变量名 → 变量值 的映射
     * @throws VariableResolveException 解析失败时抛出
     */
    Map<String, String> resolve(Map<String, String> context) throws VariableResolveException;

    /**
     * 该组是否可用（如：扩展字段开关是否开启、配置是否完整）
     */
    boolean isAvailable();

    /**
     * 检查该组在给定上下文下是否需要异步加载（即缓存未命中）。
     * 返回 true 表示 resolve() 可能涉及网络IO，调用方应在后台线程执行。
     * 返回 false 表示 resolve() 可以同步快速返回。
     */
    boolean needsAsyncResolve(Map<String, String> context);
}
```

**Step 3: 编译验证**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/cn/bigcoder/soa/helper/variable/
git commit -m "feat: 添加 VariableGroup 接口与 VariableResolveException"
```

---

### Task 2: VariableGroupRegistry

**Files:**
- Create: `src/main/java/cn/bigcoder/soa/helper/variable/VariableGroupRegistry.java`

**Step 1: 创建 VariableGroupRegistry**

```java
package cn.bigcoder.soa.helper.variable;

import java.util.*;

/**
 * 变量组注册表 —— 管理所有 VariableGroup，
 * 提供"根据变量名找到所属 Group"的能力。
 */
public class VariableGroupRegistry {

    private final List<VariableGroup> groups = new ArrayList<>();

    public void register(VariableGroup group) {
        groups.add(group);
    }

    /**
     * 给定一组未解析的变量名，返回需要调用的 VariableGroup 集合（去重）。
     * 如果某个变量名不属于任何已注册的 Group，则忽略它。
     */
    public Set<VariableGroup> findGroupsForVariables(Set<String> unresolvedVarNames) {
        Set<VariableGroup> result = new LinkedHashSet<>();
        for (String varName : unresolvedVarNames) {
            for (VariableGroup group : groups) {
                if (group.getVariableNames().contains(varName)) {
                    result.add(group);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 获取所有已注册的 Group
     */
    public List<VariableGroup> getAllGroups() {
        return Collections.unmodifiableList(groups);
    }
}
```

**Step 2: 编译验证**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/cn/bigcoder/soa/helper/variable/VariableGroupRegistry.java
git commit -m "feat: 添加 VariableGroupRegistry 变量组注册表"
```

---

### Task 3: BasicVariableGroup

**Files:**
- Create: `src/main/java/cn/bigcoder/soa/helper/variable/impl/BasicVariableGroup.java`

**Step 1: 创建 BasicVariableGroup**

```java
package cn.bigcoder.soa.helper.variable.impl;

import cn.bigcoder.soa.helper.variable.VariableGroup;
import cn.bigcoder.soa.helper.variable.VariableResolveException;

import java.util.*;

/**
 * 基础变量组 —— 提供 appId 和 methodName。
 * 变量在构造时传入，resolve() 直接返回，无IO操作。
 */
public class BasicVariableGroup implements VariableGroup {

    private static final Set<String> VARIABLE_NAMES = Set.of("appId", "methodName");

    private final Map<String, String> variables;

    public BasicVariableGroup(String appId, String methodName) {
        this.variables = new HashMap<>();
        this.variables.put("appId", appId);
        this.variables.put("methodName", methodName);
    }

    @Override
    public Set<String> getVariableNames() {
        return VARIABLE_NAMES;
    }

    @Override
    public Map<String, String> resolve(Map<String, String> context) throws VariableResolveException {
        return new HashMap<>(variables);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean needsAsyncResolve(Map<String, String> context) {
        return false;
    }
}
```

**Step 2: 编译验证**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/cn/bigcoder/soa/helper/variable/impl/
git commit -m "feat: 添加 BasicVariableGroup 基础变量组"
```

---

### Task 4: TemplateResolver 编排层

**Files:**
- Create: `src/main/java/cn/bigcoder/soa/helper/variable/TemplateResolver.java`

**Step 1: 创建 TemplateResolver**

```java
package cn.bigcoder.soa.helper.variable;

import cn.bigcoder.soa.helper.util.TemplateParser;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板解析编排器 —— 实现分组懒加载。
 *
 * 流程：
 * 1. 用基础变量做第一轮渲染
 * 2. 扫描渲染结果中剩余的 ${...} 占位符
 * 3. 通过 Registry 找到这些占位符所属的 VariableGroup（去重）
 * 4. 只调用需要的 Group 的 resolve()
 * 5. 合并新变量，做第二轮渲染
 */
public class TemplateResolver {

    /**
     * 匹配纯变量占位符 ${name}，不匹配函数调用 ${name(...)}
     * 注意：也需要匹配嵌套在函数参数中的变量，如 ${lower(${projectId})} 中的 projectId
     */
    private static final Pattern UNRESOLVED_VAR_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

    private final VariableGroupRegistry registry;
    private final TemplateParser parser = new TemplateParser();

    public TemplateResolver(VariableGroupRegistry registry) {
        this.registry = registry;
    }

    /**
     * 解析模板，按需懒加载变量组。
     *
     * @param template URL 模板
     * @param basicVars 基础变量（appId, methodName）
     * @return 完全解析后的 URL
     * @throws VariableResolveException 某个 Group 解析失败时抛出
     */
    public String resolve(String template, Map<String, String> basicVars)
            throws VariableResolveException {

        // 第一轮：用基础变量渲染
        String firstPass = parser.parse(template, basicVars);

        // 扫描剩余未解析的变量名
        Set<String> unresolvedVars = extractUnresolvedVariables(firstPass);

        if (unresolvedVars.isEmpty()) {
            return firstPass;
        }

        // 找到需要调用的 Group（去重）
        Set<VariableGroup> neededGroups = registry.findGroupsForVariables(unresolvedVars);

        if (neededGroups.isEmpty()) {
            // 未解析的变量不属于任何已注册的 Group，返回第一轮结果
            return firstPass;
        }

        // 逐个 Group 解析，收集新变量
        Map<String, String> allVars = new HashMap<>(basicVars);
        for (VariableGroup group : neededGroups) {
            if (!group.isAvailable()) {
                throw new VariableResolveException(
                        "模板使用了扩展变量，请在设置中启用扩展字段功能并完成配置");
            }
            Map<String, String> resolved = group.resolve(allVars);
            allVars.putAll(resolved);
        }

        // 第二轮：用完整变量表渲染
        return parser.parse(template, allVars);
    }

    /**
     * 检查模板是否有 Group 需要异步加载。
     * 用于在跳转前判断是否需要弹出进度对话框。
     */
    public boolean needsAsyncResolve(String template, Map<String, String> basicVars) {
        String firstPass = parser.parse(template, basicVars);
        Set<String> unresolvedVars = extractUnresolvedVariables(firstPass);

        if (unresolvedVars.isEmpty()) {
            return false;
        }

        Set<VariableGroup> neededGroups = registry.findGroupsForVariables(unresolvedVars);
        for (VariableGroup group : neededGroups) {
            if (group.needsAsyncResolve(basicVars)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从渲染结果中提取未解析的变量名。
     * 匹配 ${variableName} 模式，排除已知的内置函数名。
     */
    public static Set<String> extractUnresolvedVariables(String text) {
        Set<String> result = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return result;
        }

        // 内置函数名不算未解析变量
        Set<String> builtinFunctions = Set.of(
                "lower", "upper", "urlEncode", "urlDecode", "base64Encode", "base64Decode");

        Matcher matcher = UNRESOLVED_VAR_PATTERN.matcher(text);
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!builtinFunctions.contains(varName)) {
                result.add(varName);
            }
        }
        return result;
    }
}
```

**Step 2: 编译验证**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/cn/bigcoder/soa/helper/variable/TemplateResolver.java
git commit -m "feat: 添加 TemplateResolver 模板解析编排器（懒加载核心）"
```

---

### Task 5: SoaHelperSettings 新增扩展字段配置

**Files:**
- Modify: `src/main/java/cn/bigcoder/soa/helper/settings/SoaHelperSettings.java`

**Step 1: 新增字段和 getter/setter**

在 `SoaHelperSettings.java` 的 `logJumpOptions` 字段之后，新增以下字段：

```java
/**
 * 扩展字段开关
 */
private boolean extendedFieldsEnabled = false;

/**
 * MOM 契约平台 API 基础 URL
 */
private String momBaseUrl = "";

/**
 * MOM 契约平台 Access Token
 */
private String momAccessToken = "";

/**
 * API 请求超时时间（毫秒）
 */
private int momTimeout = 5000;

/**
 * 缓存 TTL（秒）
 */
private int momCacheTtl = 300;
```

并为每个字段添加 getter/setter：

```java
public boolean isExtendedFieldsEnabled() {
    return extendedFieldsEnabled;
}

public void setExtendedFieldsEnabled(boolean extendedFieldsEnabled) {
    this.extendedFieldsEnabled = extendedFieldsEnabled;
}

public String getMomBaseUrl() {
    return momBaseUrl;
}

public void setMomBaseUrl(String momBaseUrl) {
    this.momBaseUrl = momBaseUrl;
}

public String getMomAccessToken() {
    return momAccessToken;
}

public void setMomAccessToken(String momAccessToken) {
    this.momAccessToken = momAccessToken;
}

public int getMomTimeout() {
    return momTimeout;
}

public void setMomTimeout(int momTimeout) {
    this.momTimeout = momTimeout;
}

public int getMomCacheTtl() {
    return momCacheTtl;
}

public void setMomCacheTtl(int momCacheTtl) {
    this.momCacheTtl = momCacheTtl;
}
```

**Step 2: 编译验证**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/cn/bigcoder/soa/helper/settings/SoaHelperSettings.java
git commit -m "feat: SoaHelperSettings 新增扩展字段配置项"
```

---

### Task 6: MomApiVariableGroup 实现

**Files:**
- Create: `src/main/java/cn/bigcoder/soa/helper/variable/impl/MomApiVariableGroup.java`

**Step 1: 创建 MomApiVariableGroup**

```java
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
 * 通过 HTTP API 获取 projectId、momVersion、serviceCode。
 * 内置按 appId 的 TTL 缓存，避免重复请求。
 */
public class MomApiVariableGroup implements VariableGroup {

    private static final Set<String> VARIABLE_NAMES = Set.of(
            "projectId", "momVersion", "serviceCode"
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
     * 解析 API 响应 JSON，按 appId 精确匹配
     */
    private Map<String, String> parseResponse(String responseBody, String appId)
            throws VariableResolveException {
        try {
            JsonArray array = JsonParser.parseString(responseBody).getAsJsonArray();

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
            result.put("projectId", String.valueOf(matched.get("id").getAsInt()));
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
```

**Step 2: 编译验证**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/cn/bigcoder/soa/helper/variable/impl/MomApiVariableGroup.java
git commit -m "feat: 添加 MomApiVariableGroup 契约平台变量组（含缓存）"
```

---

### Task 7: SoaHelperSettingsComponent UI 改造

**Files:**
- Modify: `src/main/java/cn/bigcoder/soa/helper/ui/SoaHelperSettingsComponent.java`

**Step 1: 新增扩展字段 UI 控件声明**

在类顶部字段声明区域（`logTable` 字段之后）新增：

```java
// 扩展字段配置
private final JCheckBox extendedFieldsCheckBox;
private final JTextField momBaseUrlField;
private final JPasswordField momAccessTokenField;
private final JSpinner momTimeoutSpinner;
private final JSpinner momCacheTtlSpinner;
```

**Step 2: 在构造方法中初始化扩展字段控件**

在构造方法中，`JPanel logTablePanel = logDecorator.createPanel();` 之后、创建 `helpLabel` 之前，添加：

```java
// ===== 扩展字段配置 =====
extendedFieldsCheckBox = new JCheckBox("启用扩展字段（projectId、momVersion、serviceCode）", false);
extendedFieldsCheckBox.setToolTipText("启用后可在 URL 模板中使用契约平台提供的扩展变量");

momBaseUrlField = new JTextField(40);
momBaseUrlField.setToolTipText("契约平台 API 基础地址，如：http://xxx.com");

momAccessTokenField = new JPasswordField(40);
momAccessTokenField.setToolTipText("契约平台 Access Token");

momTimeoutSpinner = new JSpinner(new SpinnerNumberModel(5000, 1000, 30000, 500));
momTimeoutSpinner.setToolTipText("API 请求超时时间（毫秒），建议 3000-10000");

momCacheTtlSpinner = new JSpinner(new SpinnerNumberModel(300, 0, 86400, 60));
momCacheTtlSpinner.setToolTipText("缓存有效时间（秒），设为 0 表示不缓存");

// 联动：开关控制子字段的 enabled 状态
extendedFieldsCheckBox.addActionListener(e -> updateExtendedFieldsEnabled());
updateExtendedFieldsEnabled();

// 构建扩展字段面板
JPanel extFieldsPanel = new JPanel(new GridBagLayout());
extFieldsPanel.setBorder(BorderFactory.createTitledBorder("扩展字段配置"));
GridBagConstraints eGbc = new GridBagConstraints();
eGbc.insets = new Insets(4, 8, 4, 8);
eGbc.anchor = GridBagConstraints.WEST;

eGbc.gridx = 0; eGbc.gridy = 0; eGbc.gridwidth = 2;
extFieldsPanel.add(extendedFieldsCheckBox, eGbc);

eGbc.gridx = 0; eGbc.gridy = 1; eGbc.gridwidth = 1; eGbc.weightx = 0;
extFieldsPanel.add(new JLabel("契约平台地址："), eGbc);
eGbc.gridx = 1; eGbc.weightx = 1; eGbc.fill = GridBagConstraints.HORIZONTAL;
extFieldsPanel.add(momBaseUrlField, eGbc);

eGbc.gridx = 0; eGbc.gridy = 2; eGbc.weightx = 0; eGbc.fill = GridBagConstraints.NONE;
extFieldsPanel.add(new JLabel("Access Token："), eGbc);
eGbc.gridx = 1; eGbc.weightx = 1; eGbc.fill = GridBagConstraints.HORIZONTAL;
extFieldsPanel.add(momAccessTokenField, eGbc);

eGbc.gridx = 0; eGbc.gridy = 3; eGbc.weightx = 0; eGbc.fill = GridBagConstraints.NONE;
extFieldsPanel.add(new JLabel("请求超时(ms)："), eGbc);
eGbc.gridx = 1; eGbc.weightx = 0; eGbc.fill = GridBagConstraints.NONE;
extFieldsPanel.add(momTimeoutSpinner, eGbc);

eGbc.gridx = 0; eGbc.gridy = 4; eGbc.weightx = 0;
extFieldsPanel.add(new JLabel("缓存时间(秒)："), eGbc);
eGbc.gridx = 1; eGbc.weightx = 0;
extFieldsPanel.add(momCacheTtlSpinner, eGbc);
```

**Step 3: 在 FormBuilder 中添加扩展字段面板**

修改 mainPanel 构建部分，在 logTablePanel 之后、helpLabel 之前插入：

```java
mainPanel = FormBuilder.createFormBuilder()
    .addComponent(enabledCheckBox)
    .addVerticalGap(5)
    .addLabeledComponent("SOA方法快速跳转：", tablePanel, true)
    .addVerticalGap(10)
    .addLabeledComponent("日志快速跳转：", logTablePanel, true)
    .addVerticalGap(10)
    .addComponent(extFieldsPanel)               // 新增
    .addComponentFillVertically(helpLabel, 0)
    .getPanel();
```

**Step 4: 添加联动方法**

```java
/**
 * 根据扩展字段开关状态，启用或禁用子控件
 */
private void updateExtendedFieldsEnabled() {
    boolean enabled = extendedFieldsCheckBox.isSelected();
    momBaseUrlField.setEnabled(enabled);
    momAccessTokenField.setEnabled(enabled);
    momTimeoutSpinner.setEnabled(enabled);
    momCacheTtlSpinner.setEnabled(enabled);
}
```

**Step 5: 修改 isModified / apply / reset 方法**

`isModified` 方法追加：

```java
public boolean isModified(SoaHelperSettings settings) {
    return enabledCheckBox.isSelected() != settings.isEnabled()
        || !tableModel.getOptions().equals(settings.getJumpOptions())
        || !logTableModel.getOptions().equals(settings.getLogJumpOptions())
        || extendedFieldsCheckBox.isSelected() != settings.isExtendedFieldsEnabled()
        || !momBaseUrlField.getText().equals(settings.getMomBaseUrl())
        || !new String(momAccessTokenField.getPassword()).equals(settings.getMomAccessToken())
        || (int) momTimeoutSpinner.getValue() != settings.getMomTimeout()
        || (int) momCacheTtlSpinner.getValue() != settings.getMomCacheTtl();
}
```

`apply` 方法追加：

```java
public void apply(SoaHelperSettings settings) {
    settings.setEnabled(enabledCheckBox.isSelected());
    settings.setJumpOptions(new ArrayList<>(tableModel.getOptions()));
    settings.setLogJumpOptions(new ArrayList<>(logTableModel.getOptions()));
    settings.setExtendedFieldsEnabled(extendedFieldsCheckBox.isSelected());
    settings.setMomBaseUrl(momBaseUrlField.getText());
    settings.setMomAccessToken(new String(momAccessTokenField.getPassword()));
    settings.setMomTimeout((int) momTimeoutSpinner.getValue());
    settings.setMomCacheTtl((int) momCacheTtlSpinner.getValue());
}
```

`reset` 方法追加：

```java
public void reset(SoaHelperSettings settings) {
    enabledCheckBox.setSelected(settings.isEnabled());
    tableModel.setOptions(settings.getJumpOptions());
    logTableModel.setOptions(settings.getLogJumpOptions());
    extendedFieldsCheckBox.setSelected(settings.isExtendedFieldsEnabled());
    momBaseUrlField.setText(settings.getMomBaseUrl());
    momAccessTokenField.setText(settings.getMomAccessToken());
    momTimeoutSpinner.setValue(settings.getMomTimeout());
    momCacheTtlSpinner.setValue(settings.getMomCacheTtl());
    updateExtendedFieldsEnabled();
}
```

**Step 6: 更新帮助文本**

在现有帮助文本的 "SOA方法跳转 - 内置变量" 部分之后，追加：

```java
"<b>SOA方法跳转 - 扩展变量（需启用扩展字段）：</b><br/>" +
"• <code>projectId</code> - 契约平台项目ID<br/>" +
"• <code>momVersion</code> - 契约版本号<br/>" +
"• <code>serviceCode</code> - 服务代码<br/>" +
"<i>注：扩展变量在跳转时按需获取，需在设置中配置契约平台地址和Token</i><br/><br/>" +
```

**Step 7: 更新 JumpOptionDialog 预览示例值**

在 `JumpOptionDialog.updatePreview()` 方法中，`sampleVars` 新增：

```java
sampleVars.put("projectId", "67890");
sampleVars.put("momVersion", "3");
sampleVars.put("serviceCode", "sample.service");
```

**Step 8: 在 JumpOptionDialog 中添加扩展变量提示**

在 `JumpOptionDialog.createCenterPanel()` 中，在 `enabledCheckBox` 之前添加提示 label：

```java
// 扩展变量提示标签
JLabel extHintLabel = new JLabel("");
extHintLabel.setFont(extHintLabel.getFont().deriveFont(Font.ITALIC, 11f));
```

在 `updatePreview()` 方法末尾追加提示逻辑：

```java
// 检查模板是否使用了扩展变量
Set<String> extendedVarNames = Set.of("projectId", "momVersion", "serviceCode");
boolean usesExtended = extendedVarNames.stream()
        .anyMatch(v -> template.contains("${" + v + "}"));
if (usesExtended && !SoaHelperSettings.getInstance().isExtendedFieldsEnabled()) {
    extHintLabel.setText("该模板使用了扩展变量，请确保在设置中启用扩展字段功能");
    extHintLabel.setForeground(new Color(200, 130, 0));
} else {
    extHintLabel.setText("");
}
```

**Step 9: 编译验证**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 10: Commit**

```bash
git add src/main/java/cn/bigcoder/soa/helper/ui/SoaHelperSettingsComponent.java
git commit -m "feat: Settings UI 新增扩展字段配置区域、预览更新、编辑提示"
```

---

### Task 8: SoaHelperSettingsConfigurable 保存验证

**Files:**
- Modify: `src/main/java/cn/bigcoder/soa/helper/settings/SoaHelperSettingsConfigurable.java`

**Step 1: 在 apply() 中添加验证逻辑**

将现有 `apply()` 方法修改为先验证再保存：

```java
@Override
public void apply() throws ConfigurationException {
    // 验证扩展字段配置
    if (settingsComponent.isExtendedFieldsEnabled()) {
        if (settingsComponent.getMomBaseUrl() == null 
                || settingsComponent.getMomBaseUrl().trim().isEmpty()) {
            throw new ConfigurationException("启用扩展字段时，契约平台地址不能为空");
        }
        if (settingsComponent.getMomAccessToken() == null 
                || settingsComponent.getMomAccessToken().trim().isEmpty()) {
            throw new ConfigurationException("启用扩展字段时，Access Token 不能为空");
        }
    }

    SoaHelperSettings settings = SoaHelperSettings.getInstance();
    settingsComponent.apply(settings);
}
```

**Step 2: 在 SoaHelperSettingsComponent 中暴露验证所需的 getter**

```java
public boolean isExtendedFieldsEnabled() {
    return extendedFieldsCheckBox.isSelected();
}

public String getMomBaseUrl() {
    return momBaseUrlField.getText();
}

public String getMomAccessToken() {
    return new String(momAccessTokenField.getPassword());
}
```

**Step 3: 编译验证**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/cn/bigcoder/soa/helper/settings/SoaHelperSettingsConfigurable.java \
        src/main/java/cn/bigcoder/soa/helper/ui/SoaHelperSettingsComponent.java
git commit -m "feat: 扩展字段保存时验证必填项"
```

---

### Task 9: SoaMethodJumpMarkerProvider 跳转流程改造

**Files:**
- Modify: `src/main/java/cn/bigcoder/soa/helper/marker/SoaMethodJumpMarkerProvider.java`

**Step 1: 添加 import 声明**

```java
import cn.bigcoder.soa.helper.variable.*;
import cn.bigcoder.soa.helper.variable.impl.BasicVariableGroup;
import cn.bigcoder.soa.helper.variable.impl.MomApiVariableGroup;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import java.util.Set;
```

**Step 2: 添加 MomApiVariableGroup 单例字段**

在类顶部添加：

```java
/**
 * MOM API 变量组单例 —— 所有跳转共享同一个实例（共享缓存）
 */
private static final MomApiVariableGroup MOM_API_GROUP = new MomApiVariableGroup();
```

**Step 3: 替换 buildUrlFromTemplate 为 performJump**

删除现有的 `buildUrlFromTemplate` 方法，替换为：

```java
/**
 * 执行跳转 —— 按需懒加载扩展变量。
 * 三条路径：
 * 1. 快速路径：模板没用扩展变量 → 直接跳转
 * 2. 缓存路径：用了扩展变量，缓存命中 → 直接跳转
 * 3. 异步路径：用了扩展变量，缓存未命中 → 进度框 + 后台请求
 */
private void performJump(Project project, String urlTemplate, String appId, String methodName) {
    Map<String, String> basicVars = new HashMap<>();
    basicVars.put("appId", appId);
    basicVars.put("methodName", methodName);

    // 构建 Registry
    VariableGroupRegistry registry = new VariableGroupRegistry();
    registry.register(new BasicVariableGroup(appId, methodName));
    registry.register(MOM_API_GROUP);

    TemplateResolver resolver = new TemplateResolver(registry);

    // 判断是否需要异步加载
    if (!resolver.needsAsyncResolve(urlTemplate, basicVars)) {
        // 快速路径 或 缓存路径：同步解析
        try {
            String url = resolver.resolve(urlTemplate, basicVars);
            BrowserUtil.browse(url);
        } catch (VariableResolveException e) {
            NotifyUtil.showError(project, "跳转失败：" + e.getMessage());
        }
        return;
    }

    // 异步路径：显示进度对话框
    ProgressManager.getInstance().run(new Task.Modal(project, "正在获取扩展字段...", true) {
        private String resolvedUrl;
        private VariableResolveException error;

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
            indicator.setIndeterminate(true);
            try {
                resolvedUrl = resolver.resolve(urlTemplate, basicVars);
            } catch (VariableResolveException e) {
                error = e;
            }
        }

        @Override
        public void onSuccess() {
            if (resolvedUrl != null) {
                BrowserUtil.browse(resolvedUrl);
            } else if (error != null) {
                NotifyUtil.showError(project, "扩展字段获取失败：" + error.getMessage());
            }
        }

        @Override
        public void onCancel() {
            // 用户取消，静默退出
        }
    });
}
```

**Step 4: 更新调用点**

将 `showEnvironmentPopup` 中所有 `buildUrlFromTemplate` + `BrowserUtil.browse` 的调用替换为 `performJump`。

单选项直接跳转的地方：

```java
// 原：
// String url = buildUrlFromTemplate(option.getUrlTemplate(), appId, methodName);
// BrowserUtil.browse(url);
// 新：
performJump(project, option.getUrlTemplate(), appId, methodName);
```

弹出菜单 `onChosen` 回调中：

```java
// 原：
// String url = buildUrlFromTemplate(selectedValue.getUrlTemplate(), appId, methodName);
// BrowserUtil.browse(url);
// 新：
performJump(project, selectedValue.getUrlTemplate(), appId, methodName);
```

注意：`showEnvironmentPopup` 方法签名需要增加 `Project project` 参数（如果尚未有的话），并从调用方传入。

**Step 5: 编译验证**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/java/cn/bigcoder/soa/helper/marker/SoaMethodJumpMarkerProvider.java
git commit -m "feat: 跳转流程改用 TemplateResolver，支持三条路径懒加载"
```

---

### Task 10: 最终集成验证

**Step 1: 全量编译**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 2: 运行插件沙箱手动验证**

Run: `./gradlew runIde`

验证场景：
1. 打开设置 → SOA Helper → 确认扩展字段配置区域正常显示
2. 勾选"启用扩展字段" → 子字段变为可编辑
3. 取消勾选 → 子字段变灰
4. 不填地址/Token 时点保存 → 应弹出错误提示
5. 编辑跳转选项 → URL 模板输入 `${projectId}` → 预览应显示 `67890`
6. 不启用扩展字段时使用扩展变量的模板 → 预览下方应显示橙色警告
7. 使用不含扩展变量的模板跳转 → 应即时跳转（与以前一致）
8. 配置好契约平台后，使用含扩展变量的模板跳转 → 首次应显示进度框，之后应走缓存直接跳转

**Step 3: Commit（如有修复）**

```bash
git add -A
git commit -m "fix: 集成测试修复"
```
