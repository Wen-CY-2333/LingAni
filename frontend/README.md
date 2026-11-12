# 灵漫 LingAni 前端

基于 Vue 3 + Vite 的灵漫前端项目，包含首页推荐、追番周表、排行榜、番剧详情、在线播放（ArtPlayer + 弹幕），以及账号、追番、播放历史、评论等用户功能。

> 番剧元数据与播放地址来自公开接口，用户数据由本地 `backend` 服务保存。

## 技术栈

- Vue 3（`<script setup>`）+ Vue Router
- Vite
- Sass（SCSS）
- Axios
- ArtPlayer + artplayer-plugin-danmuku
- lucide-vue-next（图标）

## 功能

### 首页

- Banner 轮播：封面、评分、标签、指示器、左右切换、自动轮播（悬停暂停）
- 首页推荐：按接口分组渲染「TV番组」「剧场版」卡片网格
- 追番周表：周一至周日切换，默认选中今天，展示当日更新番剧
- 排行榜：按榜单分类切换，展示排名、评分与播放量

### 分类

- TV番组 / 剧场版分区切换，年份与题材筛选，分页浏览
- 筛选条件同步到 URL query，刷新/分享链接保持状态

### 番剧详情

- 封面、标题、别名、标签、评分、更新信息、播放量
- 简介展开/收起
- 选集（正序/倒序切换）与相关推荐
- 追番按钮：登录后可加入/取消追番，游客点击会跳转登录
- 评论区：分页展示、发表评论、一级回复、编辑/删除自己的评论；游客可浏览，发表时提示登录

### 播放页

- 使用章节接口获取真实视频直链，ArtPlayer 播放
- 弹幕：从灵漫后端按章节加载，支持开关；弹幕失败不影响播放
- 弹幕发送：登录后可选择颜色与模式发送，支持查看并删除自己发送的弹幕
- 播放进度：登录状态下每 15 秒及离开页面时保存到后端；从播放历史进入可通过 `?t=` 续播
- 选集切换：先保存上一章节进度，再更新播放地址并加载新章节弹幕
- 播放控制：进度、音量、倍速、画中画、网页全屏/全屏
- 直链失效、Token 缺失、接口异常均有明确提示与重试
- 评论区：分页展示、发表评论、一级回复、编辑/删除自己的评论；游客可浏览，发表时提示登录

### 账号与个人中心

- 注册、登录、退出登录；JWT 保存在 localStorage
- 个人中心：查看资料、修改昵称/头像、修改密码
- 我的追番：分页列表、查看详情、取消追番
- 播放历史：分页列表、进度条、继续观看、删除单条、清空历史

## 目录结构

```
frontend/
├── public/
│   ├── images/                # 封面占位图
│   └── fonts/                 # 品牌展示字体（站酷快乐体子集，仅含「灵漫 LingAni」用字）
├── src/
│   ├── api/                   # 接口封装（request / home / anime / video / section / backend）
│   ├── assets/styles/         # 主题变量、reset、全局样式
│   ├── components/            # Header、Footer、卡片、轮播、周表、榜单、选集、分页、播放器、评论、Toast
│   ├── router/                # 路由与登录守卫
│   ├── stores/                # 轻量登录状态（localStorage + reactive）
│   ├── utils/                 # 格式化、时间、Toast、弹幕数据转换
│   ├── views/                 # Home / Category / Rank / Schedule / Search / AnimeDetail / Play / Login / Register / User
│   ├── App.vue
│   └── main.js
├── .env.example               # 环境变量示例
└── vite.config.js
```

## 路由

