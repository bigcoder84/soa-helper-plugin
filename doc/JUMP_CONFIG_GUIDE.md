# SOA Helper 跳转配置功能使用指南

## 功能概述

SOA Helper 插件现在支持灵活的跳转配置功能。你可以在设置中配置多个跳转选项，每个选项可以定义自己的 URL 模板，支持变量替换和函数调用。

## 主要特性

1. **可配置的跳转选项**：支持配置多个跳转目标（如不同环境的日志、监控系统等）
2. **灵活的模板语法**：支持变量、函数、嵌套函数和字符串拼接
3. **内置函数**：提供常用的字符串处理函数（大小写转换、URL编码、Base64编码等）
4. **易于管理**：支持启用/禁用、排序、编辑和删除配置项

## 配置步骤

### 1. 打开设置页面

- 菜单路径：`Settings/Preferences -> Tools -> SOA Helper`
- 或者：`⌘,`（Mac）/ `Ctrl+Alt+S`（Windows/Linux） 然后搜索 "SOA Helper"

### 2. 添加跳转选项

点击列表上方的 `+` 按钮，在弹出的对话框中填写：

- **名称**：跳转选项的显示名称（如：生产环境Clog、测试环境Bat）
- **URL 模板**：使用模板语法编写的 URL 模板
- **启用该选项**：勾选后该选项才会在跳转菜单中显示

### 3. 编辑现有选项

- 选中列表中的某一项
- 点击编辑按钮（铅笔图标）
- 或者双击列表项

### 4. 管理选项

- **删除**：选中后点击 `-` 按钮
- **排序**：使用上下箭头按钮调整顺序
- **启用/禁用**：直接勾选或取消勾选列表中的复选框

## URL 模板语法

### 基本语法

#### 1. 变量

使用 `${变量名}` 引用内置变量：

```
${appId}        # 应用ID
${methodName}   # 方法名
```

**示例：**
```
https://example.com?app=${appId}&method=${methodName}
```

#### 2. 函数

使用 `${函数名(参数)}` 调用内置函数：

```
${lower(methodName)}      # 转小写
${upper(appId)}           # 转大写
${urlEncode(methodName)}  # URL编码
```

**支持的函数：**

| 函数 | 说明 | 示例 |
|------|------|------|
| `lower(str)` | 转小写 | `${lower(methodName)}` |
| `upper(str)` | 转大写 | `${upper(appId)}` |
| `urlEncode(str)` | URL编码 | `${urlEncode(methodName)}` |
| `urlDecode(str)` | URL解码 | `${urlDecode(encodedText)}` |
| `base64Encode(str)` | Base64编码 | `${base64Encode(data)}` |
| `base64Decode(str)` | Base64解码 | `${base64Decode(encodedData)}` |

#### 3. 嵌套函数

函数支持嵌套调用：

```
${urlEncode(lower(methodName))}           # 先转小写，再URL编码
${base64Encode(upper(appId))}             # 先转大写，再Base64编码
${urlEncode(base64Encode(methodName))}    # 先Base64编码，再URL编码
```

#### 4. 字符串拼接

使用 `+` 连接字符串（JavaScript 风格）：

```
${'prefix_' + methodName + '_suffix'}
${'prod_' + lower(methodName)}
```

**注意：**
- 字面量字符串需要用单引号 `'` 或双引号 `"` 包裹
- 支持转义字符：`\'`, `\"`, `\\`, `\n`, `\r`, `\t`


## 常见问题

### Q: 如何测试模板是否正确？

A: 建议先手动在浏览器中构造一个完整的 URL，验证可以正常访问后，再将其改写为模板格式。

### Q: 支持自定义变量吗？

A: 目前只支持内置变量 `appId` 和 `methodName`。如有其他需求，可以通过字符串拼接实现。

### Q: 为什么点击图标没有反应？

A: 请检查：
1. 是否配置了启用的跳转选项
2. 项目中是否存在 `app.properties` 文件并配置了 `app.id`
3. 查看 IDE 右下角是否有错误通知

### Q: URL 中的特殊字符如何处理？

A: 使用 `urlEncode()` 函数对包含特殊字符的部分进行编码。例如：
```
${urlEncode('title=' + methodName)}
```

### Q: 如何配置多个相同环境但不同参数的选项？

A: 可以创建多个跳转选项，每个选项使用不同的名称和 URL 模板。例如：
- "PRO-错误日志"：只查询 ERROR 级别
- "PRO-全部日志"：查询所有级别

## 贡献和反馈

如有问题或建议，欢迎：

1. 提交 GitHub Issue
2. 发送邮件至：bigcoder84@gmail.com

---

**版本**：1.0.2+  
**更新时间**：2025

