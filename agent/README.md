# 灵漫「小灵」AI Agent — 技术实现文档

> 本文档整理了灵漫动漫平台 AI Agent「小灵」的完整技术实现，覆盖系统架构、Agent 核心逻辑、工具层、身份安全、流式输出与配置部署，便于理解现状与后续扩展。

---

## 一、项目概述

**灵漫（LingAni）** 是一个动漫追番平台，核心功能包括：首页推荐、追番周表、排行榜、番剧详情、在线播放（ArtPlayer + 弹幕）、追番/收藏、播放历史、评论、Live2D 看板娘，以及 AI 助手「小灵」。

小灵是一个 **ReAct 风格的自主工具调用 Agent**，运行在独立的 Python 服务中（FastAPI + LangGraph），通过 SSE 流式接口与前端通信，所有平台数据均通过 Spring Boot 现有业务接口获取。

整体回答采用**两阶段架构**：

- **阶段 1**：带工具的对话模型自主完成 ReAct 工具循环，产出可信的 `ToolMessage` 数据；
- **阶段 2**：独立的结构化输出模型把工具结果整理成前端可直接消费的 `{reply, works, actions}` JSON。

---

## 二、系统架构

### 2.1 三服务分离

```
┌─────────────────────┐  HTTP/JWT   ┌──────────────────────┐  X-Agent-JWT    ┌──────────────────────┐
│  前端 Vue 3 + Vite   │ ──────────► │  Spring Boot 后端     │ ◄────────────── │  Python Agent 服务    │
│  :5173               │ ◄────────── │  :8080                │ ──────────────► │  :8000               │
└─────────────────────┘  同源代理     └──────────────────────┘  内部调用        └──────────────────────┘
         │                                     │                                     │
         │ Vite dev server 同源代理            │ MySQL 数据库                         │ LLM API（deepseek / ollama）
         │ /api       → :8080                 │ user_favorite, user_history         │ LangGraph MemorySaver
         │ /agent-api → :8000                 │ user_notification, anime_comment    │
```

### 2.2 双 JWT 架构（关键约束）

```
用户浏览器                                    后端 Spring Boot                   Python Agent
   │                                              │                                  │
   │  ① 登录 → User JWT（长期，7天）               │                                  │
   │  ② 请求 Agent 对话前，先 POST /api/internal/agent/context                     │
   │     携带 User JWT ─────────────────────────►│                                  │
   │                                              │  校验 User JWT                    │
   │                                              │  签发 Agent JWT（短期，300s）       │
   │     ◄───────────────────────────────────────│                                  │
   │  ③ POST /api/agent/chat/stream               │                                  │
   │     Authorization: Bearer <Agent JWT> ──────────────────────────────────────►│
   │                                              │                                  │  验证 Agent JWT
   │                                              │                                  │  提取 user_id + display_name
   │                                              │                                  │  写入请求级 ContextVar
   │                                              │  ④ Agent 调用个人数据工具          │
   │                                              │ ◄──── Header: X-Agent-JWT ──────│
   │                                              │       （原样回传同一份令牌）        │
```

**安全红线**：

- Agent 服务不得信任前端传入的 `userId`，身份完全由 Agent JWT 的 payload 决定。
- Agent 回调后端个人数据接口（历史、追番）时，使用 `X-Agent-JWT` 头，不使用 `Authorization`。
- User JWT 与 Agent JWT 使用**不同密钥、不同 audience、不同 TTL**。

---

## 三、Agent 服务核心实现

### 3.1 目录结构

