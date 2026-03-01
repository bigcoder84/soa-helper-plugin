# 扩展字段支持设计文档

## 概述

为 SOA Helper 插件的 URL 模板跳转功能增加扩展字段支持。现有模板变量仅支持 `${appId}` 和 `${methodName}`，本次新增 `${projectId}`、`${momVersion}`、`${serviceCode}` 三个扩展变量，数据通过 MOM 契约平台 API 实时获取。

## 设计决策

| 决策项 | 选择 | 原因 |
|---|---|---|
| API 失败行为 | 阻止跳转，显示错误通知 | 避免用户看到残缺 URL |
| 缓存策略 | 按 appId 缓存，TTL 可配置 | 平衡请求频率和数据时效 |
| HTTP 客户端 | `java.net.HttpURLConnection` | 零依赖，JDK 内置 |
| 线程模型 | IntelliJ `ProgressManager` + `Task.Modal` | 不阻塞 EDT，可取消 |
| Token 存储 | Settings XML 明文 | 企业内部工具，简单优先 |
| JSON 解析 | IntelliJ 内置 Gson | 无需额外依赖 |

## 核心架构：分组变量提供器 + 模板驱动懒加载

### 设计原则

核心矛盾：变量是单个的，但数据源是批量的（一次 API 返回多个变量）。引入 `VariableGroup` 概念——一组变量由同一个数据源提供，要么整组加载，要么整组不加载。

```
TemplateParser 用基础变量渲染模板（第一轮）
       |
  发现未解析的变量 (e.g. ${projectId}, ${serviceCode})
       |
  通过 Registry 找到这些变量所属的 VariableGroup（去重）
       |
  对每个需要的 Group 只调用一次 resolve()
       |
  Group 一次返回多个变量的值 (Map<String, String>)
       |
  合并到变量表，完成模板渲染（第二轮）
```

### VariableGroup 接口

```java
package cn.bigcoder.soa.helper.variable;

public interface VariableGroup {
    /** 该组提供的所有变量名 */
    Set<String> getVariableNames();

    /** 一次性解析该组所有变量 */
    Map<String, String> resolve(Map<String, String> context) throws VariableResolveException;

    /** 该组是否可用（开关是否开启、配置是否完整） */
    boolean isAvailable();

    /** 是否需要异步加载（缓存未命中时返回 true） */
    boolean needsAsyncResolve(Map<String, String> context);
}
```

### 两个实现

| 类 | 变量 | 数据源 | needsAsyncResolve |
|---|---|---|---|
| `BasicVariableGroup` | appId, methodName | 构造时传入 | 始终 false |
| `MomApiVariableGroup` | projectId, momVersion, serviceCode | HTTP API | 缓存未命中时 true |

### VariableGroupRegistry

管理所有 VariableGroup，提供按变量名查找所属 Group 的能力：

```java
public class VariableGroupRegistry {
    private final List<VariableGroup> groups = new ArrayList<>();

    public void register(VariableGroup group) { ... }

    /** 给定未解析变量名集合，返回需要调用的 VariableGroup（去重） */
    public Set<VariableGroup> findGroupsForVariables(Set<String> unresolvedVarNames) { ... }
}
```

## TemplateResolver 编排层

不修改 TemplateParser，新增 `TemplateResolver` 负责懒加载编排：

```java
public class TemplateResolver {
    private final VariableGroupRegistry registry;
    private final TemplateParser parser = new TemplateParser();

    /** 解析模板，按需懒加载变量组 */
    public String resolve(String template, Map<String, String> basicVars)
            throws VariableResolveException {
        // 第一轮：用基础变量渲染
        String firstPass = parser.parse(template, basicVars);

        // 扫描剩余未解析变量
        Set<String> unresolvedVars = extractUnresolvedVariables(firstPass);
        if (unresolvedVars.isEmpty()) {
            return firstPass;
        }

        // 找到需要的 Group，逐个 resolve
        Set<VariableGroup> neededGroups = registry.findGroupsForVariables(unresolvedVars);
        Map<String, String> allVars = new HashMap<>(basicVars);
        for (VariableGroup group : neededGroups) {
            if (!group.isAvailable()) {
                throw new VariableResolveException("变量组不可用，请检查扩展字段配置");
            }
            allVars.putAll(group.resolve(allVars));
        }

        // 第二轮：用完整变量表渲染
        return parser.parse(template, allVars);
    }

    /** 提取未解析的变量名（正则匹配 ${name}，排除函数调用） */
    public static Set<String> extractUnresolvedVariables(String text) { ... }

    /** 检查是否有 Group 需要异步加载 */
    public boolean needsAsyncResolve(String template, Map<String, String> basicVars) { ... }
}
```

## MomApiVariableGroup 实现

### HTTP 调用

- 接口：`POST {momBaseUrl}/api/osg/project`
- Header：`access-token: {momAccessToken}`，`Content-Type: application/json`
- Body：`{"term": "<appId>"}`
- 响应：JSON 数组，模糊匹配结果，需按 `appId` 字段精确匹配

