# 灵漫 LingAni 后端

基于 Spring Boot 4 + MyBatis-Plus + MySQL 8 的灵漫后端项目，为前端提供动漫元数据代理、账号、播放历史、追番、评论与弹幕能力。

## 技术栈

- Java 17、Spring Boot 4.1.2
- Spring Web / Spring Security / Bean Validation
- MyBatis-Plus 3.5.18.1
- MySQL 8.0（`utf8mb4`）
- JJWT 0.12.6（无状态 JWT）
- springdoc-openapi 3.2.0（OpenAPI 3 + Swagger UI）

## 目录结构

```
backend/
├── pom.xml
├── .env.example                       # 环境变量模板（复制为 .env）
├── src/main/java/com/lingani/
│   ├── common/      # 统一响应、分页、业务异常、全局异常处理
│   ├── config/      # Security、MyBatis-Plus、JWT/CORS/CycAni 配置
│   ├── client/      # CycAni 统一请求客户端（基础地址、请求头、超时、异常）
│   ├── controller/  # CycAni/Auth/History/Favorite/Comment/Danmaku/Play/Admin 接口
│   ├── dto/ vo/     # 请求与响应模型
│   ├── entity/      # 5 张业务表实体
│   ├── mapper/      # MyBatis-Plus Mapper
│   ├── security/    # JWT 过滤器、Token 黑名单、登录用户上下文
│   └── service/     # 业务逻辑（含 CycaniService 动漫元数据查询）
└── src/main/resources/
    ├── application.yml
    └── sql/init.sql               # 建库建表脚本
```

## 启动步骤

1. **初始化数据库**
   ```bash
   mysql -u root -p -e "source backend/src/main/resources/sql/init.sql"
   ```
2. **配置环境变量**
   ```bash
   cd backend
   cp .env.example .env
   # 编辑 .env，填写 MySQL 密码、JWT 密钥、CycAni Token 等实际环境参数
   ```
   `.env` 已被 `.gitignore` 忽略，不会提交到 Git；`application.yml` 会通过 `spring.config.import` 自动加载它。
3. **启动后端**
   ```bash
   # 需已安装 Maven 与 JDK 17；以下为 Windows PowerShell 示例
   $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
   mvn spring-boot:run
   ```
   默认地址：`http://localhost:8080`。
4. **前端联调**

   在 `frontend/.env` 中配置：
   ```env
   VITE_BACKEND_BASE_URL=http://localhost:8080
   ```

## 环境配置

敏感配置全部放在后端根目录的 `.env`（从 `.env.example` 复制）。`application.yml` 通过 `spring.config.import` 自动加载该文件，并用 `${变量名:默认值}` 占位符引用；变量未配置时使用内置默认值。`.env` 已被 `.gitignore` 忽略，且需在 `backend` 目录下启动服务。

| 变量                            | 是否必填 | 说明                                          |
| ----------------------------- | ---- | ------------------------------------------- |
| `DB_URL`                      | 可选   | 数据库连接地址，默认本地 `lingani` 库                     |
| `DB_USERNAME`                 | 可选   | 数据库用户名，默认 `root`                             |
| `DB_PASSWORD`                 | 必填   | 数据库密码                                       |
| `JWT_SECRET`                  | 必填   | HS256 密钥，至少 32 字节，默认值仅供本地开发                 |
| `JWT_EXPIRE_SECONDS`          | 可选   | Token 有效期（秒，默认 7 天）                          |
| `CORS_ALLOWED_ORIGINS`        | 可选   | 允许跨域的前端来源，逗号分隔，默认本地 5173                    |
| `CYCANI_TOKEN`                | 必填   | 播放源直链接口 Token（第三方颁发），仅保存在后端，缺失时播放接口返回 503   |
| `SERVER_PORT`                 | 可选   | 服务端口，默认 8080                                 |

完整清单见 `backend/.env.example`。

## 统一响应结构

```json
{ "code": 200, "msg": "success", "data": {} }
```

- 成功：`code = 200`
- 参数错误：400；未登录：401；无权限：403；不存在：404；冲突：409；频率限制：429；服务异常：500
- HTTP 状态码与 `code` 保持一致，方便前端统一处理

分页数据结构：

```json
{ "records": [], "total": 0, "current": 1, "size": 10 }
```

## 接口文档（Swagger UI）

启动后端后即可在线查看并调试全部接口：