```
agent/
├── app/
│   ├── main.py                  # FastAPI 入口：CORS、路由、SSE、异常映射、身份注入
│   ├── config.py                # 环境变量加载与启动期校验（Settings 冻结数据类）
│   ├── agent/
│   │   ├── graph.py             # LangGraph 图构建 + 两阶段 Agent 主逻辑 + 流式
│   │   ├── prompts.py           # System Prompt + ANSWER_PROMPT + 页面上下文注入
│   │   ├── state.py             # AgentState TypedDict
│   │   ├── session.py           # thread_id 校验、会话归属校验、消息窗口裁剪
│   │   ├── llm.py               # 模型初始化（deepseek / ollama，支持 json_mode）
│   │   └── output.py            # JSON 解析、works/actions 清洗、ID 白名单、reply 流式抽取
│   ├── tools/
│   │   ├── anime.py             # 8 个公共数据工具（搜索/详情/章节/推荐/首页/周表/榜单）
│   │   └── user.py              # 5 个个人数据工具（历史/进度/追番/添加追番/检查追番）
│   ├── services/
│   │   └── backend_client.py    # 统一封装 httpx 调用后端
│   ├── security/
│   │   ├── agent_jwt.py         # Agent JWT 验签
│   │   └── context.py           # RequestIdentity + ContextVar 请求级上下文
│   └── schemas/
│       └── chat.py              # ChatRequest / PageContext / ChatResponse
├── requirements.txt
├── .env.example
└── .gitignore
```

### 3.2 LangGraph 图结构

```
START ──► agent (LLM + bind_tools) ──► 条件判断 tools_condition
              ▲                              │
              │                        ┌──────┴──────┐
              │                        ▼             ▼
              │                     ToolNode        END
              │                        │
              └────────────────────────┘
```

实现要点：

- `add_node("agent", _call_model)`：每轮动态构建 System Prompt 后调用工具模型。
- `add_node("tools", ToolNode(tools, handle_tool_errors=_tool_error_message))`：工具异常不中断图，转为 `工具调用失败：{exc}` 的 ToolMessage 回灌，模型可换工具或换关键词重试。
- `tools_condition`：模型返回 `tool_calls` 时进入 `tools`，否则进入 `END`。
- `compile(checkpointer=MemorySaver())`：开启进程内会话记忆。
- `recursion_limit = 20`：限制图的最大步数，防止工具循环失控。
- 无工具时退化为 `agent → END` 的纯对话图。

`AgentState` 定义：

```python
class AgentState(TypedDict, total=False):
    messages: Annotated[list, add_messages]   # 会话消息，add_messages 归并
    user_id: str
    display_name: str
    request_id: str
    user_context: dict[str, Any]              # 已白名单过滤的页面上下文
```

### 3.3 两阶段回答流程

```
第一阶段：自主 ReAct 循环
  SystemPrompt → LLM → 判断是否调工具 → 循环直到不再调工具
  产出：工具结果（ToolMessage）留在 graph messages 里，但不直接对用户输出

第二阶段：格式化最终回答
  取全部消息 → 提取本轮工具结果 + 最近对话 + 用户最新问题 → 作为 HumanMessage
  另一个 LLM 实例（json_mode=True）+ ANSWER_PROMPT → 产出合法 JSON
  {"reply": "...", "works": [...], "actions": [...]}
```

**为什么两阶段？** 第一阶段让模型专注判断要不要调工具，第二阶段让模型专注输出符合前端格式的 JSON。分离后提示词职责清晰、JSON 格式更稳定，并可在阶段 2 单独开启 `json_mode`。

阶段 2 的输入由 `_format_input` 组装，并做三级体积控制：

| 组成 | 内容 | 限制 |
|------|------|------|
| `【用户最新问题】` | 当前轮最后一条用户消息 | 无 |
| `【最近对话】` | 当前轮之前的用户/助手发言（跳过带 tool_calls 的消息） | 最多 6 行 |
| `【本轮工具结果】` | 按 `[工具名] 结果` 逐块拼接 | 单块 ≤ 4000 字符，累计 ≤ 16000 字符，超出替换为省略提示 |

### 3.4 System Prompt 结构（build_system_prompt）