```java
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
result.put("projectId", String.valueOf(matched.get("id").getAsInt()));
result.put("momVersion", String.valueOf(matched.get("version").getAsInt()));
result.put("serviceCode", matched.get("serviceCode").getAsString());
```

### 缓存设计

- 粒度：按 appId 缓存，`ConcurrentHashMap<String, CacheEntry>`
- 过期：惰性过期，读取时检查 `System.currentTimeMillis() > expireTimeMillis`
- TTL：从 `SoaHelperSettings.getMomCacheTtl()` 读取，用户可配置
- 清理：不做主动清理，插件场景 appId 数量有限

## Settings 改造

### 新增配置字段（SoaHelperSettings）

```java
private boolean extendedFieldsEnabled = false;  // 扩展字段开关
private String momBaseUrl = "";                  // 契约平台地址
private String momAccessToken = "";              // Access Token
private int momTimeout = 5000;                   // 请求超时（毫秒）
private int momCacheTtl = 300;                   // 缓存 TTL（秒）
```

### Settings UI（SoaHelperSettingsComponent）

在日志快速跳转表格与帮助文本之间新增"扩展字段配置"区域：

- `JCheckBox`：启用扩展字段（联动控制下方字段的 enabled 状态）
- `JTextField`：契约平台地址
- `JPasswordField`：Access Token
- `JSpinner`：请求超时（毫秒）
- `JSpinner`：缓存时间（秒）

### JumpOptionDialog 预览更新

新增示例值：`projectId=67890`、`momVersion=3`、`serviceCode=sample.service`

### 帮助文本更新

追加扩展变量说明段落。

### 编辑对话框提示

模板使用扩展变量但扩展字段未启用时，预览区下方显示警告提示（非阻塞）。

### 保存验证（SoaHelperSettingsConfigurable）

扩展字段启用时，校验契约平台地址和 Access Token 不为空，否则抛出 `ConfigurationException` 阻止保存。

## 跳转流程改造

### 三条路径

```
用户点击 gutter icon → 获取 appId → 显示弹出菜单 → 用户选择 JumpOption
       |
  第一轮渲染（同步）
       |
  有未解析变量？── 否 → 直接跳转（快速路径，0ms）
       | 是
       |
  需要的 Group 全部 needsAsyncResolve=false？── 是 → 同步 resolve → 直接跳转（缓存路径，<1ms）
       | 否
       |
  Task.Modal("正在获取扩展字段...") {
      resolve() → 跳转 或 显示错误通知
      支持用户 Cancel 取消
  }
```

| 路径 | 条件 | 延迟 | 用户感知 |
|---|---|---|---|
| 快速路径 | 模板没用扩展变量 | 0ms | 即时跳转 |
| 缓存路径 | 用了扩展变量，缓存命中 | <1ms | 即时跳转 |
| 异步路径 | 用了扩展变量，缓存未命中 | 网络延迟 | 进度条，可取消 |

## 错误处理

| 场景 | 处理 |
|---|---|
| 扩展字段未启用/配置不全 | `isAvailable()` 返回 false → 通知提示 |
| 网络超时 | `VariableResolveException("契约平台请求超时")` |
| 网络不可达 | `VariableResolveException("无法连接契约平台")` |
| HTTP 非200 | `VariableResolveException("HTTP状态码：" + code)` |
| JSON 格式异常 | `VariableResolveException("响应格式异常")` |
| appId 无精确匹配 | `VariableResolveException("未找到精确匹配")` |
| 返回空数组 | `VariableResolveException("未找到项目信息")` |
| 用户取消 | 静默退出 |

所有错误通过 `NotifyUtil.showError()` 以 Notification Balloon 展示。

## 文件变更清单

### 新增文件

| 文件 | 职责 |
|---|---|
| `variable/VariableGroup.java` | 变量组接口 |
| `variable/VariableResolveException.java` | 解析异常 |
| `variable/VariableGroupRegistry.java` | 注册表 |
| `variable/TemplateResolver.java` | 模板解析编排器 |
| `variable/impl/BasicVariableGroup.java` | 基础变量组 |
| `variable/impl/MomApiVariableGroup.java` | MOM 契约平台变量组 |

### 修改文件

| 文件 | 改动 |
|---|---|
| `SoaHelperSettings.java` | 新增 5 个配置字段及 getter/setter |
| `SoaHelperSettingsComponent.java` | 新增扩展字段配置 UI、预览更新、编辑提示 |
| `SoaHelperSettingsConfigurable.java` | apply/reset/isModified 增加扩展字段、保存验证 |
| `SoaMethodJumpMarkerProvider.java` | 跳转逻辑改用 TemplateResolver，三条路径 |

### 不修改的文件

| 文件 | 原因 |
|---|---|
| `TemplateParser.java` | 职责不变 |
| `JumpOption.java` | 结构不变 |
