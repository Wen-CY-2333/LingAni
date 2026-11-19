# 灵漫（LingAni）后端 — 技术实现文档

> 本文档整理了灵漫动漫平台 Spring Boot 后端的完整技术实现，覆盖认证体系、业务模块、动漫数据代理、定时任务与配置部署，便于理解现状与后续维护。

---

## 一、项目概述

后端是灵漫平台的唯一数据出口，承担六类职责：

1. **用户体系**：注册、登录、注销、资料与密码修改、账号状态与角色管理；
2. **个人数据**：播放历史（断点续播）、追番收藏、站内通知；
3. **社区互动**：番剧评论（含楼中楼）、弹幕发送与查询；
4. **动漫元数据代理**：统一代理第三方 CycAni 接口，屏蔽其内部协议；
5. **管理后台**：数据统计、用户/评论/弹幕/追番/历史的查询与审核；
6. **Agent 身份签发**：为 Python AI Agent 签发短期、限定 audience 的委托令牌。

---

## 二、技术栈与依赖

| 层 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 框架 | Spring Boot | 4.1.2 | Web、依赖注入、自动配置 |
| 语言 | Java | 17 | `instanceof` 模式匹配等新语法 |
| 持久层 | MyBatis-Plus | 3.5.18.1 | Lambda Wrapper、分页插件、逻辑删除 |
| 数据库 | MySQL | 8.x | 业务数据存储 |
| 连接池 | HikariCP | 内置 | 最大 10 连接 |
| 认证 | jjwt | 0.12.6 | USER JWT / AGENT JWT |
| 安全 | Spring Security | 内置 | 无状态过滤链、方法级鉴权 |
| 校验 | spring-boot-starter-validation | 内置 | `@Valid` 参数校验 |
| 文档 | SpringDoc OpenAPI | 3.2.0 | Swagger UI |
| 工具 | Lombok | 内置 | 构造注入、日志 |

打包方式为 jar，主类 `com.lingani.LingAniApplication`。

---

## 三、系统架构

### 3.1 上下游关系

```
        ┌──────────────────┐        ┌──────────────────┐
        │ 前端 Vue :5173    │        │ Python Agent      │
        │（同源代理 /api）  │        │ :8000             │
        └────────┬─────────┘        └────────┬─────────┘
                 │ Authorization: Bearer       │ X-Agent-JWT
                 │ <USER JWT>                  │ （仅历史/收藏接口）
                 ▼                             ▼
        ┌───────────────────────────────────────────────┐
        │           Spring Boot 后端 :8080               │
        │  Controller → Service → Mapper → MySQL :3306   │
        │                     │                          │
        │                     ▼                          │
        │              CycAniClient → 第三方动漫接口      │
        └───────────────────────────────────────────────┘
```

### 3.2 请求处理链路

```
HTTP 请求
  │
  ├─ CORS 过滤器（CorsConfigurationSource，允许来源来自 lingani.cors.allowed-origins）
  │
  ├─ UserJwtAuthenticationFilter（解析 Authorization: Bearer）
  │    验签 → 黑名单校验(jti) → 查用户 → 状态校验 → token_version 校验 → 写入 SecurityContext
  │
  ├─ AgentJwtAuthenticationFilter（仅 /api/history/**、/api/favorites/**）
  │    解析 X-Agent-JWT → 验签 → 查用户 → 写入 SecurityContext
  │
  ├─ Security 授权规则（permitAll 白名单 / authenticated）
  │
  ├─ Controller（@Valid 参数校验）→ Service（事务、归属校验）
  │
  ├─ Mapper（MyBatis-Plus Lambda Wrapper）→ MySQL
  │
  ├─ 全局异常处理（GlobalExceptionHandler）→ 统一 Result
  │
  └─ 响应 {code, msg, data}
```

---

## 四、核心实现

### 4.1 目录结构