```
你是「小灵」，灵漫动漫平台的 AI 助手。

【当前环境】2026-11-17 21:30，周二。
【用户身份】当前用户昵称：xxx。
【当前播放上下文】用户当前正在观看作品 video_id=12345，当前集 section_id=678，
                 该集已播放 12 分 30 秒。只能讨论已播放范围内的内容。

1. 角色定位
   - 熟悉站内动漫数据
   - 语气活泼友好、自然简洁，带有轻度二次元风格
   - 默认回复 1～4 句，推荐最多 5 部

2. 核心规则
   - 数据必须真实，不得编造
   - 个人数据必须查工具
   - 参数依赖顺序（先搜索拿 ID，再用 ID 查详情）
   - 优先使用工具
   - 简洁回答，不输出内部 ID / 接口 / 工具名
   - 不擅自操作（追番等写操作需用户明确要求）

3. 可用工具（13 个）

4. 业务规则
   4.1 搜索动漫
   4.2 作品详情与分集
   4.3 个性化推荐
   4.4 更新排播
   4.5 排行榜
   4.6 观看历史与进度
   4.7 剧情讨论与防剧透（最高优先级）
   4.7.1 前情提要
   4.8 追番操作

5. 异常处理

6. 普通聊天
```

页面上下文注入规则（`_page_context_line`）：

- 仅当 `current_video_id` 为纯数字时注入；
- `page=detail`：强调"这部""它"直接指代该 ID，但剧情问题仍须先查进度；
- `page=play`：额外给出 `current_section_id` 与已播放时长（`progress // 60` 分 `progress % 60` 秒），并声明"工具返回的进度优先于这里"。

### 3.5 工具列表

#### 公共数据工具（8 个，匿名可调用）

| 工具 | 功能 | 参数 | 后端接口 |
|------|------|------|----------|
| `search_anime` | 按关键词搜索 | q, page, page_size | `GET /api/cycani/videos/search` |
| `get_anime_detail` | 作品详情 | video_id | `GET /api/cycani/videos/{id}` |
| `get_anime_sections` | 章节列表 | video_id, page, page_size | `GET /api/cycani/videos/{id}/sections` |
| `get_anime_recommendations` | 相似推荐 | video_id, limit | `GET /api/cycani/videos/{id}/recommendations` |
| `get_home_recommend` | 首页推荐 | 无 | `GET /api/cycani/index/recommend` |
| `get_weekday_schedule` | 追番周表 | weekday（可选，1–7） | `GET /api/cycani/index/weekday` |
| `get_rank_list` | 榜单列表 + 每榜 top5 | 无 | `GET /api/cycani/ranks`（+ 每榜 `/videos`） |
| `get_rank_videos` | 指定榜单内容 | rank_id | `GET /api/cycani/ranks/{id}/videos` |

#### 个人数据工具（5 个，需 Agent JWT）

| 工具 | 功能 | 参数 | 后端接口 |
|------|------|------|----------|
| `get_user_history` | 观看历史 | current, size | `GET /api/history` |
| `get_user_progress` | 单部观看进度 | video_id 或 anime_title | `GET /api/history/video/{id}` |
| `get_user_favorites` | 追番列表 | current, size | `GET /api/favorites` |
| `add_favorite` | 添加追番 | video_id, anime_title（可选） | `POST /api/favorites` |
| `check_favorite` | 检查追番状态 | video_id 或 anime_title | `GET /api/favorites/check` |

工具层实现要点：

- **身份注入**：个人工具内部调用 `require_identity()` 从 ContextVar 取身份，工具签名中没有 `userId` 参数，模型无从伪造。
- **ID 约束**：公共工具的 `video_id` / `rank_id` 必须匹配 `^\d{1,20}$`；个人工具允许只给 `anime_title`，由 `_resolve_video_id` 调用公开搜索接口解析真实 ID。
- **标题清洗**：`_clean_title` 去掉书名号与引号，剥离口语前缀（`我的|我|咱们|我们|你|请问|帮我|帮忙|查一下|看看|关于`）和意图后缀（`看到第几集了|看到哪了|的进度|追了吗|有没有收藏|收藏了吗`）。
- **回包瘦身**：`_video_brief` 只保留 `video_id/title/year/area/score/remarks/total/tags`；`_clip` 把换行压成空格并截断；`_dump` 使用紧凑 JSON（`ensure_ascii=False`）。
- **分页归一化**：`page ≥ 1`，`size` 限制在 `[1, max_size]`（公共工具 50，个人工具 30）。

