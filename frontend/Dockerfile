# 灵漫前端：Vite 构建 + nginx 托管
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm config set registry https://registry.npmmirror.com && npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine
ENV TZ=Asia/Shanghai
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
