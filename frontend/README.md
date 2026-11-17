# 灵漫（LingAni）前端 — 技术实现文档

> 本文档整理了灵漫动漫平台前端的完整技术实现，覆盖路由权限、API 封装、AI 助手流式对话、播放器、Live2D 看板娘与开发代理配置，便于理解现状与后续维护。

---

## 一、项目概述

前端是灵漫平台的用户界面，基于 Vue 3 + Vite 构建，承担五类职责：

1. **内容浏览**：首页推荐、分类筛选、排行榜、追番周表、关键词搜索；
2. **观看体验**：番剧详情、ArtPlayer 播放、弹幕、断点续播、进度上报；
3. **个人中心**：登录注册、资料修改、追番列表、播放历史、站内通知；
4. **AI 助手「小灵」**：聊天框、SSE 流式渲染、工具调用状态、作品内联跳转；
5. **看板娘**：Live2D 模型加载、缩放/位置配置与持久化。

---

## 二、技术栈

| 类别 | 依赖 | 版本 | 用途 |
|------|------|------|------|
| 框架 | `vue` | ^3.5.44 | 组合式 API（`<script setup>`） |
| 路由 | `vue-router` | ^5.4.0 | History 模式 + 全局前置守卫 |
| 构建 | `vite` | ^8.3.6 | 开发服务器、HMR、代理 |
| 插件 | `@vitejs/plugin-vue` | ^6.0.10 | SFC 编译 |
| HTTP | `axios` | ^1.21.0 | 业务接口调用 |
| 播放器 | `artplayer` | ^5.4.0 | 视频播放 |
| 弹幕 | `artplayer-plugin-danmuku` | ^5.3.0 | 弹幕渲染 |
| Markdown | `marked` | ^18.2.0 | AI 回复渲染 |
| 安全 | `dompurify` | ^3.4.17 | 清洗 Markdown 生成的 HTML |
| 图标 | `lucide-vue-next` | ^0.577.0 | 图标组件 |
| 样式 | `sass` | ^1.108.1 | 样式预处理 |

脚本：`npm run dev`（`vite --host 0.0.0.0`）、`npm run build`（`vite build`）。

---

## 三、整体架构

### 3.1 分层

```
views（页面）
  │  组合 components（AgentChat / Live2DAvatar / VideoPlayer / 卡片 …）
  ▼
stores（跨组件状态）
  │  auth（登录态） / mascot（看板娘配置） / agentContext（页面上下文）
  ▼
api（接口层）
  │  backend.js（axios 实例 + 拦截器） / agent.js（fetch + SSE 解析）
  ▼
Vite 代理（开发环境）
  │  /api → 127.0.0.1:8080      /agent-api → 127.0.0.1:8000
  ▼
Spring Boot / Python Agent
```

### 3.2 同源代理策略

`vite.config.js` 把两个后端都挂到开发服务器的同源路径下：

| 前缀 | 目标 | 处理 |
|------|------|------|
| `/api` | `http://127.0.0.1:8080` | 原样转发，`changeOrigin: true` |
| `/agent-api` | `http://127.0.0.1:8000` | 转发前去掉 `/agent-api` 前缀 |

代理配置中的 `stripOrigin` 会在转发开始时删除 `Origin` 请求头：当其他设备通过主机 IP 访问时，浏览器会把 `Origin` 设为该 IP，后端 CORS 白名单不含它就会返回 403；删除后由后端按服务端请求处理。

---

## 四、路由与权限

