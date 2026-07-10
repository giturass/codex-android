# Codex Android

Codex Android 是 Codex app-server 协议的原生 Android 客户端

## 功能

- 添加、编辑、删除并切换多个 Codex 服务端
- 使用 Android Keystore 加密保存 capability token
- 在侧边栏按项目组织并搜索会话
- 选择服务端项目目录并直接提交新任务
- 恢复历史会话并继续对话
- 流式展示回复、思考、命令执行、文件修改和工具活动
- 处理命令执行与文件修改审批
- 中断正在运行的任务
- 自动适配浅色和深色主题

## 技术栈

- Kotlin 2.3 与 Android Gradle Plugin 9 内置 Kotlin
- Jetpack Compose 与 Material 3
- Navigation Compose 类型安全路由
- Coroutines 与 StateFlow
- DataStore Preferences
- Android Keystore AES-GCM
- OkHttp WebSocket
- kotlinx.serialization JSON

## 服务端

服务端可使用以下方式启动

```bash
codex app-server \
  --listen ws://10.150.99.201:8390 \
  --ws-auth capability-token \
  --ws-token-file ~/.codex/app-server-token
```

应用中填写相同的 WebSocket 地址，并粘贴 token 文件中的原始内容

非本机连接优先使用 `wss://` 或 SSH 端口转发，`ws://` 不提供传输加密

## 协议范围

客户端使用不带 `jsonrpc` 字段的 JSON-RPC 2.0 消息，并在每条连接上完成 `initialize` 和 `initialized` 握手

当前使用以下稳定接口和通知

- `thread/list`
- `thread/start`
- `thread/resume`
- `turn/start`
- `turn/interrupt`
- `turn/started` 与 `turn/completed`
- `item/started` 与 `item/completed`
- Agent、Reasoning、Command、FileChange 和 Plan 增量通知
- CommandExecution 与 FileChange 审批请求

未启用 experimental API capability

协议依据为 [Codex App Server 文档](https://learn.chatgpt.com/docs/app-server) 和本地 Codex 协议源码

## 工程结构

```text
app/src/main/java/com/hebo/codex
├── data/local     服务端配置与 Token 加密
├── data/remote    WebSocket、JSON-RPC 与协议映射
├── domain         仓库、会话状态与项目聚合
└── ui             Compose 页面、组件与主题
```

## 构建

项目使用 JDK 26 运行 Gradle，源码和字节码目标为 Java 17，并要求 Android SDK 37

```bash
./gradlew :app:assembleDebug
```

运行单元测试

```bash
./gradlew :app:testDebugUnitTest
```