### 3.6 输出格式（ANSWER_PROMPT）

最终回答必须是合法 JSON：

```json
{
  "reply": "给用户看的自然语言回答",
  "works": [{"videoId": "12345", "title": "《进击的巨人》"}],
  "actions": [{"type": "navigate", "path": "/anime/12345"}]
}
```

- `works`：最多 10 部，必须覆盖 reply 中提到的所有作品（前端据此生成番名内联跳转链接）；标题必须与工具结果完全一致并用《》包裹。
- `actions`：最多 2 个，只允许：
  - `navigate`：`/search?q=`、`/anime/{id}`、`/anime/{id}/play/{sectionId}`、`/schedule`、`/rank`、`/category`、`/user/history`、`/user/favorites`
  - `add_favorite`：`{type, videoId}`
- **陪伴页规则**：在播放页/详情页且用户问的是当前作品时，`actions` 必须返回 `[]`，避免打断观看。
- **陪伴轻量模式**：有 `current_video_id` 时 reply 限制 3 句以内。

#### 服务端强制校验（防模型幻觉）

```python
video_ids, section_ids, _ = extract_tool_references(本轮所有 ToolMessage)
sanitize_output(parsed, video_ids, section_ids)
```

- `works` 中 ID 不在本轮工具结果白名单的条目直接丢弃；
- `navigate` 到 `/anime/{id}` 要求 id 命中白名单，`/{id}/play/{sectionId}` 要求两者同时命中；
- `add_favorite` 的 `videoId` 同样必须命中；
- 结构清洗还包括：`videoId` 纯数字、去重、路径禁止 `://` 与 `//`、长度 ≤ 200。

### 3.7 SSE 流式接口

端点：`POST /api/agent/chat/stream`，请求头 `Authorization: Bearer <Agent JWT>`。

请求体：

```json
{
  "message": "帮我推荐一部热血番",
  "thread_id": "可选，首次不传",
  "context": { "page": "play", "current_video_id": "3924", "current_section_id": "12345", "progress": 180 }
}
```

事件顺序：

```
meta    → {"thread_id": "xxx"}
tool    → {"name": "search_anime", "args": {"q": "银魂"}}   ← 工具调用状态
token   → {"text": "小"}
token   → {"text": "灵"}
...（多个 token）
done    → {"thread_id": "xxx", "works": [...], "actions": [...]}
```

异常：`error` 事件，含 `code` 和 `msg`。

响应头携带 `Cache-Control: no-cache`、`Connection: keep-alive`、`X-Accel-Buffering: no`，防止代理缓冲。

### 3.8 流式实现细节

阶段 1（工具调用）：

- 以 `stream_mode="messages"` 运行状态图，只处理 `metadata["langgraph_node"] == "agent"` 的 chunk。
- `chunk.tool_call_chunks` 按 `index` 累积 `name` / `args` 字符串，每次尝试 `json.loads`，解析成功才发出 `tool_call` 事件。
- 兼容不产出 chunks 的模型：直接读取 `chunk.tool_calls`（args 已是完整 dict）。
- 两条分支共用 `(name, 排序后 args)` 去重，保证同一调用只发一次。

阶段 2（回答文本）：

- `ReplyStreamExtractor` 是逐字符状态机（`search → reply → done`）：先定位 `"reply"` 键与开引号，再持续解转义（含 `\uXXXX`、`\n`、`\"`），遇到未转义的闭引号停止。
- 效果是前端只收到人类可读的 reply，不会看到 JSON 骨架；缓冲区超过 8192 字符仍找不到 `reply` 时自动放弃。
- 全部 chunk 拼完后整体解析，得到 `works` / `actions`；解析失败则退回非流式格式化（带一次重试）。
- 若流式阶段一个 token 都没发出，结束时补发 `token`（内容为最终 reply），避免前端空白。

### 3.9 会话与记忆