| 路径 | 名称 | 组件 | 权限 |
|------|------|------|------|
| `/` | home | `HomeView` | 公开 |
| `/category` | category | `CategoryView` | 公开 |
| `/rank` | rank | `RankView` | 公开 |
| `/schedule` | schedule | `ScheduleView` | 公开 |
| `/search` | search | `SearchView` | 公开 |
| `/login` | login | `LoginView` | 公开 |
| `/register` | register | `RegisterView` | 公开 |
| `/anime/:id` | anime-detail | `AnimeDetailView` | 公开 |
| `/anime/:videoId/play/:sectionId` | anime-play | `PlayView` | 公开 |
| `/user` | user | `UserView` | `requiresAuth` |
| `/user/favorites` | user-favorites | `UserFavoritesView` | `requiresAuth` |
| `/user/history` | user-history | `UserHistoryView` | `requiresAuth` |
| `/settings/mascot` | mascot-settings | `MascotSettingsView` | 公开 |
| `/admin` | — | `AdminLayout` | `requiresAuth` + `requiresAdmin` |
| `/admin/users` | admin-users | `AdminUsersView` | 同上 |
| `/admin/content` | admin-content | `AdminContentView` | 同上 |
| `/admin/records` | admin-records | `AdminRecordsView` | 同上 |
| `/:pathMatch(.*)*` | — | 重定向 `/` | — |

守卫逻辑（`router.beforeEach`）：

- `meta.requiresAuth` 且未登录 → 跳 `/login?redirect=<原路径>`；
- `meta.requiresAdmin` 且 `authState.user.role !== 1` → 跳首页；
- `scrollBehavior` 支持 hash 锚点滚动（顶部偏移 72px）。

所有路由均为**同步导入**（无懒加载拆包）。

---

## 五、核心实现

### 5.1 目录结构

```
frontend/src/
├── main.js                 # 应用入口
├── App.vue                 # 根组件（全局看板娘、布局）
├── api/
│   ├── backend.js          # axios 实例、拦截器、用户/历史/追番/评论/弹幕/通知/管理接口
│   ├── agent.js            # Agent 委托令牌 + SSE 流式客户端
│   ├── anime.js            # 动漫详情/推荐接口
│   ├── home.js             # 首页推荐/周表/排行榜接口
│   ├── section.js          # 章节列表接口
│   └── video.js            # 搜索/分类/播放直链接口
├── router/index.js         # 路由表 + 前置守卫
├── stores/
│   ├── auth.js             # 登录态与 token 读写
│   ├── agentContext.js     # 当前页面上下文（播放/详情）
│   └── mascot.js           # 看板娘模型、缩放、位置与持久化
├── utils/
│   ├── toast.js            # 全局提示封装
│   ├── format.js           # 通用格式化（如时间、数字）
│   ├── datetime.js         # 日期时间处理
│   └── danmaku.js          # 弹幕数据处理
├── views/                  # 页面（含 admin/ 子目录）
├── components/             # 15 个组件：AgentChat、Live2DAvatar、VideoPlayer、CommentSection、EpisodeList、BannerCarousel、WeekSchedule、RankList、RecommendationList、AnimeCard、AnimeGrid、Pagination、AppHeader、AppFooter、AppToast
└── assets/
    └── styles/
        ├── reset.scss      # 样式重置
        ├── variables.scss  # 主题变量（主色、间距等）
        ├── global.scss     # 全局基础样式
        └── admin.scss      # 管理后台样式
```

### 5.2 API 层

`api/backend.js` 创建唯一的 axios 实例：

- `baseURL` 取 `VITE_BACKEND_BASE_URL`，留空即同源（走 Vite 代理）；
- 超时 15 秒，默认 `Accept: application/json`；
- **请求拦截器**：从 `stores/auth` 读取 token，存在则注入 `Authorization: Bearer <token>`；
- **响应拦截器（成功分支）**：识别统一响应体 `{code,msg,data}`，`code === 200` 直接返回 `data`，否则构造带 `code` 的 `Error` 并 reject；`code === 401` 触发统一登出；
- **响应拦截器（失败分支）**：

| 情况 | 提示 |
|------|------|
| `ECONNABORTED` | 请求超时，请稍后重试 |
| 无 `response` | 网络请求失败，请检查后端服务是否已启动 |
| 401 | 清理登录态 + 派发 `lingani:unauthorized` 事件（3 秒内只派发一次） |
| 403 | 没有权限执行该操作 |
| 404 | 请求的内容不存在 |
| 429 | 操作过于频繁，请稍后再试 |
| ≥ 500 | 服务器暂时不可用，请稍后重试 |

接口按模块导出：用户、历史、追番、评论、弹幕、通知、管理后台，页面只调用这些具名函数，不直接使用 axios。