| 路径                                | 页面             | 登录要求 |
| --------------------------------- | -------------- | ---- |
| `/`                               | 首页             | 否    |
| `/category`                       | 分类             | 否    |
| `/rank`                           | 排行榜            | 否    |
| `/schedule`                       | 追番周表           | 否    |
| `/search?q=`                      | 搜索             | 否    |
| `/anime/:id`                      | 番剧详情           | 否    |
| `/anime/:videoId/play/:sectionId` | 播放页            | 否    |
| `/login`                          | 登录             | 否    |
| `/register`                       | 注册             | 否    |
| `/user`                           | 个人中心           | 是    |
| `/user/favorites`                 | 我的追番           | 是    |
| `/user/history`                   | 播放历史           | 是    |
| `/admin`                          | 管理后台 · 数据总览    | 管理员  |
| `/admin/users`                    | 管理后台 · 用户管理    | 管理员  |
| `/admin/content`                  | 管理后台 · 评论与弹幕审核 | 管理员  |
| `/admin/records`                  | 管理后台 · 追番与历史   | 管理员  |

未登录访问 `/user*` 会自动跳转登录页，并携带 `redirect` 参数，登录后回到原页面。`/admin*` 额外要求账号 `role = 1`，普通用户访问会被重定向回首页；管理员登录后，导航栏用户菜单会出现「管理后台」入口。

## 页面布局说明

- 页面主体统一包裹在 `page-wrap` 中（最大宽度 1600px），内容左右留白由 `.container` 的 `--container-padding` 控制（桌面 40px / 平板 28px / 手机 18px）。
- 首页 Banner 通过负外边距跳出 `page-wrap`，通栏铺满视口；其余内容仍在 wrap 内。
- 顶栏固定悬浮：处于首页顶部时透明并叠加自上而下的渐变遮罩，Banner 延伸至顶栏下方；滚动后切换为深色毛玻璃背景。
- 顶栏右侧为登录/注册入口或用户头像菜单（个人中心、我的追番、播放历史、退出登录）。

## 环境变量

复制 `.env.example` 为 `.env`：

```bash
cp .env.example .env
```

| 变量                      | 说明                                |
| ----------------------- | --------------------------------- |
| `VITE_BACKEND_BASE_URL` | 灵漫后端地址，默认 `http://localhost:8080` |

说明：

- 动漫列表、搜索、详情、章节、相关推荐、排行榜、周表、首页数据全部由灵漫后端代理（`/api/cycani/**`），前端不再直连 CycAni，也不需要任何第三方请求头或 Token。
- 播放地址由灵漫后端代理获取（`GET /api/cycani/play/{sectionId}`），第三方播放源 Token 只保存在后端 `.env` 的 `CYCANI_TOKEN` 中，前端产物不再包含任何 Token。
- 账号、历史、追番、评论、弹幕全部请求 `VITE_BACKEND_BASE_URL`，请求头自动携带 `Authorization: Bearer <token>`。
- `.env` 已在 `.gitignore` 中忽略，请勿把真实 Token 提交到仓库。

## 启动

### 1. 启动后端

```bash
cd backend
mysql -u root -p -e "source src/main/resources/sql/init.sql"
cp .env.example .env
# 编辑 .env 填写数据库密码、JWT 密钥、CycAni Token 等配置
mvn spring-boot:run
```

后端默认监听 `http://localhost:8080`，详细说明见 [`backend/README.md`](../backend/README.md)。

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认访问 `http://localhost:5173`（端口被占用时 Vite 会自动递增）。请确保 `VITE_BACKEND_BASE_URL` 指向已启动的后端，否则账号、评论等功能会提示网络失败。

生产构建：

```bash
npm run build
npm run preview
```

## 接口与跨域

- 番剧元数据请求灵漫后端 `/api/cycani/**`，由后端统一访问 CycAni；视频直链通过后端 `/api/cycani/play/{sectionId}` 代理获取。
- 用户相关接口请求 `VITE_BACKEND_BASE_URL`；后端只放行 `CORS_ALLOWED_ORIGINS` 中配置的前端来源，本地开发默认允许 `http://localhost:5173`。
- 后端统一返回 `{ code, msg, data }`，前端拦截器统一处理 401（清理登录态并提示重新登录）、403、404、429、500 与网络异常。