| 类型           | 地址                                      |
| ------------ | --------------------------------------- |
| Swagger UI   | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs`     |

使用步骤：

1. 在 Swagger UI 中先调用 `POST /api/auth/login`，从返回的 `data.token` 复制 Token
2. 点击页面右上角 **Authorize**，填入该 Token（不需要手动加 `Bearer `   前缀）
3. 之后即可直接调试所有需要登录的接口

文档按「动漫数据」「认证与用户」「播放历史」「追番收藏」「评论」「弹幕」「管理后台」分组展示；接口详细说明见下方「接口清单」。

## 接口清单

> 除标注「公开」外，所有接口都需要请求头 `Authorization: Bearer <token>`。

### 动漫数据（CycAni 代理）

前端不再直连 CycAni，全部经后端 `CycaniClient` 统一访问。响应中的 `data` 与 CycAni 原始结构保持一致（列表为 `{ list, pager }`），避免前端字段丢失。

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/cycani/videos` | 动漫列表，支持 `zoneId`、`year`、`tag`、`page`、`pageSize` | 公开 |
| GET | `/api/cycani/videos/search` | 关键词搜索，参数 `q`、`page`、`pageSize` | 公开 |
| GET | `/api/cycani/videos/{id}` | 动漫详情 | 公开 |
| GET | `/api/cycani/videos/{id}/sections` | 章节列表，参数 `playerCode`、`page`、`pageSize` | 公开 |
| GET | `/api/cycani/videos/{id}/recommendations` | 相关推荐，参数 `limit` | 公开 |
| GET | `/api/cycani/ranks` | 排行榜榜单列表 | 公开 |
| GET | `/api/cycani/ranks/{id}/videos` | 指定榜单的动漫列表 | 公开 |
| GET | `/api/cycani/index/weekday` | 追番周表 | 公开 |
| GET | `/api/cycani/index/recommend` | 首页推荐 | 公开 |
| GET | `/api/cycani/adverts` | 首页轮播位，参数 `position`（默认 `banner`） | 公开 |
| GET | `/api/cycani/play/{sectionId}` | 章节视频直链，第三方 Token 保存在后端 | 公开 |

播放源 Token 配置在后端 `.env` 的 `CYCANI_TOKEN`，前端产物不包含该 Token；未配置时该接口返回 503。

异常处理：上游 404 返回 404「动漫数据不存在」，上游 5xx 返回 502，超时返回 504，业务 `code != 0` 返回 502 并携带上游提示；不会把异常堆栈或第三方敏感信息透给前端。

### 认证与用户

| 方法   | 路径                   | 说明                   | 权限 |
| ---- | -------------------- | -------------------- | -- |
| POST | `/api/auth/register` | 注册                   | 公开 |
| POST | `/api/auth/login`    | 登录，返回 token 与 user   | 公开 |
| POST | `/api/auth/logout`   | 退出登录（当前 Token 加入黑名单） | 登录 |
| GET  | `/api/user/me`       | 当前用户信息               | 登录 |
| PUT  | `/api/user/profile`  | 修改昵称、头像              | 登录 |
| PUT  | `/api/user/password` | 修改密码，旧 Token 全部失效    | 登录 |

注册/登录请求示例：

```json
{ "username": "lingan", "password": "abc12345", "confirmPassword": "abc12345", "nickname": "灵漫" }
```

密码规则：8-32 位，同时包含字母与数字。

### 播放历史

| 方法     | 路径                               | 说明                                 |
| ------ | -------------------------------- | ---------------------------------- |
| POST   | `/api/history`                   | 保存/更新进度（按 `userId + sectionId` 唯一） |
| GET    | `/api/history?current=1&size=10` | 分页历史，按最近播放时间倒序                     |
| GET    | `/api/history/video/{videoId}`   | 某部动画下本人的全部观看进度（用于详情页已看标记与续播）       |
| DELETE | `/api/history/{id}`              | 删除单条（仅本人）                          |
| DELETE | `/api/history`                   | 清空本人历史                             |

保存请求示例：

```json
{
  "videoId": "3921",
  "sectionId": "52383",
  "animeTitle": "银魂",
  "animeCover": "https://example.com/cover.jpg",
  "episodeTitle": "第1话",
  "progress": 120,
  "duration": 1440
}
```

### 追番

| 方法     | 路径                                  | 说明        |
| ------ | ----------------------------------- | --------- |
| POST   | `/api/favorites`                    | 添加追番      |
| GET    | `/api/favorites?current=1&size=12`  | 分页追番列表    |
| GET    | `/api/favorites/check?videoId=3921` | 查询是否已追番   |
| DELETE | `/api/favorites/{id}`               | 取消追番（仅本人） |

### 评论

| 方法     | 路径                                            | 说明                  | 权限  |
| ------ | --------------------------------------------- | ------------------- | --- |
| GET    | `/api/comments?videoId=3921&current=1&size=8` | 一级评论分页，附带子回复        | 公开  |
| POST   | `/api/comments`                               | 发表评论/回复             | 登录  |
| PUT    | `/api/comments/{id}`                          | 编辑评论                | 仅作者 |
| DELETE | `/api/comments/{id}`                          | 删除评论，删除一级评论时同步删除子回复 | 仅作者 |

发布示例：