```
backend/src/main/java/com/lingani/
│
├── LingAniApplication.java          # 启动类
│
├── common/                           # 通用基础设施
│   ├── Result.java                   # 统一响应体 {code,msg,data}
│   ├── PageResult.java               # 分页响应体 {records,total,current,size}
│   ├── PageUtils.java                # 分页参数归一化
│   ├── BizException.java             # 业务异常（含 401/403/404/409/429 工厂方法）
│   └── GlobalExceptionHandler.java   # 全局异常 → 统一 Result
│
├── config/                           # 配置类
│   ├── SecurityConfig.java           # 过滤链、放行矩阵、CORS
│   ├── MybatisPlusConfig.java        # MyBatis-Plus 分页插件
│   ├── OpenApiConfig.java            # Swagger / SpringDoc
│   ├── UserJwtProperties.java        # USER JWT 配置绑定
│   ├── AgentJwtProperties.java       # AGENT JWT 配置绑定
│   ├── CycaniProperties.java         # CycAni 第三方接口配置
│   └── CorsProperties.java           # CORS 允许来源
│
├── controller/                       # 接口层（9 个）
│   ├── AuthController.java           # 注册/登录/注销 + 当前用户信息/资料/密码
│   ├── CycaniController.java         # 动漫数据代理（公开）
│   ├── HistoryController.java        # 播放历史
│   ├── FavoriteController.java       # 追番
│   ├── CommentController.java        # 评论
│   ├── DanmakuController.java        # 弹幕
│   ├── NotificationController.java   # 站内通知
│   ├── AgentContextController.java   # AGENT JWT 签发
│   └── AdminController.java          # 管理后台
│
├── service/                          # 业务层（11 个）
│   ├── AuthService.java              # 登录/注册/注销
│   ├── UserService.java              # 用户资料、改密码、token_version
│   ├── CycaniService.java            # 第三方协议归一化
│   ├── HistoryService.java           # 播放进度 upsert、封面补齐
│   ├── FavoriteService.java          # 追番增删查、查重
│   ├── CommentService.java           # 评论 CRUD
│   ├── DanmakuService.java           # 弹幕发送/查询
│   ├── DanmakuRateLimitService.java  # 弹幕发送限流（内存）
│   ├── LoginAttemptService.java      # 登录失败限流（内存）
│   ├── NotificationService.java      # 通知列表/已读
│   └── AdminService.java             # 管理后台统计与审核
│
├── security/                         # 认证与安全
│   ├── UserJwtService.java           # USER JWT 签发与校验
│   ├── AgentJwtService.java          # AGENT JWT 签发与校验
│   ├── UserJwtAuthenticationFilter.java     # USER JWT 过滤器
│   ├── AgentJwtAuthenticationFilter.java    # AGENT JWT 过滤器（仅历史/收藏）
│   ├── TokenBlacklistService.java    # 注销令牌黑名单（内存 ConcurrentHashMap）
│   ├── SecurityUtils.java            # 当前用户 ID 取数工具
│   ├── LoginUser.java                # 登录用户 Principal
│   └── RestAuthHandlers.java         # 认证失败 JSON 响应
│
├── client/                           # 第三方客户端
│   └── CycaniClient.java             # 调用 CycAni HTTP 接口
│
├── entity/                           # 数据库实体（6 个，对应 6 张表）
│   ├── SysUser.java
│   ├── UserHistory.java
│   ├── UserFavorite.java
│   ├── AnimeComment.java
│   ├── AnimeDanmaku.java
│   └── UserNotification.java
│
├── mapper/                           # MyBatis-Plus Mapper（6 个）
│   ├── SysUserMapper.java
│   ├── UserHistoryMapper.java
│   ├── UserFavoriteMapper.java
│   ├── AnimeCommentMapper.java
│   ├── AnimeDanmakuMapper.java
│   └── UserNotificationMapper.java
│
├── dto/                              # 请求体
│   ├── LoginRequest.java  RegisterRequest.java  UpdateProfileRequest.java
│   ├── UpdatePasswordRequest.java  HistorySaveRequest.java
│   ├── FavoriteAddRequest.java  CommentCreateRequest.java
│   ├── CommentUpdateRequest.java  DanmakuCreateRequest.java
│   └── AdminStatusRequest.java
│
├── vo/                               # 返回视图
│   ├── UserVO.java  LoginVO.java  AgentContextVO.java
│   ├── CommentVO.java  DanmakuVO.java  FavoriteCheckVO.java
│   └── admin/ (AdminStatsVO / AdminUserVO / AdminCommentVO /
│                AdminDanmakuVO / AdminFavoriteVO / AdminHistoryVO)
│
└── job/                              # 定时任务
    └── FavoriteUpdateNotifier.java   # 追番更新提醒（每小时）
```

