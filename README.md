# 灵漫 LingAni

> Spring Boot 后端 + Vue 3 前端 + LangGraph AI Agent「小灵」。

## 一、项目简介

灵漫是一个基于 Spring Boot、Vue 与 LangGraph 的智能动漫观影平台，主要功能：

- **用户体系**：注册 / 登录 / 注销、资料与密码修改、JWT 无状态认证
- **动漫观看**：对接 CycAni 第三方播放源，支持选集播放、弹幕、断点续播
- **个人数据**：播放历史、追番收藏、站内通知（追番更新定时提醒）
- **社区互动**：番剧评论（含楼中楼）、弹幕发送
- **AI 助手「小灵」**：基于 LangGraph 的对话式 Agent，可查询动漫信息、读取用户历史与收藏，支持 SSE 流式输出
- **管理后台**：用户 / 评论 / 弹幕 / 追番 / 历史的查询与审核

## 二、技术栈

| 模块 | 技术 |
|------|------|
| 后端 | Spring Boot 4、Java 17、MyBatis-Plus、Spring Security、jjwt、SpringDoc OpenAPI |
| 前端 | Vue 3、Vite、Vue Router、Axios、ArtPlayer（播放器）、Sass |
| AI Agent | Python 3.13、FastAPI、LangGraph、LangChain、DeepSeek API（可切换本地 Ollama） |
| 数据库 | MySQL 8（utf8mb4） |
| 部署 | Docker、Docker Compose、Nginx |

## 三、目录结构

```
LingAni/
├── backend/                # Spring Boot 后端（:8080）
│   ├── src/main/java/com/lingani/
│   │   ├── controller/     # 接口层（认证、历史、追番、评论、弹幕、通知、管理、Agent 令牌签发）
│   │   ├── service/        # 业务逻辑
│   │   ├── security/       # USER JWT / AGENT JWT 双令牌体系
│   │   ├── client/         # CycAni 第三方接口调用
│   │   └── job/            # 追番更新定时任务
│   ├── src/main/resources/
│   │   ├── application.yml # 全部配置项（环境变量 → 默认值两级回退）
│   │   └── sql/init.sql    # 建库建表脚本
│   └── Dockerfile
├── frontend/               # Vue 3 前端（开发 :5173，生产 nginx :80）
│   ├── src/api/            # 后端 / Agent 请求封装
│   ├── nginx.conf          # 生产反代配置（/api、/agent-api、SPA 回退）
│   └── Dockerfile
├── agent/                  # Python AI Agent（:8000）
│   ├── app/agent/          # LangGraph 图、提示词、会话管理
│   ├── app/tools/          # 动漫 / 用户数据工具
│   ├── app/security/       # AGENT JWT 校验
│   └── Dockerfile
├── docker-compose.yml      # 四服务编排（mysql / backend / agent / frontend）
└── .env.example            # 部署环境变量模板
```

## 四、本地开发

### 4.1 环境要求

JDK 17、Maven 3.9+、Node 20+、Python 3.13、MySQL 8。

### 4.2 初始化数据库

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS lingani DEFAULT CHARSET utf8mb4;"
mysql -u root -p lingani < backend/src/main/resources/sql/init.sql
```

### 4.3 后端

```bash
cd backend
cp .env.example .env   # 填 DB_PASSWORD / USER_JWT_SECRET / AGENT_JWT_SECRET / CYCANI_TOKEN
mvn spring-boot:run    # http://localhost:8080
```

### 4.4 AI Agent

```bash
cd agent
cp .env.example .env   # 填 DEEPSEEK_API_KEY，AGENT_JWT_SECRET 必须与后端一致
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

### 4.5 前端

```bash
cd frontend
npm install
npm run dev            # http://localhost:5173
```

Vite 已配置同源代理：`/api` → 后端 8080，`/agent-api` → Agent 8000，无需处理跨域。

## 五、Docker Compose 部署

### 5.1 架构

```text
公网 :80 → frontend(nginx)
             ├── /            → 前端静态资源（SPA 回退）
             ├── /api         → backend:8080
             └── /agent-api   → agent:8000（去前缀，SSE 关缓冲）
backend → mysql:3306（仅内网）
agent   → backend:8080、DeepSeek API（外网）
```

MySQL、后端、Agent 均不暴露宿主机端口，只有 nginx 的 80 对外。

### 5.2 服务器准备（CentOS 7 示例）

