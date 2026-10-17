# Codex Android

Codex Android 是基于 Codex app-server 的非官方原生 Android 客户端，让你可以通过 Android 设备连接并操作自己的 Codex 服务端

> [!WARNING]
> 本项目不是 OpenAI 官方产品，与 OpenAI 没有关联或背书
>
> 项目目前处于早期开发阶段，功能和兼容性可能随 Codex app-server 演进而调整

## 截图

| 页面 | 深色模式 | 浅色模式 |
| --- | --- | --- |
| 会话与流式活动 | <img src="docs/images/conversation.png" alt="Codex Android 深色模式会话页面" width="300"> | <img src="docs/images/conversation-light.png" alt="Codex Android 浅色模式会话页面" width="300"> |
| 侧边栏最近会话 | <img src="docs/images/recent-conversations.png" alt="Codex Android 深色模式侧边栏最近会话" width="300"> | <img src="docs/images/recent-conversations-light.png" alt="Codex Android 浅色模式侧边栏最近会话" width="300"> |
| 限额圆环详情 | <img src="docs/images/rate-limit-details.png" alt="Codex Android 深色模式限额圆环展开详情" width="300"> | <img src="docs/images/rate-limit-details-light.png" alt="Codex Android 浅色模式限额圆环展开详情" width="300"> |

## 项目状态