> 共 9 个 Controller、11 个 Service、6 个 Entity、6 个 Mapper、8 个配置类、8 个安全类。

### 4.2 数据库设计

共 6 张表（`src/main/resources/sql/init.sql`）：

| 表 | 实体 | 用途 | 关键约束 |
|------|------|------|------|
| `sys_user` | `SysUser` | 用户账号 | 主键 id；`username` 唯一索引；`status`（1 正常）、`role`（1 管理员）、`token_version`（令牌失效版本号） |
| `user_history` | `UserHistory` | 播放历史 | 唯一键 `(user_id, section_id)`；按 `last_played_at` 倒序查询；封面不入库 |
| `user_favorite` | `UserFavorite` | 追番收藏 | 唯一键 `(user_id, video_id)`；按 `created_at` 倒序查询 |
| `anime_comment` | `AnimeComment` | 番剧评论 | `video_id` + `parent_id` 支持楼中楼；`status` 审核状态 |
| `anime_danmaku` | `AnimeDanmaku` | 弹幕 | `section_id`、`time`（秒）、`color`、`mode`；按 section 查询 |
| `user_notification` | `UserNotification` | 站内通知 | 唯一索引 `(user_id, video_id, biz_date)` 保证同一天同一作品只提醒一次 |

MyBatis-Plus 全局配置：

- `map-underscore-to-camel-case: true`（下划线字段自动映射驼峰属性）
- 逻辑删除：`deleted` 字段，1 已删 / 0 未删
- SQL 日志：`StdOutImpl`

### 4.3 认证与双 JWT

后端同时维护两套令牌，职责严格分离：

| | USER JWT | AGENT JWT |
|------|----------|-----------|
| 性质 | 长期身份凭据 | 短期委托凭据 |
| 签发时机 | 登录成功 | 前端请求 `/api/internal/agent/context` 时 |
| 携带方式 | `Authorization: Bearer` | `X-Agent-JWT` |
| 密钥 | `lingani.jwt.user.secret`（`USER_JWT_SECRET`） | `lingani.jwt.agent.secret`（`AGENT_JWT_SECRET`） |
| issuer / audience | `lingani` / 无 | `lingani-backend` / `lingani-agent` |
| TTL | 7 天（`604800` 秒） | 5 分钟（`300` 秒） |
| 使用方 | 前端全部受保护接口 | Python Agent 回调个人数据接口 |

**USER JWT 校验链**（`UserJwtAuthenticationFilter`）：

1. 读取 `Authorization: Bearer <token>`，为空则跳过（保持匿名）；
2. `userJwtService.verify()` 验签；
3. `TokenBlacklistService.isRevoked(claims.getId())` 检查 jti 是否已注销；
4. `sub` 解析为用户 ID，查 `sys_user`；
5. 用户不存在或 `status != 1` 则不建立身份；
6. `tv`（token version）与库中 `token_version` 不一致则不建立身份——改密码或强制下线时递增该字段即可让旧令牌全部失效；
7. 写入 `LoginUser` 与权限：普通用户 `ROLE_USER`，管理员额外 `ROLE_ADMIN`。

