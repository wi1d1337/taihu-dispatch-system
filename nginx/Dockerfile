# 前端自包含构建：repo 根为构建上下文（需包含 ruoyi-ui/ 与 nginx/）
FROM node:22-alpine AS build
WORKDIR /ui
COPY ruoyi-ui/package.json ruoyi-ui/package-lock.json* ./
RUN npm config set registry https://registry.npmmirror.com && npm install --no-audit --no-fund
COPY ruoyi-ui/ .
RUN npm run build:prod

FROM nginx:1.27-alpine
COPY --from=build /ui/dist /usr/share/nginx/html
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