```bash
# Docker（CentOS 7 已 EOL，建议用阿里云镜像源）
sudo yum install -y yum-utils
sudo yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
sudo yum install -y docker-ce docker-ce-cli containerd.io
sudo systemctl enable --now docker

# Compose 静态二进制
sudo curl -L https://github.com/docker/compose/releases/download/v2.20.3/docker-compose-linux-x86_64 \
  -o /usr/local/bin/docker-compose && sudo chmod +x /usr/local/bin/docker-compose
```

### 5.3 配置与启动

```bash
git clone <仓库地址> /opt/lingani && cd /opt/lingani
cp .env.example .env
openssl rand -hex 32    # 执行两次，分别生成 USER_JWT_SECRET、AGENT_JWT_SECRET
vim .env                # 填 MYSQL_ROOT_PASSWORD / CYCANI_TOKEN / DEEPSEEK_API_KEY 等
chmod 600 .env

sudo firewall-cmd --permanent --add-port=80/tcp && sudo firewall-cmd --reload

docker-compose up -d --build
docker-compose ps
```

同源部署（nginx 反代并清空 `Origin`），无需配置跨域；仅当将来前后端拆分到不同域名时，才需在 `.env` 中补 `CORS_ALLOWED_ORIGINS`。

> 低内存服务器（≤2G）建议先加 swap，或分服务构建：`docker-compose build backend` → `agent` → `frontend`。

### 5.4 访问地址

| 入口 | 地址 |
|------|------|
| 前端首页 | `http://服务器IP/` |
| Agent 健康检查 | `http://服务器IP/agent-api/health` |

生产环境不暴露 Swagger：nginx 未代理 `/swagger-ui`、`/v3/api-docs`，后端 8080 也没有映射到宿主机。需要临时调试时进容器访问：

```bash
docker-compose exec backend curl -s localhost:8080/v3/api-docs
```

### 5.5 常用运维命令

```bash
docker-compose logs -f backend          # 跟踪后端日志
docker-compose restart agent            # 重启单个服务
docker-compose up -d --build frontend   # 修改前端或 nginx 配置后重建
docker-compose down                     # 停止（数据卷保留）

# 数据库备份
docker-compose exec mysql mysqldump -uroot -p lingani > backup-$(date +%F).sql
```

## 六、核心配置说明

部署配置分两处：**根目录 `.env` 只放密钥与凭据**，其余配置直接写在 `docker-compose.yml`。

| 变量 | 说明 |
|------|------|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码，同时作为后端数据库密码 |
| `USER_JWT_SECRET` | 用户登录令牌密钥，≥32 字节随机字符串 |
| `AGENT_JWT_SECRET` | Agent 委托令牌密钥，**后端与 Agent 必须一致** |
| `CYCANI_TOKEN` | CycAni 播放源 Token（仅后端持有） |
| `DEEPSEEK_API_KEY` | DeepSeek API Key |

数据库连接串、端口、模型名、JWT 时效等静态配置在 `docker-compose.yml` 的 `environment` 中，要改就改那里；密钥不写进 compose，避免随代码进 Git。

本地开发则分别使用 `backend/.env` 与 `agent/.env`（模板为各自目录下的 `.env.example`）。

**认证体系**：用户登录后签发 USER JWT（默认 7 天）；每次 AI 对话前，后端额外签发 AGENT JWT（默认 5 分钟、限定 audience），Agent 仅校验不签发，并用它回调后端读取该用户的历史与收藏。

## 七、注意事项与已知限制

- **MySQL 初始化脚本只在数据卷为空时执行**。后续改表结构需手动执行 SQL，不要指望重启容器生效。
- **所有服务只能单实例**：JWT 黑名单、登录限流（后端）与会话记忆（Agent 的 `MemorySaver`）都在进程内存中，多实例不共享。
- **nginx 会清掉代理请求的 `Origin` 头**，与本地 Vite 代理行为一致，避免后端 CORS 白名单误伤同源请求。
- **Agent 会话记忆随重启丢失**，属预期行为。
- 后端默认开启 MyBatis SQL 标准输出日志，生产如需降噪可调整 `application.yml` 中 `mybatis-plus.configuration.log-impl`。
- 动漫元数据强依赖 CycAni 第三方接口，其不可用时列表与播放会受影响。

## 八、模块详细文档

- 后端实现细节：[backend/README.md](backend/README.md)
- 前端实现细节：[frontend/README.md](frontend/README.md)
- Agent 实现细节：[agent/README.md](agent/README.md)