**AGENT JWT 校验链**（`AgentJwtAuthenticationFilter`）：

1. 仅当请求路径匹配 `/api/history/**` 或 `/api/favorites/**` 时处理，避免任意请求头伪造身份；
2. 读取 `X-Agent-JWT`；
3. 校验签名、`iss`、`aud`、`exp`，并要求 `scope=agent`；
4. `sub` 必须是纯数字用户 ID，查库校验用户存在且状态正常；
5. 写入 `SecurityContext`，后续 Service 通过 `SecurityUtils.currentUserId()` 取用户。

**委托令牌签发**（`AgentContextController`）：`POST /api/internal/agent/context` 在用户身份已建立的前提下签发令牌，返回 `{token, ttlSeconds}`；前端拿到后仅用于 Agent 调用。

### 4.4 Spring Security 配置

`SecurityConfig` 的关键设置：

- CSRF 关闭、Session 无状态（`STATELESS`）、禁用 formLogin 与 httpBasic；
- 认证/鉴权失败统一走 `RestAuthHandlers`，返回 JSON 而非重定向；
- 过滤器顺序：`UserJwtAuthenticationFilter` 在 `UsernamePasswordAuthenticationFilter` 之前，`AgentJwtAuthenticationFilter` 紧随其后；
- 方法级鉴权开启（`@EnableMethodSecurity`）。

放行矩阵：

| 路径 | 方法 | 说明 |
|------|------|------|
| `/**` | OPTIONS | 预检请求 |
| `/api/auth/register`、`/api/auth/login` | POST | 注册与登录 |
| `/v3/api-docs/**`、`/swagger-ui/**` | GET | 接口文档 |
| `/api/comments/**` | GET | 匿名查看评论 |
| `/api/danmaku/**` | GET | 匿名查看弹幕 |
| `/api/cycani/**` | GET | 公开动漫数据 |
| `/error` | — | 错误转发 |
| 其余 | 任意 | 必须认证 |

CORS：

- 允许来源：`lingani.cors.allowed-origins`（默认 `http://localhost:5173,http://127.0.0.1:5173`）；
- 允许方法：GET / POST / PUT / DELETE / OPTIONS；
- 允许请求头：`Authorization`、`Content-Type`、`Accept`、`X-App-Name`；
- `allowCredentials=false`、预检缓存 3600 秒。

> 前端开发服务器会主动删除代理请求的 `Origin` 头，避免通过主机 IP 访问时被判定为跨域 403。

### 4.5 统一响应、分页与异常

统一响应体：

```json
{ "code": 200, "msg": "success", "data": {} }
```

`Result.success(data)` 固定返回 `code=200`，业务失败通过 `BizException(code, msg)` 抛出。

`PageUtils` 对分页参数做归一化（`current ≥ 1`、`size` 有上下限），并统一组装 `PageResult{records, total, current, size}`。

全局异常映射：

| 异常 | HTTP 状态 | 返回 code / msg |
|------|-----------|----------------|
| `BizException` | 按异常 code 解析，无法解析则 400 | 原始 code + msg |
| `MethodArgumentNotValidException` | 400 | 首个字段错误信息 |
| `ConstraintViolationException` | 400 | 首个约束违反信息 |
| 请求体不可读 / 参数类型不匹配 / 缺少参数 | 400 | "参数格式错误" |
| `HttpRequestMethodNotSupportedException` | 405 | "请求方法不被支持" |
| `NoResourceFoundException` | 404 | "请求的资源不存在" |
| `DuplicateKeyException` | 409 | "数据已存在，请勿重复操作" |
| `AccessDeniedException` | 403 | "没有权限执行该操作" |
| 其他 `Exception` | 500 | "服务器内部错误，请稍后重试"（同时记录完整堆栈） |

### 4.6 动漫数据代理层

