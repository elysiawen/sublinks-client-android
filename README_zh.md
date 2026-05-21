# SubLinks Client for Android (SCA)

<div align="center">

[![License](https://img.shields.io/github/license/MetaCubeX/ClashMetaForAndroid)](https://github.com/MetaCubeX/ClashMetaForAndroid/blob/Meta/LICENSE)

基于 [ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid) 的 SubLinks 订阅管理客户端

[English](README.md)

</div>

## 特性

### SubLinks 集成
- **一键登录**：使用 SubLinks 账号直接登录
- **自动同步**：订阅配置自动同步到设备
- **智能管理**：自动处理订阅更新和冲突

### 个性化定制
- **Hero 卡片背景**：
  - 网络图片（随机 API 或自定义 URL）
  - 本地图片选择
  - 纯色/渐变色（内置色板 + 自定义）
- **时段问候**：根据时间显示不同的问候语
- **一言集成**：每日随机一言展示

### 核心功能
- 完整的 Clash Meta 内核支持
- 规则集管理
- 配置文件编辑
- 实时流量监控
- 延迟测试

## 截图

<div align="center">

| 登录界面 | 主界面 | 个性化设置 |
|:---:|:---:|:---:|
| ![Login](screenshots/login.png) | ![Main](screenshots/main.png) | ![Settings](screenshots/settings.png) |

</div>

## 构建

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 21
- Android SDK (API 35)
- Go 1.25+ （用于编译 Clash Meta 内核）

### 配置

1. **克隆仓库**
   ```bash
   git clone https://github.com/elysiawen/sublinks-client-android.git
   cd sublinks-client-android
   ```

2. **配置 API 地址**

   在项目根目录创建 `local.properties` 文件：
   ```properties
   # SubLinks API 配置
   SUBLINKS_APIURL_RELEASE=https://your-sublinks-server.com/
   SUBLINKS_APIURL_DEBUG=http://192.168.1.100:3000/

   # Android SDK 路径（如果需要）
   sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
   ```

3. **生成签名密钥**（可选，用于 Release 构建）
   ```bash
   keytool -genkey -v -keystore release.keystore -alias key0 -keyalg RSA -keysize 2048 -validity 10000
   ```

   创建 `signing.properties`：
   ```properties
   keystore.password=your_password
   key.alias=key0
   key.password=your_password
   ```

4. **构建**
   ```bash
   # Debug 版本
   ./gradlew assembleMetaDebug

   # Release 版本
   ./gradlew assembleMetaRelease
   ```

生成的 APK 位于：`app/build/outputs/apk/meta/release/`

## 同步上游更新

本项目基于 ClashMetaForAndroid，可以定期同步上游更新：

```bash
# 添加上游仓库（仅需一次）
git remote add upstream https://github.com/MetaCubeX/ClashMetaForAndroid.git

# 同步更新
git fetch upstream
git checkout -b sync-upstream-$(date +%Y%m%d)
git merge upstream/main

# 解决冲突后
git checkout sublinks
git merge sync-upstream-$(date +%Y%m%d)
git push origin sublinks
```

详细说明请参考 [上游同步指南](docs/UPSTREAM_SYNC.md)

## 与上游的主要差异

### 修改的文件
- `build.gradle.kts` - 应用 ID 和版本配置
- `app/build.gradle.kts` - API URL 配置和 APK 命名
- `app/src/main/java/com/github/kr328/clash/SubLinksService.kt` - SubLinks API 集成
- `app/src/main/java/com/github/kr328/clash/LoginActivity.kt` - 登录界面
- `design/src/main/res/` - UI 资源和字符串

### 新增的功能
- SubLinks 账号系统集成
- 订阅自动同步
- Hero 卡片个性化
- 时段问候和一言

## 贡献

欢迎提交 Issue 和 Pull Request！

### 开发指南
1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

本项目基于 GPL-3.0 许可证开源，详见 [LICENSE](LICENSE) 文件。

## 致谢

- [ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid) - 上游项目
- [Clash Meta (mihomo)](https://github.com/MetaCubeX/mihomo) - 核心引擎
- [一言](https://hitokoto.cn/) - 一言 API

## 联系方式

- Issue: [GitHub Issues](https://github.com/elysiawen/sublinks-client-android/issues)