- **会话标识**：`thread_id` 必须匹配 `^[A-Za-z0-9_-]{8,128}$`，不传则生成 `uuid4().hex`；格式非法返回 400。
- **归属绑定**：`SessionRegistry`（加锁字典）记录 `thread_id → user_id`，首次使用时绑定，他人复用抛 `SessionOwnershipError`（403）。
- **双重校验**：除登记表外，`_ensure_ownership` 还会比对 graph 快照中的 `values["user_id"]`，防止仅绕过内存字典。
- **记忆存储**：`MemorySaver` 按 `thread_id` 隔离消息历史，进程重启即丢失。
- **窗口裁剪**：`trim_messages(messages, 20)` 只保留最近 20 条，并保证：
  - 窗口首条不能是 `ToolMessage`（避免工具结果脱离调用消息）；
  - 窗口末条不能是带 `tool_calls` 的 `AIMessage`（避免出现无人应答的调用）。
- **历史写回**：`_remember` 用最终 reply 覆盖本轮最后一条 `AIMessage`（复用其 `id`），避免阶段 1 中间文本与阶段 2 最终回答在历史中重复。

### 3.10 身份与令牌校验

`RequestIdentity`（冻结数据类）包含 `user_id`、`display_name`、`agent_jwt`，`__repr__` 已脱敏。身份存放在 `ContextVar`，请求开始时写入、`finally` 中清除，保证请求间隔离。

`verify_agent_jwt` 只校验不签发：

| 校验项 | 规则 |
|--------|------|
| 算法 | 仅 `HS256` |
| 签名密钥 | `AGENT_JWT_SECRET`，编码后必须 ≥ 32 字节 |
| `iss` / `aud` | 等于 `AGENT_JWT_ISSUER`（默认 `lingani-backend`）/ `AGENT_JWT_AUDIENCE`（默认 `lingani-agent`） |
| 必需声明 | `exp`、`iat`、`iss`、`aud`、`sub` |
| 用途 | `scope` 必须为 `agent` |
| 主体 | `sub` 必须是纯数字用户 ID |

### 3.11 后端调用与错误映射

`BackendClient` 使用**懒加载的 httpx.AsyncClient 单例**（`base_url` + 超时 + `Accept: application/json`），服务关闭时统一 `close()`。

| 传输/响应情况 | 转换为 `BackendError` |
|---------------|----------------------|
| 请求超时 | "后端服务响应超时，请稍后再试" |
| 连接失败 | "无法连接灵漫后端服务，请确认后端已启动" |
| HTTP 401 | "登录状态已失效，请重新登录" |
| HTTP 403 | "没有权限执行该操作" |
| HTTP ≥ 500 | "后端服务暂时不可用，请稍后再试" |
| HTTP ≥ 400 | 响应体 `msg`，否则 `请求失败（HTTP {status}）` |
| 响应体非 `{code,msg,data}` | "后端返回了无法识别的数据格式" |
| `code != 200` | 抛出 `msg` 与 `code` |
| 正常 | 返回 `data` 字段 |

### 3.12 配置加载（启动期校验）

`load_dotenv(.env)` 后构造冻结的 `Settings`，以下情况**启动即失败**：

- 缺少 `AGENT_JWT_SECRET`；
- `LLM_PROVIDER=deepseek` 但 `DEEPSEEK_API_KEY` 为空；
- 数值型变量（端口、温度、超时、历史条数）无法解析。

兼容旧变量名：`LLM_TEMPERATURE` ← `OLLAMA_TEMPERATURE`，`LLM_TIMEOUT_SECONDS` ← `OLLAMA_TIMEOUT_SECONDS`。

### 3.13 当前 LLM 配置

- Provider 默认 `deepseek`（`deepseek-chat`），可通过 `LLM_PROVIDER=ollama` 切换到本地 `qwen2.5:7b`
- Temperature 0.3（较保守，减少幻觉）
- Max history messages: 20（工具消息 + 对话消息合计）
- Recursion limit: 20（防止工具调用死循环）
- 阶段 2 开启 JSON 模式：DeepSeek 使用 `response_format={"type":"json_object"}`，Ollama 使用 `format="json"`（`num_ctx=8192`）

---

## 四、接口说明

### `GET /health`