`CycaniClient` 负责 HTTP 调用，`CycaniService` 负责协议归一化，两者配合把第三方接口改造成前端与 Agent 都能直接消费的结构。

**协议归一化**：CycAni 原生返回 `{list: [], pager: {page, page_size, total}}` 或仅 `{list: []}`，`CycaniService.toPageFormat` 统一转换为：

```json
{ "records": [], "total": 0, "current": 1, "size": 24 }
```

单对象接口（详情、播放直链）原样透传。

**参数约束**：

- 分页：`page` 最小 1；`page_size` 上限 50，缺省按接口取 24 / 48 / 12；
- ID：`requireNumericId` 要求 `^\d{1,20}$`，非法直接抛业务异常；
- 章节接口 `player_code` 默认 `cychub`；
- 广告位 `position` 默认 `banner`。

**播放直链安全**：`getPlayUrl` 使用后端配置的 `CYCANI_TOKEN` 调用第三方 `/api/v2/sections/{id}/play-url`，Token 只存在服务端，不下发给前端；未配置时抛 503 并提示在 `.env` 中补全。

### 4.7 播放历史与追番

**历史记录（`HistoryService.save`）** 采用「先查后写 + 唯一键兜底」的 upsert：

1. 以 `(user_id, section_id)` 查询已有记录；
2. 存在则更新进度与最后播放时间；
3. 不存在则插入；并发插入触发 `DuplicateKeyException` 时重新查询并退化为更新；
4. `progress > duration` 时按 `duration` 截断，避免异常进度。

查询侧：

- 列表按 `last_played_at`、`id` 倒序；
- 封面不落库（第三方地址会过期），返回前用 `CycaniService.getVideoDetail` 实时补齐，第三方异常时静默跳过、前端显示占位图；
- 删除单条时校验归属，不允许删除他人记录；清空仅作用于当前用户。

**追番（`FavoriteService`）**：

- 添加前先按 `(user_id, video_id)` 查重，已存在返回 409「该动画已在追番列表中」；
- 并发插入由唯一索引兜底，同样映射为 409；
- 列表返回前用 CycAni 详情补齐封面与标题（以第三方最新标题为准）；
- `/api/favorites/check` 返回 `{favorited, favoriteId}`，供详情页按钮状态使用。

### 4.8 评论与弹幕

- 评论支持 `parent_id` 楼中楼；创建/编辑/删除均校验归属，管理员通过 `@PreAuthorize` 或管理接口处理审核；
- 弹幕按 `section_id` 查询，发送时记录 `time`、`color`、`mode`；
- `DanmakuRateLimitService` 对发送频率做限制，超过阈值返回 429 语义的业务异常；
- `LoginAttemptService` 对登录失败次数做限制，防止暴力破解。

### 4.9 通知与定时任务

`FavoriteUpdateNotifier` 每小时扫描一次当天更新的追番：

```
@Scheduled(initialDelay = 30s, fixedDelay = 1h)
  → 取 CycAni 周表，筛选 weekday == 今天
  → 查所有相关 user_favorite
  → 查当天已存在的通知（user_id + video_id + biz_date）
  → 未提醒过的插入 user_notification（type=favorite_update）
  → 唯一索引兜底并发/重复扫描
```

通知接口提供分页列表、未读数、单条已读、全部已读。

### 4.10 管理后台

`AdminController` 统一前缀 `/api/admin`，全部要求管理员角色：

- 统计：`GET /stats`
- 用户：`GET /users`、`PUT /users/{id}/status`
- 评论：`GET /comments`、`PUT /comments/{id}/status`、`DELETE /comments/{id}`
- 弹幕：`GET /danmaku`、`PUT /danmaku/{id}/status`、`DELETE /danmaku/{id}`
- 记录：`GET /favorites`、`GET /history`

---

## 五、接口清单

