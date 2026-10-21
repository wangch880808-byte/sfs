# 学校主平台域名切换

适用于当前 NUS 离线部署：Ubuntu 24.04、`/srv/nus`、学校网关负责 HTTPS 证书，并通过 HTTP 回源服务器。默认主平台地址为 `https://nus.zknu.edu.cn`。

将同目录的 `configure-domain.sh` 上传到服务器，在脚本所在目录执行：

```bash
sudo bash configure-domain.sh --keep-monitor --yes
```

脚本使用服务器已有的 Python 3、Docker Compose、curl 和本地镜像，不需要下载依赖，不需要输入证书路径。

## 自动完成的内容

- 更新 `/srv/nus/config/.env` 的 `NUS_SITE_URL`。
- 更新数据库中的主本地文件存储域名和部署站点记录，保留上传文件目录。
- 将代码中列出的附件、头像、图片和富文本字段内，原站点的 NUS 文件接口地址改成 HTTPS 域名；包含普通链接和 JSON 转义链接。
- 为主平台 API 设置正确的 HTTPS 转发信息，将生成的 Nginx 配置持久化到 `/srv/nus/config/domain-nginx/nginx.conf`，并修改当前发布目录的 Compose 挂载。
- 重建前后端容器，等待健康检查，并检查服务器本机的首页响应。

按本次要求，不创建备份。运行期间前后端会短暂中断。SkyWalking 地址和监控 Cookie 设置保持现状；学校网关、证书和防火墙配置不由脚本修改。

域名配置保存在 `/srv/nus/config/domain-nginx/`，后续应用升级会通过其中的 Compose 覆盖文件自动沿用，无需重新执行本脚本。

## 验收

看到“主平台切换完成”后，通过外部浏览器访问 `https://nus.zknu.edu.cn`，检查登录、已有附件下载、图片显示，以及新文件上传。服务器本机检查通过不能代替学校网关链路的外部验收。

如果报错，保存错误输出并检查服务日志。修改开始后报错不会自动回滚或重新开放业务；修复原因后可以重复执行同一命令。数据库更新在一个事务中完成；数据库提交和配置文件写入不是跨系统原子事务。

## 可选预检查

```bash
sudo bash configure-domain.sh --keep-monitor --dry-run
```

预检查读取现有部署和数据库，并验证 Compose；不应用配置、不更新数据库、不重启业务。实际执行时还会使用本地前端镜像运行独立的 `nginx -t` 语法检查。

历史链接迁移只处理脚本明确列出的业务字段中的 `/admin-api/infra/file/` 和 `/app-api/infra/file/` 地址，不修改上传文件内部内容或审计日志。其他历史站点地址可以使用 `--old-url http://旧站点地址` 补充。
