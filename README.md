# BurpAgent

**BurpAgent** 将大语言模型 (LLM) 和 MCP (Model Context Protocol) 引入 Burp Suite，使其能够连接本地工具、数据库或远程 Agent，辅助安全测试。

![Status](https://img.shields.io/badge/Status-Active-green) ![Java](https://img.shields.io/badge/Language-Java-orange) ![BurpSuite](https://img.shields.io/badge/Platform-Burp%20Suite-blue)
<img width="3838" height="1850" alt="image" src="https://github.com/user-attachments/assets/5d0f86f6-ee3a-412a-be2b-e67ad2f13f6b" />

## 功能概览

### 1. 流量分析
- 利用 GPT-4/DeepSeek 等模型对 HTTP 请求/响应进行分析。
- 支持自定义 Prompt 模板，适配代码审计、漏洞挖掘等不同场景。

### 2. MCP (Model Context Protocol) 支持
支持 MCP 协议，扩展 Burp Suite 的能力：
- **本地工具**：通过 `stdio` 运行本地 MCP Server（如文件操作）。
- **远程连接**：通过 `SSE` 连接远程 MCP Server。
- **状态监控**：在设置面板查看 MCP Server 连接状态。
- **工具调用**：模型可直接调用 MCP Server 提供的工具。

### 3. 自定义脚本工具
支持挂载 Python/Bash/Batch 脚本作为 AI 可调用的工具：
- 自动解析执行结果。
- 内置命令黑名单和执行确认机制。

### 4. 技能预设 (Skills)
- 预设多种角色（如 "代码审计员"）。
- 支持保存自定义 System Prompt，快速切换分析视角。

---

## 快速开始

### 1. 安装
1. 在 [Releases](../../releases) 页面下载 `BurpAgent-x.x.x.jar`。
2. 打开 Burp Suite -> **Extensions** -> **Add**。
3. 选择下载的 JAR 包。

### 2. 配置
加载插件后，进入 **BurpAgent Settings** 标签页：
1. **API Key**: 填入 OpenAI 或兼容接口的 API Key。
2. **Model**: 推荐使用 `gpt-4o` 以获得更好的工具调用体验。
3. **Prompt Template**: 定义默认的分析提示词，支持 `{REQUEST}` 和 `{RESPONSE}` 占位符。
4. **Tools Directory**: 设置存放自定义工具脚本的本地目录（例如 `D:\burp-tools`）。
<img width="3833" height="2076" alt="image" src="https://github.com/user-attachments/assets/0ee16e3c-6471-4789-a7db-a777379eb63c" />

### 3. 技能管理 (Agent Skills)
技能 (Skills) 是一组预设的 System Prompts，用于切换 AI 的分析角色。
- 在 **Agent Skills** 标签页查看、添加或编辑技能。
- 分析时，可以在窗口顶部的下拉菜单中快速切换（例如从 "Default" 切换到 "Code Auditor"）。
<img width="3836" height="2077" alt="image" src="https://github.com/user-attachments/assets/0a36c988-6d55-4b08-804a-860bd04a6cdb" />

### 4. 使用
1. 在 **Proxy** 或 **Repeater** 中右键点击请求。
2. 选择 **Extensions** -> **Send to BurpAgent**。
3. 分析窗口将自动弹出并开始工作。

---

## 进阶配置

### 配置 MCP 服务器
在 **MCP Servers** 区域添加服务。

**方式一：管道模式 (本地)**
格式：`名称|命令`
```text
Filesystem|npx -y @modelcontextprotocol/server-filesystem D:\projects
```

**方式二：JSON 配置 (远程/复杂参数)**
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

### 添加自定义工具 (Custom Tools)

自定义工具允许你通过编写 Python 或 Bash 脚本来扩展 AI 的能力。

**示例：创建一个 SQLMap 调用工具**

1. **前提**：在 `General Settings` 中设置好 `Tools Directory`。
2. 进入 **Custom Tools** 标签页，点击 **Add Tool**。
3. 填写如下信息：

- **Name**: `run_sqlmap`
- **Description**: `Run sqlmap on a target URL to test for SQL injection.`
- **Script Type**: `Python`
- **Parameters (JSON Schema)**:
  ```json
  {
    "type": "object",
    "properties": {
      "url": {
        "type": "string",
        "description": "The target URL to scan"
      },
      "level": {
        "type": "string",
        "description": "Level of tests to perform (1-5)",
        "default": "1"
      }
    },
    "required": ["url"]
  }
  ```
- **Script Content**:
  ```python
  import argparse
  import subprocess

  def main():
      parser = argparse.ArgumentParser()
      parser.add_argument('--url', required=True)
      parser.add_argument('--level', default='1')
      args, unknown = parser.parse_known_args()

      # 构建 sqlmap 命令 (请确保 sqlmap 在 PATH 中或使用绝对路径)
      cmd = ["sqlmap", "-u", args.url, "--batch", "--level", args.level]
      
      try:
          result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
          print(result.stdout)
      except Exception as e:
          print(f"Error: {str(e)}")

  if __name__ == "__main__":
      main()
  ```

4. 点击 **Save** 保存。现在，当你在分析请求时，如果 AI 认为需要进行 SQL 注入测试，它就会自动调用这个工具并读取 sqlmap 的输出。

---
<img width="3838" height="2077" alt="image" src="https://github.com/user-attachments/assets/9c755c1e-2b55-486e-a367-3fda2826b5ca" />

## 常见问题

**Q: 插件加载后 Burp 变慢？**
A: 插件采用异步加载 MCP，不影响 Burp 启动。如果连接远程 MCP 超时，请检查网络配置。

**Q: 状态显示 Failed？**
A: 鼠标悬停在红色状态上可查看错误详情。通常是命令路径错误或缺少依赖（如 Node.js/Python）。

---

## 构建项目
```bash
# Windows
.\gradlew.bat clean jar

# Linux/Mac
./gradlew clean jar
```
构建产物位于 `build/libs/` 目录。

## 许可证
MIT License