### 认证与用户（`/api`）

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/auth/register` | 公开 | 注册 |
| POST | `/auth/login` | 公开 | 登录，返回 USER JWT |
| POST | `/auth/logout` | 登录 | 注销（令牌加入黑名单） |
| GET | `/user/me` | 登录 | 当前用户信息 |
| PUT | `/user/profile` | 登录 | 修改资料 |
| PUT | `/user/password` | 登录 | 修改密码（递增 token_version） |

### 动漫数据（`/api/cycani`，全部公开 GET）

| 路径 | 说明 |
|------|------|
| `/videos` | 分类列表（zoneId / year / tag / 分页） |
| `/videos/search` | 关键词搜索（q / 分页） |
| `/videos/{id}` | 作品详情 |
| `/videos/{id}/sections` | 章节列表 |
| `/videos/{id}/recommendations` | 相关推荐 |
| `/ranks` | 榜单列表 |
| `/ranks/{id}/videos` | 指定榜单内容 |
| `/index/weekday` | 追番周表 |
| `/index/recommend` | 首页推荐 |
| `/adverts` | 广告位（position） |
| `/play/{sectionId}` | 播放直链（服务端注入 Token） |

### 个人数据

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/api/history` | 登录 / Agent | 保存播放进度（upsert） |
| GET | `/api/history` | 登录 / Agent | 历史分页 |
| GET | `/api/history/video/{videoId}` | 登录 / Agent | 单部作品的观看记录 |
| DELETE | `/api/history/{id}` | 登录 / Agent | 删除单条 |
| DELETE | `/api/history` | 登录 / Agent | 清空 |
| POST | `/api/favorites` | 登录 / Agent | 添加追番 |
| GET | `/api/favorites` | 登录 / Agent | 追番分页 |
| GET | `/api/favorites/check` | 登录 / Agent | 是否已追番 |
| DELETE | `/api/favorites/{id}` | 登录 / Agent | 取消追番 |

### 社区

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/api/comments` | 公开 | 评论分页 |
| POST | `/api/comments` | 登录 | 发表评论 |
| PUT | `/api/comments/{id}` | 登录 | 编辑评论 |
| DELETE | `/api/comments/{id}` | 登录 | 删除评论 |
| GET | `/api/danmaku` | 公开 | 按 sectionId 拉取弹幕 |
| POST | `/api/danmaku` | 登录 | 发送弹幕（限流） |
| DELETE | `/api/danmaku/{id}` | 登录 | 删除弹幕 |

### 通知

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/api/notifications` | 登录 | 通知分页 |
| GET | `/api/notifications/unread-count` | 登录 | 未读数 |
| POST | `/api/notifications/{id}/read` | 登录 | 标记已读 |
| POST | `/api/notifications/read-all` | 登录 | 全部已读 |

### Agent 内部

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/api/internal/agent/context` | USER JWT | 签发 AGENT JWT，返回 `{token, ttlSeconds}` |

### 管理后台（`/api/admin`，需管理员）

`GET /stats`、`GET /users`、`PUT /users/{id}/status`、`GET /comments`、`PUT /comments/{id}/status`、`DELETE /comments/{id}`、`GET /danmaku`、`PUT /danmaku/{id}/status`、`DELETE /danmaku/{id}`、`GET /favorites`、`GET /history`。

---

## 六、配置说明

`application.yml` 从 `backend/.env` 导入敏感配置（`spring.config.import: optional:file:./.env[.properties]`）。带环境变量的配置项支持「环境变量 → 默认值」两级回退，标注 `—` 的为固定值，直接写在 yml 中。

| 配置项 | 环境变量 | 值 | 说明 |
|--------|----------|-----|------|
| `server.port` | — | `8080` | 服务端口 |
| `spring.datasource.url` | `DB_URL` | `jdbc:mysql://localhost:3306/lingani?...` | 数据库连接 |
| `spring.datasource.username` / `password` | `DB_USERNAME` / `DB_PASSWORD` | `root` / 空 | 数据库账号 |
| `lingani.jwt.user.secret` | `USER_JWT_SECRET` | 本地开发占位值 | USER JWT 密钥 |
| `lingani.jwt.user.issuer` | — | `lingani` | USER JWT 签发方 |
| `lingani.jwt.user.ttl-seconds` | — | `604800` | 7 天 |
| `lingani.jwt.agent.secret` | `AGENT_JWT_SECRET` | 空（必须配置） | **必须与 Agent 服务一致** |
| `lingani.jwt.agent.issuer` | — | `lingani-backend` | 需与 Agent 侧一致 |
| `lingani.jwt.agent.audience` | — | `lingani-agent` | 需与 Agent 侧一致 |
| `lingani.jwt.agent.ttl-seconds` | — | `300` | 5 分钟 |
| `lingani.cors.allowed-origins` | — | `http://localhost:5173,http://127.0.0.1:5173` | 跨域来源 |
| `lingani.cycani.token` | `CYCANI_TOKEN` | 空 | 播放直链 Token |