- 最低支持 Android 8.0（API 26）
- 可从 [GitHub Releases](https://github.com/hebo6/codex-android/releases/latest) 下载正式 APK
- app-server WebSocket 传输本身仍是实验性接口，不建议用于生产环境

## 功能

- 添加、编辑、删除并切换多个 Codex 服务端
- 使用 Android Keystore 加密保存 capability token
- 按项目组织、搜索、恢复、重命名、分叉、归档和删除会话，支持复制会话 ID
- 保存新任务和会话草稿，并选择服务端项目目录提交任务
- 流式展示回复、推理、计划、命令执行、文件修改和工具活动
- 在历史消息内快捷回复异步提问，并接收后台提问通知
- 排队发送下一回合消息，撤销待发送消息或撤回到草稿编辑
- 按文件查看本轮汇总差异，并区分终端输入与命令输出
- 查看子代理的任务、状态与结果，打开子会话并返回父会话
- 处理命令、文件、权限审批、用户问题和 MCP 确认请求
- 配置模型、推理强度、审批策略、审批者和沙箱权限
- 支持设备图片、图片地址、Skills、Apps、Plugins、快捷短语和 Shell 输入
- 预览、保存和分享服务端文件与图片
- 查看账号、使用额度和 Token 用量，并管理 Skills、Plugins 与 MCP 服务
- 支持任务完成与待审批通知
- 自动适配浅色和深色主题

## 使用要求

- 运行应用需要 Android 8.0 或更高版本
- 服务端需要提供与当前客户端兼容的 Codex app-server
- Android 设备需要能够访问服务端 WebSocket 地址
- 跨设备连接使用 TLS（`wss://`）或 SSH 端口转发

## 配置服务端

准备一个仅当前用户可读的高强度随机 capability token 文件，例如使用 OpenSSL 生成

```bash
umask 077
mkdir -p "$HOME/.codex"
openssl rand -hex 32 > "$HOME/.codex/app-server-token"
```

在服务端启动仅监听本机地址的 app-server

```bash
codex app-server \
  --listen ws://127.0.0.1:8390 \
  --ws-auth capability-token \
  --ws-token-file "$HOME/.codex/app-server-token"
```

若 Codex 运行在同一台 Android 设备上，在应用中填写 `ws://127.0.0.1:8390`，并粘贴 token 文件中的原始内容

连接另一台设备时，可在 Android 的 SSH 客户端（例如 Termux）中建立本地转发，将 `user@server` 替换为服务端 SSH 登录地址

```bash
ssh -N -L 127.0.0.1:8390:127.0.0.1:8390 user@server
```

保持 SSH 转发运行，应用仍连接 `ws://127.0.0.1:8390`，并使用服务端的 capability token

也可由 TLS 终止代理提供 `wss://codex.example.com`，转发到上述本机监听地址，再在应用中填写该 `wss://` 地址。app-server 的 `--listen` 使用 `ws://`，TLS 由代理处理

连接方式与认证参数参见 [官方连接指南](https://learn.chatgpt.com/docs/app-server#connect-the-cli-terminal-ui) 和 [app-server 命令参考](https://learn.chatgpt.com/docs/developer-commands#codex-app-server)

## 安全说明

- `ws://` 不提供传输加密，仅用于本机或 SSH 端口转发；直接跨设备连接使用 `wss://`
- 不要将 app-server 直接暴露在公共网络或不可信的共享网络中
- capability token 仅用于客户端到 app-server 的连接认证，不要复用 Codex 账号凭据或 OpenAI API Key
- capability token 在 Android 设备上使用 Android Keystore 和 AES-GCM 加密保存

## 构建

构建环境要求

- JDK 26
- Android SDK 37
- 使用仓库提供的 Gradle Wrapper，无需单独安装 Gradle

构建 Debug APK

```bash
./gradlew :app:assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`

通过 Android SDK Platform Tools 安装 APK

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

运行单元测试

```bash
./gradlew :app:testDebugUnitTest
```

## 连接异常排查

生产版本通过 Logcat 的 `CodexConnection` 标签记录连接失败、非主动断开、请求超时和会话恢复失败，正常连接、消息收发和主动取消不记录

```bash
adb logcat -d -v threadtime 'CodexConnection:W' '*:S'
```

日志包含连接编号、服务器内部 ID、失败阶段与耗时、恢复进度、待响应请求数及事件消费状态，同一连接的终止异常只记录一次，同类请求超时或恢复失败按连接和操作限频至每分钟一次，不输出地址、Token、消息正文、请求参数或原始异常消息

## 协议

客户端使用省略 `jsonrpc` 字段的 JSON-RPC 2.0 消息，并在每条连接上完成 `initialize` 和 `initialized` 握手，通过 `capabilities.experimentalApi: true` 启用所需的实验性接口

承载 Android 连接的 WebSocket 传输由上游标记为 experimental 和 unsupported

当前协议核对基准为 `codex-cli 0.162.1`（2026-10-17）。Schema 与导出它的 CLI 版本对应；升级时先记录版本，再导出包含实验性字段和方法的 JSON Schema

```bash
codex --version
mkdir -p "$HOME/tmp"
CODEX_ANDROID_SCHEMA_DIR="$(mktemp -d "$HOME/tmp/codex-android-schema.XXXXXX")"
codex app-server generate-json-schema --experimental --out "$CODEX_ANDROID_SCHEMA_DIR"
```

以聚合 Schema 的 `ClientRequest`、`ServerRequest`、`ClientNotification` 和 `ServerNotification` 核对方法名与消息方向，并同步更新请求参数、响应与通知映射、业务模型及相关测试

协议详情参见 [Codex App Server 文档](https://learn.chatgpt.com/docs/app-server)、[Schema 导出说明](https://learn.chatgpt.com/docs/developer-commands#codex-app-server) 和 [Codex app-server 源码](https://github.com/openai/codex/tree/main/codex-rs/app-server)

## 技术栈

- Kotlin 2.3 与 Android Gradle Plugin 9 内置 Kotlin
- Jetpack Compose 与 Material 3
- Navigation Compose 类型安全路由
- Coroutines 与 StateFlow
- Room 与 DataStore Preferences
- Android Keystore AES-GCM
- Java-WebSocket
- kotlinx.serialization JSON

## 工程结构

```text
app/src/main/java/com/hebo/codex
├── data/local     本地数据库、服务端配置与 Token 加密
├── data/remote    WebSocket、JSON-RPC 与协议映射
├── domain         仓库、会话状态与业务模型
└── ui             Compose 页面、组件与主题
```

## 贡献

欢迎通过 Issue 报告问题或提出建议，也欢迎提交 Pull Request。涉及较大范围的功能或架构调整时，建议先通过 Issue 讨论方案

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源