```json
{ "videoId": "3921", "content": "这部动画的节奏很舒服", "parentId": 0 }
```

### 弹幕

| 方法     | 路径                             | 说明                     | 权限  |
| ------ | ------------------------------ | ---------------------- | --- |
| GET    | `/api/danmaku?sectionId=52383` | 按章节查询，按时间升序，`data` 为数组 | 公开  |
| POST   | `/api/danmaku`                 | 发送弹幕                   | 登录  |
| DELETE | `/api/danmaku/{id}`            | 删除本人弹幕                 | 仅作者 |

发送示例：

```json
{ "sectionId": "52383", "time": 12.5, "text": "前方高能", "color": 16777215, "mode": 0 }
```

`color` 为十进制颜色值（0 \~ 16777215），`mode`：0 滚动、1 顶部、2 底部。

### 管理后台

以下接口要求登录账号的 `role = 1`（管理员），普通账号访问返回 403。默认后台入口为前端 `/admin`。

| 方法     | 路径                                                | 说明                             |
| ------ | ------------------------------------------------- | ------------------------------ |
| GET    | `/api/admin/stats`                                | 数据总览：用户、评论、弹幕、追番、历史总数          |
| GET    | `/api/admin/users?current=1&size=10&keyword=`     | 用户列表，支持用户名/昵称搜索                |
| PUT    | `/api/admin/users/{id}/status`                    | 启用/禁用用户（不能操作自己；禁用会递增 Token 版本） |
| GET    | `/api/admin/comments?current=1&size=10&keyword=`  | 评论列表                           |
| PUT    | `/api/admin/comments/{id}/status`                 | 屏蔽/恢复评论（0 正常，1 屏蔽）             |
| DELETE | `/api/admin/comments/{id}`                        | 删除评论，一级评论同时删除子回复               |
| GET    | `/api/admin/danmaku?current=1&size=10&keyword=`   | 弹幕列表                           |
| PUT    | `/api/admin/danmaku/{id}/status`                  | 屏蔽/恢复弹幕                        |
| DELETE | `/api/admin/danmaku/{id}`                         | 删除弹幕                           |
| GET    | `/api/admin/favorites?current=1&size=10&keyword=` | 全站追番记录（只读）                     |
| GET    | `/api/admin/history?current=1&size=10&keyword=`   | 全站播放历史（只读）                     |

状态修改请求体：

```json
{ "status": 1 }
```

将账号提升为管理员：

```sql
UPDATE sys_user SET role = 1 WHERE username = '你的用户名';
```

管理员身份从数据库实时读取，修改后无需重新登录即可生效。

## 安全设计

- **密码**：BCrypt 加密存储，注册与修改密码均做长度和复杂度校验。
- **JWT**：HS256 签名，密钥至少 32 字节；Token 包含 `sub`（用户 ID）、`jti`、`tv`（Token 版本）。
- **退出登录**：服务端将当前 Token 的 `jti` 加入内存黑名单直至自然过期，不是只返回成功。
- **修改密码**：递增 `sys_user.token_version`，旧 Token 全部失效；前端修改成功后要求重新登录。
- **越权防护**：历史、追番、评论、弹幕的删除/修改均校验资源归属；用户 ID 只从 JWT 读取，不信任请求参数。
- **管理员权限**：`/api/admin/**` 由 `SecurityUtils.requireAdmin()` 统一校验 `role = 1`，角色每次请求从数据库读取。
- **登录限流**：同一用户名 + IP 10 分钟内最多失败 5 次。
- **弹幕限流**：同一用户 3 秒内最多 1 条，每分钟最多 20 条。
- **输入校验**：所有请求 DTO 使用 Bean Validation 校验；MyBatis-Plus 使用参数绑定，避免 SQL 注入。
- **CORS**：只允许配置的前端来源，Bearer Token 模式下不开启 `allowCredentials` 的通配来源。
- **状态字段**：用户 `status`、评论/弹幕 `status` 仅由后端维护，前端没有修改入口。

## 数据表

| 表               | 说明                                      |
| --------------- | --------------------------------------- |
| `sys_user`      | 用户、BCrypt 密码、角色（role）、状态、Token 版本、逻辑删除  |
| `user_history`  | 播放历史，`(user_id, section_id)` 唯一         |
| `user_favorite` | 追番，`(user_id, video_id)` 唯一             |
| `anime_comment` | 评论，`parent_id = 0` 为一级，逻辑删除             |
| `anime_danmaku` | 弹幕，`section_id + time` 索引，`status` 控制隐藏 |

## 关于动画标识

本项目动漫元数据来自 CycAni，并由后端统一代理（`/api/cycani/**`），前端不直连第三方接口。评论、追番、播放历史统一使用 CycAni 的 `videoId`（字符串）作为动画唯一标识，不使用其他数据源的 ID。
