# 澄盾 ClearGuard

<img src="app/src/main/assets/logo.svg" alt="澄盾 SVG 图标" width="112">

清晰可控的 Android 安全工具。基于 YouLong Security 开源快照重构，保留守护、紧急逃生、应用治理、广告跳过、屏幕滤镜、性能工具、Shizuku/Stellar 和诊断能力。

- 五页完整本地界面：总览、守护、应用、工具、设置。支持浅色/深色，页面不加载远程脚本。
- 原生终端：明确确认后执行命令，可停止运行，限制时间和输出大小。
- 无线调试：配对后复用已授权会话，先尝试已知端口再发现端口，验证 Binder 就绪后报告成功。
- 安全修复：管理接口鉴权、拒绝状态保留、包名及签名身份绑定、用户服务隔离、授权请求限额和一次性消费。

[下载与项目主页](https://huvubb.abrdns.com/) · [GitHub Releases](https://github.com/huvubb/clearguard/releases)

## 构建与测试

JDK 21、Gradle 8.14、Android API 37、NDK 29、CMake 3.22.1。项目保留原有应用 ID 以兼容内置内核的 Binder/Provider 协议，显示名称和图标为澄盾。发布签名独立于原作者，不能覆盖安装官方签名 APK。

```sh
./gradlew :app:assembleRelease
node tests/assets_check.js
python tests/ui_check.py /tmp/clearguard-preview
```

发布包在 `build_out/_app/outputs/apk/release/`，源构建未签名。签名私钥不入仓库。JVM 安全组件检查见 [安全修复说明](docs/SECURITY-FIXES-2026-11-01.md)。

## 来源与许可

本项目由 huvubb 维护，源于 [iill392/youlong-security](https://github.com/iill392/youlong-security)，内置 [Stellar](https://github.com/roro2239/Stellar) 及 Shizuku/AOSP 相关代码。原始项目介绍和完整署名保留于 [上游 README](docs/UPSTREAM_README.md)。

应用自有代码及新前端遵循 AGPL-3.0；Stellar 修改文件遵循 MPL-2.0；Shizuku/AOSP 等组件按 Apache-2.0 等原许可分发。见 [LICENSE](LICENSE)、[NOTICE](NOTICE)、[第三方声明](THIRD_PARTY_NOTICES.md) 和 `LICENSES/`。

原作者私有病毒库数据和服务器未随源码发布，本分支不声称自带该私有数据库。设备权限、ROM 和 Android 版本会影响特权功能，真实状态以应用显示为准。
