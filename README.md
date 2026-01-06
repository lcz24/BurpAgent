# BurpAgent

**BurpAgent** 是一款为 Burp Suite 打造的下一代 AI 增强插件。它不仅集成了 OpenAI/GPT 的强大分析能力，更通过引入 **MCP (Model Context Protocol)** 支持，让 Burp Suite 能够连接本地工具、数据库甚至远程 Agent，成为一个真正的智能安全测试助手。

![BurpAgent Status](https://img.shields.io/badge/Status-Active-green) ![Java](https://img.shields.io/badge/Language-Java-orange) ![BurpSuite](https://img.shields.io/badge/Platform-Burp%20Suite-blue)

## ✨ 核心功能 (Features)

### 1. 🤖 智能流量分析
- **自动化漏洞检测**：利用 GPT-4 等大模型对 HTTP 请求/响应进行深度语义分析，发现传统扫描器难以察觉的逻辑漏洞。
- **上下文感知**：支持自定义 Prompt 模板，能够理解应用场景（如代码审计、渗透测试）。

### 2. 🔌 MCP (Model Context Protocol) 集成 [独家亮点]
BurpAgent 是首个支持 MCP 协议的 Burp 插件，这极大地扩展了它的能力边界：
- **连接本地工具**：通过 `stdio` 协议运行本地 MCP Server（如文件操作、本地数据库查询）。
- **连接远程 Agent**：支持 `SSE` (Server-Sent Events) 协议，连接部署在远程服务器上的复杂 Agent。
- **实时状态监控**：在设置面板实时查看所有 MCP Server 的连接状态 (🟢 已连接 / 🟡 连接中 / 🔴 失败)。
- **工具互操作**：大模型可以直接调用 MCP Server 提供的工具（如 `read_file`, `execute_sql` 等）来辅助分析。

### 3. 🛠️ 自定义工具扩展 (Function Calling)
除了 MCP，你还可以轻松挂载自己的脚本作为 AI 可调用的工具：
- 支持 **Python (.py)**, **Bash (.sh)**, **Batch (.bat/cmd)** 脚本。
- AI 会根据任务自动决定是否调用这些脚本，并解析执行结果。
- **安全沙箱**：内置命令黑名单和执行确认机制，防止 AI 执行危险操作（如 `rm -rf`）。

### 4. 🧠 技能系统 (Agent Skills)
- 预设多种角色技能（如 "代码审计员", "红队专家"）。
- 支持用户创建和保存自定义 System Prompt，针对不同测试目标快速切换 AI 人格。

---

## 🚀 安装部署 (Installation)

### 环境要求
- **JDK**: Java 17 或更高版本
- **Burp Suite**: Professional 或 Community Edition (推荐 2023.12+)
- **Gradle**: (可选，仅构建需要)

### 方式一：下载构建好的 JAR
1. 在 GitHub Releases 页面下载最新的 `BurpAgent-x.x.x.jar`。
2. 打开 Burp Suite -> **Extensions** -> **Add**。
3. Extension type 选择 **Java**，Select file 选择下载的 JAR 包。

### 方式二：源码构建
```bash
# 1. 克隆仓库
git clone https://github.com/your-repo/BurpAgent.git
cd BurpAgent

# 2. 编译构建 (Windows)
.\gradlew.bat clean jar

# 2. 编译构建 (Linux/Mac)
./gradlew clean jar
```
构建完成后，JAR 文件位于 `build/libs/BurpAgent-1.0-SNAPSHOT.jar`。

---

## 📖 使用指南 (Usage)

插件加载后，你会看到一个名为 **"BurpAgent Settings"** 的新标签页。

### 1. 基础配置 (General Settings)
- **API Key**: 填入你的 OpenAI API Key (或兼容接口的 Key，如 DeepSeek)。
- **API URL**: 默认为 OpenAI 官方地址，可修改为中转代理地址。
- **Model**: 选择模型（推荐 `gpt-4o` 或 `gpt-4-turbo` 以获得最佳工具调用体验）。
- **Prompt Template**: 定义 AI 分析时的默认提示词，支持 `{REQUEST}` 和 `{RESPONSE}` 占位符。

### 2. 配置 MCP 服务器 (MCP Configuration)
这是 BurpAgent 最强大的功能。在 "MCP Servers" 区域，你可以添加多个 MCP 服务。

**格式支持两种：**

#### A. 简单管道模式 (Pipe/Stdio)
适用于本地运行的 MCP Server。格式：`名称|启动命令`
```text
Filesystem|npx -y @modelcontextprotocol/server-filesystem D:\projects
Git|npx -y @modelcontextprotocol/server-git
```

#### B. JSON 高级配置 (支持 SSE/HTTP)
适用于需要连接远程服务或复杂参数的场景。
```json
{
  "mcpServers": {
    "Local-Dev": {
      "command": "python",
      "args": ["server.py"],
      "transport": "stdio"
    },
    "Remote-Analysis": {
      "url": "http://localhost:8000/sse",
      "transport": "sse"
    }
  }
}
```

**控制与状态：**
- **启用/禁用**：通过 "Enable MCP Integration" 复选框一键开关。
- **状态指示灯**：
  - 🟢 **Connected**: 正常工作。
  - 🟡 **Connecting**: 正在握手或启动。
  - 🔴 **Failed**: 连接失败（鼠标悬停或查看日志可看原因）。
- **重启**：点击 "Restart MCP Servers" 可在不重启 Burp 的情况下重连。

### 3. 发起分析
1. 在 Burp Suite 的 **Proxy** 或 **Repeater** 中，右键点击任意请求。
2. 选择 **Extensions** -> **Send to BurpAgent**。
3. 弹出的窗口将实时显示 AI 的分析结果和工具调用过程。

---

## 🌟 优势 (Advantages)

| 功能 | 传统 Burp 插件 | BurpAgent |
| :--- | :--- | :--- |
| **扩展性** | 仅限 Java/Python API | **无限** (通过 MCP 连接任何语言编写的工具) |
| **工具调用** | 无或硬编码 | **原生 Function Calling** + **MCP Tool 自动发现** |
| **连接性** | 仅本地 | **本地进程 + 远程 SSE 连接** |
| **用户体验** | 静态配置 | **实时状态监控 + 异步无阻塞加载** |
| **安全性** | 弱 | **命令黑名单 + 敏感操作确认** |

---

## 🤝 贡献 (Contributing)
欢迎提交 Issue 和 Pull Request！如果你开发了有趣的 MCP Server 并想在 Burp 中使用，请分享给我们。

## 📄 许可证 (License)
MIT License