```json
{ "status": "ok", "service": "lingani-agent", "model": "deepseek-chat" }
```

### `POST /api/agent/chat`

非流式对话，请求体与流式接口相同。

响应：

```json
{
  "reply": "……",
  "thread_id": "会话 ID",
  "works": [{ "videoId": "3924", "title": "黑色五叶草 第二季" }],
  "actions": [{ "type": "navigate", "path": "/anime/3924" }]
}
```

`context` 字段（仅用于陪伴模式判断，不作为身份凭证）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `page` | `play` / `detail` / 不传 | 页面类型 |
| `current_video_id` | string | 当前作品 ID |
| `current_section_id` | string | 当前集 ID |
| `progress` | int | 当前集已播放秒数 |

错误统一为 `{ "code": ..., "msg": "...", "data": null }`：

| 状态码 | 场景 |
|--------|------|
| 401 | 缺少 / 非法 / 过期的委托令牌 |
| 400 | `thread_id` 格式非法 |
| 403 | `thread_id` 属于其他用户 |
| 422 | 消息为空或超长 |
| 502 | 后端业务接口异常 |
| 503 | 模型服务不可用或模型调用失败 |

---

## 五、安装与启动

```bash
cd agent

python -m venv .venv
.venv\Scripts\activate          # Windows
# source .venv/bin/activate     # macOS / Linux

pip install -r requirements.txt

copy .env.example .env          # Windows
# cp .env.example .env          # macOS / Linux
# 至少填写 AGENT_JWT_SECRET；provider=deepseek 时填 DEEPSEEK_API_KEY

uvicorn app.main:app --host 127.0.0.1 --port 8000
```

启动顺序：

1. Spring Boot 后端（8080）
2. 模型：`deepseek` 无需本地模型；`ollama` 先 `ollama serve` 并 `ollama pull qwen2.5:7b`
3. 本 Agent 服务（8000）
4. 前端（5173，`npm run dev`）

环境要求：Python 3.11+（当前验证环境 Python 3.14）。

---

## 六、LLM Provider 配置

| 变量 | 说明 | 默认 |
|------|------|------|
| `LLM_PROVIDER` | `deepseek` 或 `ollama` | `deepseek` |
| `OLLAMA_BASE_URL` | 本地 Ollama 地址 | `http://localhost:11434` |
| `OLLAMA_MODEL` | Ollama 模型名 | `qwen2.5:7b` |
| `DEEPSEEK_API_KEY` | DeepSeek API Key | 必填（provider=deepseek） |
| `DEEPSEEK_BASE_URL` | DeepSeek 接口地址 | `https://api.deepseek.com` |
| `DEEPSEEK_MODEL` | 模型名 | `deepseek-chat` |
| `LLM_TEMPERATURE` | 采样温度 | `0.3` |
| `LLM_TIMEOUT_SECONDS` | 模型请求超时 | `120` |
| `AGENT_MAX_HISTORY_MESSAGES` | 消息窗口大小 | `20` |

---

## 七、环境变量速查

| 变量 | 服务 | 说明 |
|------|------|------|
| `AGENT_JWT_SECRET` | backend + agent | **必须一致**，Agent 委托令牌密钥（≥ 32 字符） |
| `USER_JWT_SECRET` | backend | 用户 JWT 密钥，与 Agent 无关 |
| `AGENT_JWT_ISSUER` | agent | 默认 `lingani-backend` |
| `AGENT_JWT_AUDIENCE` | agent | 默认 `lingani-agent` |
| `AGENT_JWT_TTL_SECONDS` | agent | 默认 `300`，前端拿到的令牌有效期 |
| `BACKEND_BASE_URL` | agent | `http://localhost:8080` |
| `BACKEND_TIMEOUT_SECONDS` | agent | 默认 `15` |
| `AGENT_HOST` / `AGENT_PORT` | agent | 默认 `127.0.0.1` / `8000` |
| `AGENT_CORS_ORIGINS` | agent | `http://localhost:5173,http://127.0.0.1:5173` |
| `AGENT_BASE_URL` | frontend（.env） | 默认 `/agent-api`，同源代理 |
| `VITE_BACKEND_BASE_URL` | frontend（.env） | 默认 `/api`，同源代理 |

