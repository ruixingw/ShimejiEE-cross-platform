# ShimejiEE 跨平台版(简体中文)

简体中文 | [English](README.md)

桌宠 Shimeji 的跨平台移植版,在 [Kilkakon's shimeji](https://kilkakon.com/shimeji/) 和
[LavenderSnek/ShimejiEE-cross-platform](https://github.com/LavenderSnek/ShimejiEE-cross-platform)
的基础上继续开发,补全了 macOS 的窗口互动和原版的各项设置功能,并提供完整的中文界面。

> 本 fork 完全使用 [GLM-5.3](https://z.ai)(Z.ai)vibe coding 完成。

[![Release](https://img.shields.io/badge/release-v2.2.0-blue)](https://github.com/ruixingw/ShimejiEE-cross-platform/releases)

## 功能亮点

- **macOS 原生窗口互动**:桌宠可以抓起、搬动、扔出真实窗口,托盘里的「窗口归还」能把被扔出屏幕的窗口找回来;窗口位置实时避开程序坞和菜单栏
- **交互窗口白名单/黑名单**(和原版 Windows 版一致):桌宠会主动寻找标题匹配的窗口,即使它不在最前面
- **完整的设置窗口**(常规 / 可互动窗口 / 窗口模式 / 关于):缩放(0.1 步进)、透明度、缩放算法(最近邻 / 双三次 / hqx)、各项行为开关、托盘名称等
- **按行为单独开关**:右键桌宠 →「允许的行为」,可禁用镜像集中标记为 `Toggleable` 的行为(与原版 `DisabledBehaviours` 格式互通)
- **窗口模式(沙盒)**:桌宠养在一个窗口里,可配置窗口大小、背景色、背景图(居中/填充/适应/拉伸),适合直播使用
- 暂停/恢复动画(全局和单个)、多屏开关、单实例保护、首启致谢画面、统计信息窗口
- 完整简体中文(以及繁体中文等 20 余种语言)

## 平台支持

| 平台 | 状态 |
|---|---|
| macOS(Apple Silicon / Intel) | ✅ 主要支持,原生渲染 + 窗口互动 |
| Windows | ⚠️ 继承自上游,未充分测试 |
| Linux | ⚠️ 基础可用,窗口互动未实现 |

## 安装(macOS)

1. 从 [Releases](https://github.com/ruixingw/ShimejiEE-cross-platform/releases) 下载 `no-jre` 版本并解压;
2. 安装 **JDK 23 或更高版本**(Homebrew:`brew install openjdk`);
3. 在终端运行:

   ```bash
   cd ShimejiEE
   java -jar ShimejiEE.jar
   ```

4. 首次启动时在托盘菜单选择桌宠(把桌宠包放进 `img/` 目录);
5. **要使用窗口互动(扔窗)功能**,需要授予辅助功能权限:
   系统设置 → 隐私与安全性 → 辅助功能 → 添加运行 Shimeji 的 `java` 程序。程序在需要时会主动弹出引导。

## 目录结构

```
ShimejiEE/
├── ShimejiEE.jar      主程序
├── lib/               原生库(勿动)
├── conf/              配置(含 settings.properties)
├── img/               桌宠包(每个子目录一个)
│   └── icons/         可选:托盘图标(启动时随机选用一个)
└── sound/             全局音效(可选)
```

桌宠包可以从 [shimeji 标签的 DeviantArt](https://www.deviantart.com/tag/shimeji) 等社区下载,放进 `img/` 后在托盘菜单「选择Shimeji」里勾选即可。放进 `img/unused/` 则会隐藏。

## 从源码构建

需要:Python 3.13+、JDK 23+、Maven、CMake、Ninja、[jextract](https://jdk.java.net/jextract/)。

```bash
python3 build.py --jextract <jextract 路径>
```

产物在 `build/ShimejiEE/`。详见 [docs/building.md](docs/building.md)。

## 已知限制

- 多显示器场景未充分测试;
- Linux 的窗口互动(X11/Wayland)尚未实现;
- 原版的主题(NimROD)编辑功能未移植。

## 来源与致谢

本项目站在下列作品的肩膀上:

- **Group Finity**(Yuki Yamada)—— Shimeji 原作者([官网存档](https://web.archive.org/web/20140530231026/http://www.group-finity.com/Shimeji/))
- **shimeji-ee Group** —— 国际化与大量改进
- **[Kilkakon](https://kilkakon.com/shimeji/)** —— 声音、交互动作、日文配置兼容等长期维护([Discord](https://discord.gg/dcJGAn3))
- **[nonowarn](https://github.com/nonowarn/shimeji4mac)** —— 最初的 macOS 实现
- **[TigerHix](https://github.com/TigerHix/shimeji-universal)** —— Windows 64 位支持
- **[LavenderSnek](https://github.com/LavenderSnek/ShimejiEE-cross-platform)** —— 跨平台 fork 与 panama 原生后端

内置的 [hqx-java](https://github.com/Arcnor/hqx-java) 像素缩放算法采用 LGPL-3.0 许可。

## 许可证

沿用上游的许可条款(zlib 式,要求保留署名、标明修改),完整许可链见 [LICENSE.md](LICENSE.md)。对本仓库的修改同样遵循上述条款。