### 5.3 Agent SSE 客户端（`api/agent.js`）

**第一步：换取委托令牌** `createAgentContext()`

```js
if (!getToken()) throw new Error('请先登录后再使用小灵')
const data = await backend.post('/api/internal/agent/context')
return data.token
```

复用 axios 实例，因此自动携带 USER JWT 并沿用统一错误处理；每次对话前都会重新申请（令牌 5 分钟有效）。

**第二步：流式对话** `streamAgentChat({ message, threadId, context, signal, onEvent })`

```js
const response = await fetch(`${AGENT_BASE_URL}/api/agent/chat/stream`, {
  method: 'POST',
  headers: { Accept: 'text/event-stream', 'Content-Type': 'application/json',
             Authorization: `Bearer ${contextToken}` },
  body: JSON.stringify({ message, thread_id: threadId || null, context }),
  signal
})
```

- 使用 `fetch` + `ReadableStream` 而非 `EventSource`，因为需要 POST 与自定义请求头；
- `TextDecoder({stream:true})` 增量解码，缓冲区按 `\n\n` 切分事件块；
- `parseEvent` 逐行识别 `event:` 与 `data:`，`data` 做 `JSON.parse`，失败则丢弃该块；
- 回调 `onEvent(event, data)` 后，若事件为 `done` 或 `error` 立即 `reader.cancel()` 并返回；
- 非 2xx 时尝试读取 JSON 错误体的 `msg`，否则回退为 `AI 服务请求失败（HTTP xxx）`。

事件契约（与 Agent 服务一致）：`meta`、`token`、`tool`、`done`、`error`。

### 5.4 聊天组件 AgentChat

消息渲染：

- 助手回复经 `marked.parse` 转 HTML，再用 `DOMPurify.sanitize` 清洗后 `v-html` 渲染；
- `works` 中的作品标题通过 `linkifyAnimeTitles` 建立 `标题 → videoId` 映射，在已渲染 DOM 中把同名标题包成站内链接（`/anime/{id}`），因此**标题必须与渲染文本完全一致**才能变蓝；
- 未被正文提到（无法内联）的 `works` 通过 `orphanWorks` 以卡片形式单独展示。

流式与状态：

- 每轮对话先插入一条空的助手消息，随后增量拼接 `token` 文本；
- `tool` 事件把工具名映射为中文状态（`TOOL_LABELS`），显示"正在查询……"，下一个 `token` 到来时清除；
- `done` 事件写入 `works` / `actions`，并调用 `autoNavigate`：取第一个 `navigate` 类型动作执行路由跳转；
- 使用 `AbortController` 支持中断；异常时在同一条消息上展示错误文案。

其他：底部提供 `quickPrompts` 快捷提问；输入框与发送按钮在加载中禁用。

### 5.5 页面上下文（陪伴模式）

`stores/agentContext.js` 只保存一个 `ref`：

```js
export const agentPageContext = ref(null)
export function setAgentPageContext(context) { agentPageContext.value = context }
export function clearAgentPageContext() { agentPageContext.value = null }
```

- 进入番剧详情页时写入 `{ page: 'detail', current_video_id }`；
- 进入播放页时写入 `{ page: 'play', current_video_id, current_section_id, progress }`，`progress` 随播放进度更新；
- 离开页面时清空；
- 发送消息时把该对象作为 `context` 字段传给 Agent，仅用于让「小灵」理解用户所在位置，**不参与身份认证**。

### 5.6 Live2D 看板娘

- `Live2DAvatar.vue` 负责加载 Live2D 运行时脚本、校验模型配置、调用 `loadlive2d` 初始化；容器尺寸或模型切换后重新加载；
- `stores/mascot.js` 管理：可用模型列表、自定义模型、缩放范围、位置边界、默认位置计算，并通过 `localStorage` 持久化；
- 拖动后同步位置到 store，刷新后恢复；
- 模型资源位于 `public/live2d/model/`，配置页 `/settings/mascot` 提供可视化调整。

### 5.7 播放器

`VideoPlayer.vue` 封装 ArtPlayer：