> 密钥只放在 `.env`（已被 `.gitignore` 忽略），仓库内仅保留 `.env.example` 占位值。

---

## 八、端口

| 服务 | 端口 |
|------|------|
| Spring Boot 后端 | 8080 |
| Python Agent | 8000 |
| Vite 前端 | 5173 |
| Ollama（本地） | 11434 |
| MySQL | 3306 |

---

## 九、异常与降级

| 场景 | 行为 |
|------|------|
| 模型服务不可用 | 非流式 503；流式 `error` 事件（503）；绝不编造回答 |
| 结构化输出非法 JSON | 附带纠正提示重试一次；仍失败按降级链回退，`works`/`actions` 置空 |
| 模型编造 ID | `sanitize_output` 按本轮工具结果白名单丢弃 |
| 工具执行异常 | 转为 ToolMessage 回灌，模型可换工具/换关键词重试 |
| 后端超时/不可用 | 工具收到 `BackendError`，模型如实告知；纯聊天不受影响 |
| 后端 401 | 提示"登录状态已失效" |
| 会话被他人复用 | 403，不泄露任何会话内容 |
| 图执行超步数 | `recursion_limit=20` 中断，返回兜底回复 |

降级优先级（阶段 2 完全失败时）：流式已推送文本 → 阶段 1 最后一条无工具调用的回答 → 固定兜底话术。

---

## 十、关键文件路径速查

| 文件 | 作用 |
|------|------|
| `agent/app/agent/prompts.py` | System Prompt + ANSWER_PROMPT |
| `agent/app/agent/graph.py` | LangGraph 主逻辑、两阶段回答、流式 |
| `agent/app/agent/session.py` | 会话归属校验 + 消息窗口裁剪 |
| `agent/app/agent/output.py` | JSON 解析、works/actions 清洗、ID 白名单 |
| `agent/app/agent/state.py` | AgentState 定义 |
| `agent/app/agent/llm.py` | DeepSeek / Ollama 模型构建 |
| `agent/app/tools/anime.py` | 公共数据工具实现 |
| `agent/app/tools/user.py` | 个人数据工具实现 |
| `agent/app/services/backend_client.py` | 后端 HTTP 客户端 |
| `agent/app/security/agent_jwt.py` | Agent JWT 验签 |
| `agent/app/security/context.py` | RequestIdentity + ContextVar |
| `agent/app/main.py` | FastAPI 入口 + SSE 端点 |
| `agent/app/config.py` | 环境变量配置与启动期校验 |
| `frontend/src/api/agent.js` | 前端 Agent 调用 + SSE 解析 |
| `frontend/src/components/AgentChat.vue` | 聊天框 UI + 流式渲染 |
| `frontend/src/stores/agentContext.js` | page_context 状态管理 |
| `backend/src/main/java/com/lingani/controller/AgentContextController.java` | Agent JWT 签发 |
| `backend/src/main/java/com/lingani/security/AgentJwtService.java` | Agent JWT 服务 |

---

## 十一、已知限制

| 限制 | 说明 |
|------|------|
| 记忆为进程内 `MemorySaver` | 进程重启后会话历史丢失，多实例部署不共享记忆 |
| 会话归属表在内存 | `thread_id → user_id` 绑定随重启清空，但 graph 快照中的 `user_id` 仍提供第二道校验 |
| 委托令牌 300 秒 | 仅覆盖单次对话链路，前端每次发送前重新申请 |
| 消息窗口 20 条 | 工具消息占比大，有效对话历史实际只有几轮 |
| 提示词硬编码 | System Prompt / ANSWER_PROMPT 为 Python 字符串，不便做 A/B 测试或场景化人格切换 |
| 无后台任务 | Agent 只有同步请求-响应模式，无法主动推送关怀消息 |
| 推荐能力有限 | 只有单部相似推荐与首页推荐，无基于用户偏好的个性化召回 |