HikariCP：最大连接 10、最小空闲 2、连接超时 30 秒；Jackson 时区 `Asia/Shanghai`、日期格式 `yyyy-MM-dd HH:mm:ss`。

---

## 七、启动

环境要求：**JDK 17**、Maven 3.9+、MySQL 8.x。

```powershell
# 1. 建库并初始化
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS lingani DEFAULT CHARSET utf8mb4;"
mysql -u root -p lingani < src/main/resources/sql/init.sql

# 2. 配置密钥（backend/.env）
#    DB_URL=jdbc:mysql://localhost:3306/lingani?...
#    DB_USERNAME=root
#    DB_PASSWORD=...
#    USER_JWT_SECRET=...
#    AGENT_JWT_SECRET=...   # 必须与 agent/.env 完全一致
#    CYCANI_TOKEN=...

# 3. 如果系统默认 Java 不是 17
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# 4. 启动
mvn spring-boot:run
```

接口文档：`http://localhost:8080/swagger-ui.html`。

---

## 八、关键文件路径

| 文件 | 作用 |
|------|------|
| `config/SecurityConfig.java` | 安全过滤链、放行矩阵、CORS |
| `security/UserJwtAuthenticationFilter.java` | USER JWT 解析与身份建立 |
| `security/AgentJwtAuthenticationFilter.java` | AGENT JWT 解析（仅历史/收藏） |
| `security/AgentJwtService.java` | 委托令牌签发与校验 |
| `security/TokenBlacklistService.java` | 注销令牌黑名单 |
| `controller/AgentContextController.java` | 委托令牌签发接口 |
| `service/CycaniService.java` | 第三方协议归一化 |
| `client/CycaniClient.java` | 第三方 HTTP 调用 |
| `service/HistoryService.java` | 播放进度 upsert 与封面补齐 |
| `service/FavoriteService.java` | 追番增删查与查重 |
| `job/FavoriteUpdateNotifier.java` | 追番更新提醒定时任务 |
| `common/GlobalExceptionHandler.java` | 全局异常映射 |
| `resources/sql/init.sql` | 建表脚本 |
| `resources/application.yml` | 全部配置项 |

---

## 九、已知限制

| 限制 | 说明 |
|------|------|
| 无缓存层 | 动漫元数据每次直连 CycAni；历史/追番列表逐条补详情，页码越大延迟越明显 |
| 列表封面依赖第三方 | 第三方不可用时列表封面为空，仅能显示占位图 |
| 令牌黑名单在内存 | 多实例部署时各实例黑名单不共享，需重启或换 Redis |
| 登录限流在内存 | 同上，多实例下失效 |
| 无数据库迁移工具 | 仅 `init.sql` 全量建表，无 Flyway/Liquibase 版本管理 |
| 无单元测试 | `src/test` 下无测试代码 |
| 强依赖第三方协议 | `CycaniService` 中的字段名是硬编码兜底列表，第三方改字段需同步调整 |
