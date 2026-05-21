# SubLinks Client for Android (SCA)

<div align="center">

[![License](https://img.shields.io/github/license/MetaCubeX/ClashMetaForAndroid)](https://github.com/MetaCubeX/ClashMetaForAndroid/blob/Meta/LICENSE)

A SubLinks subscription management client based on [ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)

[中文](README_zh.md)

</div>

## Features

### SubLinks Integration
- **One-tap Login**: Sign in directly with your SubLinks account
- **Auto Sync**: Subscription configs automatically sync to your device
- **Smart Management**: Handles subscription updates and conflicts automatically

### Personalization
- **Hero Card Background**:
  - Online images (random API or custom URL)
  - Local image picker
  - Solid color / gradient (built-in palette + custom)
- **Time-based Greeting**: Greeting messages that change throughout the day
- **Hitokoto**: Daily random quote display

### Core
- Full Clash Meta kernel support
- Rule set management
- Config file editor
- Real-time traffic monitoring
- Latency testing

## Screenshots

<div align="center">

| Login | Main | Settings |
|:---:|:---:|:---:|
| ![Login](screenshots/login.png) | ![Main](screenshots/main.png) | ![Settings](screenshots/settings.png) |

</div>

## Building

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 21
- Android SDK (API 35)
- Go 1.25+ (for compiling the Clash Meta kernel)

### Setup

1. **Clone the repo**
   ```bash
   git clone https://github.com/elysiawen/sublinks-client-android.git
   cd sublinks-client-android
   ```

2. **Configure API endpoints**

   Create a `local.properties` file in the project root:
   ```properties
   # SubLinks API configuration
   SUBLINKS_APIURL_RELEASE=https://your-sublinks-server.com/
   SUBLINKS_APIURL_DEBUG=http://192.168.1.100:3000/

   # Android SDK path (if needed)
   sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
   ```

3. **Generate a signing key** (optional, for release builds)
   ```bash
   keytool -genkey -v -keystore release.keystore -alias key0 -keyalg RSA -keysize 2048 -validity 10000
   ```

   Create `signing.properties`:
   ```properties
   keystore.password=your_password
   key.alias=key0
   key.password=your_password
   ```

4. **Build**
   ```bash
   # Debug
   ./gradlew assembleMetaDebug

   # Release
   ./gradlew assembleMetaRelease
   ```

Output APKs are located at: `app/build/outputs/apk/meta/release/`

## Syncing Upstream Updates

This project is based on ClashMetaForAndroid. To sync upstream changes:

```bash
# Add upstream remote (one-time)
git remote add upstream https://github.com/MetaCubeX/ClashMetaForAndroid.git

# Sync updates
git fetch upstream
git checkout -b sync-upstream-$(date +%Y%m%d)
git merge upstream/main

# After resolving conflicts
git checkout sublinks
git merge sync-upstream-$(date +%Y%m%d)
git push origin sublinks
```

See [Upstream Sync Guide](docs/UPSTREAM_SYNC.md) for details.

## Key Differences from Upstream

### Modified Files
- `build.gradle.kts` - App ID and version config
- `app/build.gradle.kts` - API URL config and APK naming
- `app/src/main/java/com/github/kr328/clash/SubLinksService.kt` - SubLinks API integration
- `app/src/main/java/com/github/kr328/clash/LoginActivity.kt` - Login screen
- `design/src/main/res/` - UI resources and strings

### Added Features
- SubLinks account system integration
- Automatic subscription sync
- Hero card personalization
- Time-based greeting and Hitokoto quotes

## Contributing

Issues and pull requests are welcome!

### Development Workflow
1. Fork this repo
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under GPL-3.0. See [LICENSE](LICENSE) for details.

## Acknowledgments

- [ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid) - Upstream project
- [Clash Meta (mihomo)](https://github.com/MetaCubeX/mihomo) - Core engine
- [Hitokoto](https://hitokoto.cn/) - Hitokoto API

## Contact

- Issues: [GitHub Issues](https://github.com/elysiawen/sublinks-client-android/issues)