| 配置 | 值 |
|------|------|
| 主题色 | `#f0787f` |
| 初始音量 | 0.6 |
| 自动播放 | 关闭 |
| 功能 | 设置、倍速、全屏、网页全屏、画中画、AirPlay、热键、迷你进度条 |
| 弹幕插件 | 速度 5、不透明度 0.92、字号 22、防重叠开启 |

事件与交互：

- `ready`：若传入 `initialTime > 0` 则跳转到断点，并保证不超过 `duration - 2` 秒；
- `video:timeupdate`：向上抛出 `{time, duration}`，由播放页节流后上报历史接口；
- `video:error`：展示"视频加载失败，可能是直链已过期或网络异常"，并提供重试按钮（触发 `retry` 事件重新拉取播放直链）；
- `video:ended`：抛出 `ended`，播放页可自动切下一集；
- 支持通过按钮显示/隐藏弹幕。

### 5.8 样式

- 使用 SCSS，组件内 `<style scoped>` 为主；
- 主色 `#f0787f`（播放器主题、强调色、按钮）；
- 全局样式集中定义变量与基础排版，页面通过媒体查询适配移动端栅格。

---

## 六、环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `VITE_BACKEND_BASE_URL` | 空 | 后端地址，留空表示同源（走 Vite 代理） |
| `VITE_AGENT_BASE_URL` | `/agent-api` | Agent 地址前缀，开发期由代理转发到 8000 |

生产部署时若前后端不同源，需要同时配置这两个变量，并在后端 `CORS_ALLOWED_ORIGINS` 中加入前端域名。

---

## 七、端口与代理

| 服务 | 端口 | 前端访问方式 |
|------|------|------|
| Vite 开发服务器 | 5173 | `http://localhost:5173` |
| Spring Boot 后端 | 8080 | 经 `/api` 代理 |
| Python Agent | 8000 | 经 `/agent-api` 代理 |

`npm run dev` 使用 `--host 0.0.0.0`，局域网设备可通过主机 IP 访问；由于代理会剥离 `Origin`，这些请求不会被后端 CORS 拦截。

---

## 八、构建与启动

```bash
cd frontend

npm install
npm run dev        # 开发：http://localhost:5173

npm run build      # 产物输出到 dist/
```

启动顺序建议：MySQL → 后端 8080 → Agent 8000 → 前端 5173。

---

## 九、关键文件路径

| 文件 | 作用 |
|------|------|
| `vite.config.js` | 别名 `@`、开发代理、Origin 剥离 |
| `src/router/index.js` | 路由表与权限守卫 |
| `src/api/backend.js` | axios 实例、拦截器、全部业务接口 |
| `src/api/agent.js` | 委托令牌获取 + SSE 流式解析 |
| `src/stores/auth.js` | 登录态与 token 存储 |
| `src/stores/agentContext.js` | 页面上下文 |
| `src/stores/mascot.js` | 看板娘配置与持久化 |
| `src/components/AgentChat.vue` | 聊天 UI、流式渲染、作品内联链接、动作跳转 |
| `src/components/Live2DAvatar.vue` | Live2D 加载与渲染 |
| `src/components/VideoPlayer.vue` | ArtPlayer + 弹幕封装 |
| `src/views/PlayView.vue` | 播放页：直链拉取、进度上报、页面上下文 |
| `src/views/AnimeDetailView.vue` | 详情页：章节、追番、评论 |
| `src/views/MascotSettingsView.vue` | 看板娘设置页 |

---

## 十、已知限制

| 限制 | 说明 |
|------|------|
| 无 TypeScript | 全量 JavaScript，缺少类型约束 |
| 无状态管理持久化（聊天） | 对话消息保存在 `AgentChat` 组件内，切换页面或刷新即丢失 |
| 无单元测试 | `package.json` 中没有测试脚本与测试依赖 |
| 路由未懒加载 | 所有页面同步导入，首屏包体偏大 |
| 无 ESLint / Prettier 配置 | 代码风格依赖约定 |
| 作品内联链接依赖标题完全一致 | Agent 返回的 `title` 与正文文字必须逐字相同，否则退化为独立卡片 |
| 弹幕与评论无本地缓存 | 每次进入页面都会重新请求 |
