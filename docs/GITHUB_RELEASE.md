# SubLinks Client Android - 发布指南

## 准备工作清单

在推送代码到 GitHub 之前，请确认以下事项：

- [x] 已 Fork 上游仓库 https://github.com/MetaCubeX/ClashMetaForAndroid
- [x] 本地代码已完成所有修改
- [x] 已配置 `local.properties`（此文件不会被提交）
- [ ] 已测试 Debug 和 Release 构建
- [ ] 已更新 README.md 中的占位符
- [ ] 已准备签名密钥（用于 Release 构建）

## 第一次推送到 GitHub

### 1. 配置 Git 远程仓库

```bash
# 添加您的 Fork 作为 origin
git remote add origin https://github.com/elysiawen/sublinks-client-android.git

# 添加上游仓库
git remote add upstream https://github.com/MetaCubeX/ClashMetaForAndroid.git

# 验证配置
git remote -v
```

### 2. 创建并切换到 meta 分支

```bash
# 创建 meta 分支（与上游保持一致）
git checkout -b meta

# 或者如果已经在 main 分支，重命名为 meta
git branch -m main meta
```

### 3. 添加所有修改

```bash
# 查看所有修改
git status

# 添加所有文件
git add .

# 提交
git commit -m "feat: SubLinks Client Android - Initial Release

- 集成 SubLinks 账号系统
- 添加订阅自动同步功能
- 实现 Hero 卡片个性化
- 添加时段问候和一言
- 自定义 APK 文件名为 SCA-{version}-{abi}-{buildType}.apk
- 配置 API URL 通过 local.properties 管理

基于 ClashMetaForAndroid commit: $(git rev-parse upstream/meta 2>/dev/null || echo 'latest')"
```

### 4. 推送到 GitHub

```bash
# 首次推送
git push -u origin meta

# 如果遇到错误，强制推送（仅首次）
git push -u origin meta --force
```

## 配置 GitHub Secrets

为了让 GitHub Actions 能够自动构建，需要配置以下 Secrets：

1. 访问 https://github.com/elysiawen/sublinks-client-android/settings/secrets/actions

2. 添加以下 Secrets：

   - **SUBLINKS_API_URL**
     - 值：`https://sub.135150.xyz/`
     - 用途：Release 构建的 API 地址

   - **SIGNING_KEY**（可选，用于签名 Release APK）
     - 值：您的 keystore 文件的 Base64 编码
     - 获取方式：
       ```bash
       base64 -w 0 release.keystore
       ```

   - **SIGNING_PASSWORD**（可选）
     - 值：keystore 的密码
     - 格式：`storePassword=xxx|keyAlias=key0|keyPassword=xxx`

## 创建第一个 Release

### 方式 1: 通过 GitHub Web 界面

1. 访问 https://github.com/elysiawen/sublinks-client-android/releases/new
2. 点击 "Choose a tag"，输入 `v1.0.0`，选择 "Create new tag"
3. Release title: `SubLinks Client Android v1.0.0`
4. 描述示例：

```markdown
## ✨ 首个正式版本

基于 ClashMetaForAndroid，专为 SubLinks 订阅服务定制的 Android 客户端。

### 🎉 主要特性

- **SubLinks 集成**：一键登录，自动同步订阅
- **个性化定制**：Hero 卡片支持网络图片、本地图片、纯色/渐变色
- **智能问候**：根据时段显示不同问候语
- **一言集成**：每日随机一言

### 📦 下载说明

- **SCA-1.0.0-universal-release.apk**：通用版本（推荐）
- **SCA-1.0.0-arm64-v8a-release.apk**：64位 ARM 设备专用
- **SCA-1.0.0-armeabi-v7a-release.apk**：32位 ARM 设备专用

### 🔧 配置要求

需要有效的 SubLinks 账号才能使用。

### 📝 更新日志

- 首次发布
- 基于 ClashMetaForAndroid v2.x.x
```

5. 上传编译好的 APK 文件
6. 点击 "Publish release"

### 方式 2: 通过 Git Tag（自动构建）

```bash
# 创建标签
git tag -a v1.0.0 -m "Release v1.0.0"

# 推送标签
git push origin v1.0.0
```

GitHub Actions 会自动构建并创建 Release。

## 日常开发流程

### 提交新功能

```bash
# 创建功能分支
git checkout -b feature/new-feature

# 开发并提交
git add .
git commit -m "feat: 添加新功能"

# 推送到 GitHub
git push origin feature/new-feature

# 在 GitHub 上创建 Pull Request 到 meta 分支
```

### 发布新版本

```bash
# 确保在 meta 分支
git checkout meta

# 更新版本号（在 build.gradle.kts 中）
# versionName = "1.1.0"
# versionCode = 10100

# 提交版本更新
git add build.gradle.kts
git commit -m "chore: bump version to 1.1.0"

# 推送
git push origin meta

# 创建标签
git tag -a v1.1.0 -m "Release v1.1.0"
git push origin v1.1.0
```

## 同步上游更新

```bash
# 获取上游更新
git fetch upstream

# 创建同步分支
git checkout -b sync-upstream-$(date +%Y%m%d)

# 合并上游
git merge upstream/meta

# 解决冲突（如果有）
# ... 手动解决冲突 ...

# 提交合并
git add .
git commit -m "chore: sync upstream ClashMetaForAndroid"

# 合并回 meta 分支
git checkout meta
git merge sync-upstream-$(date +%Y%m%d)

# 推送
git push origin meta
```

详细的冲突解决指南请参考项目中的文档。

## 常见问题

### Q: 推送时提示权限错误？

A: 确保您已经配置了 GitHub 的 SSH 密钥或使用 Personal Access Token。

### Q: GitHub Actions 构建失败？

A: 检查是否正确配置了 Secrets，特别是 `SUBLINKS_API_URL`。

### Q: 如何更新 README 中的截图？

A: 
1. 创建 `screenshots/` 目录
2. 添加截图文件（login.png, main.png, settings.png）
3. 提交并推送

### Q: 如何禁用自动构建？

A: 删除或重命名 `.github/workflows/build.yml` 文件。

## 维护建议

1. **定期同步上游**：每月至少同步一次
2. **版本号规范**：遵循语义化版本 (Semantic Versioning)
3. **变更日志**：每次发布都更新 CHANGELOG.md
4. **测试充分**：发布前务必测试 Debug 和 Release 版本
5. **备份密钥**：妥善保管 `release.keystore` 和 `signing.properties`

## 需要帮助？

- 查看 [README.md](../README.md)
- 提交 [Issue](https://github.com/elysiawen/sublinks-client-android/issues)
- 参考上游文档：https://github.com/MetaCubeX/ClashMetaForAndroid
