# Copilot Response Preferences

请始终使用简体中文回答与本仓库相关的问题。

若用户没有明确要求英文，请不要输出整段英文内容。

解释时先给结论，再给步骤；保持内容简洁、可执行。

技术名词优先使用中文，必要时在括号中补充英文原词。

代码注释、提交说明、排查建议默认使用中文。

本目录是独立 Android Git 仓库，不是根仓库的普通子目录；涉及提交、CI、Actions 排查时使用 `TiDaoCookieReader` 仓库上下文。

修改上传请求、Server酱绑定、邮箱绑定时，请同步考虑根仓库 `fc_handler.update_cookie_handler` 的请求体兼容性。

不要输出或提交 Cookie、sendkey、真实云函数 URL、OSS Secret、SMTP 授权码等敏感信息。

Android 构建使用 AGP 8.5.0 和 JDK 17；CI 中 Maven 仓库应保持官方源优先，阿里云镜像仅作兜底。
