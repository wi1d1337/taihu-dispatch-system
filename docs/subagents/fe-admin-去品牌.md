# fe-admin 管理台去品牌底稿（2026-10-13）

TL;DR：ruoyi-ui 产品界面已完成去品牌，全局统一"太湖蓝藻水华预报与打捞调度系统"（长名，标题/登录页）+"太湖蓝藻调度系统"（短名，侧边栏）；`npm run build:prod` 构建通过。仓库署名（LICENSE/README/版权注释头）未动，符合 MIT 合规边界。

## 一、修改文件清单（均在 ruoyi-ui\）

| # | 文件 | 改动 |
|---|------|------|
| 1 | .env.development / .env.production / .env.staging | VITE_APP_TITLE → 长名；新增 VITE_APP_SHORT_TITLE → 短名；注释同步 |
| 2 | index.html | 无需改（title 引用 %VITE_APP_TITLE%，随 env 生效） |
| 3 | src/settings.js | footerContent："Copyright © 2018-2026 RuoYi..." → "Copyright © 2026 无锡天垣视界科技有限公司"（footerVisible=false，仅兜底文案） |
| 4 | src/views/login.vue | 无需改（标题/页脚均绑定 VITE_APP_TITLE 与 settings，随 1/3 生效；无语言下拉，注册开关默认关） |
| 5 | src/layout/components/Sidebar/Logo.vue | 标题改用 VITE_APP_SHORT_TITLE；logo 引用 logo.png → logo.svg |
| 6 | src/assets/logo/logo.svg | 新增：太湖蓝渐变圆角方块+白色波浪（替代若依绿叶 logo.png，已删除） |
| 7 | public/favicon.ico | 重新生成：太湖蓝波浪图标（替代若依 favicon） |
| 8 | src/layout/components/Navbar.vue | 移除顶部"源码地址/文档地址"两枚若依外链按钮及 import |
| 9 | src/components/RuoYi/（Git、Doc） | 删除（gitee/ruoyi.vip/doc.ruoyi.vip 外链组件，已无引用） |
| 10 | src/views/index.vue | 首页整页重写：若依介绍/技术选型/QQ群/微信/支付宝/打赏/更新日志 → 系统名+简介+日期的欢迎页；pay.png 已删除 |
| 11 | src/views/tool/build/index.vue | Form Generator 页 logo 引用 png → svg |
| 12 | src/views/tool/gen/index.vue | 代码生成 zip 包名 "ruoyi.zip" → "gen-code.zip" |

## 二、替换对照表（界面可见字符串）

| 原文案/元素 | 现文案/元素 |
|------------|------------|
| 若依管理系统（浏览器标题/登录页） | 太湖蓝藻水华预报与打捞调度系统 |
| RuoYi 绿叶 logo / favicon | 自绘太湖蓝波浪 SVG |
| Copyright © 2018-2026 RuoYi. All Rights Reserved. | Copyright © 2026 无锡天垣视界科技有限公司 |
| 顶部"源码地址/文档地址"外链（gitee/doc.ruoyi.vip） | 移除 |
| 首页"若依后台管理框架"+QQ群/微信/支付宝/捐赠/更新日志 | 系统欢迎页（自绘） |
| ruoyi.zip（代码生成下载名） | gen-code.zip |

## 三、保留项与理由

- ruoyi-ui/LICENSE、README 开源致谢、源码版权注释头（utils/ruoyi.js、directive/*）：MIT 合规要求，保留。
- utils/ruoyi.js 模块名及各处 `import ... from '@/utils/ruoyi'`：代码标识符非界面文案，不改（避免无收益的重构）。
- vite.config.js:14、api/taihu/h5.js、views/h5/login.vue 中"若依/ruoyi.vip"：代码注释，界面不可见。
- login-background.jpg（窗户照片）、profile.jpg：通用素材无品牌痕迹，保留。
- H5 页面（views/h5）：自有品牌，未动。

## 四、待核项

- 待核-1：views/monitor/job/index.vue:171 定时任务提示 "com.ruoyi.quartz.task.RyTask.ryParams('ry')"——后端实际包名即 com.ruoyi（RyTask.java 存在），为事实性示例非品牌展示，改了会误导运维；建议后端日后改包名时同步。
- 待核-2：views/tool/gen/genInfoForm.vue:30 包路径占位提示 "例如 com.ruoyi.system"——同上，随待核-1 一并处理。
- 待核-3：package.json name/description 仍为 ruoyi/若依管理系统——仓库元数据非产品界面，未动，是否改由主对话拍板。

## 五、构建结果

- `npm run build:prod`：BUILD PASS（两次执行验证退出码）。
- dist/index.html `<title>` = 太湖蓝藻水华预报与打捞调度系统；dist 内 grep "若依/RuoYi" 无界面文案命中。
- 未 commit。
