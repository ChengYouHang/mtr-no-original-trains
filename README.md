# No Original Trains

**原版列车移除** —— 移除 [MTR (Minecraft Transit Railway)](https://www.minecrafttransitrailway.com/) 4.0.5 内置的原版风格列车，并修复追加包列车的车端连接件。**仅客户端**，Minecraft 1.20.1，支持 **Fabric / Forge** 两种加载器。

![icon](src/main/resources/icon.png)

## 功能

- **纯黑名单移除** — 只移除内置清单中的 378 个原版列车：不进创造模式车辆列表、不自然生成、不加载其模型与贴图（省内存）。
- **自动放行追加包** — 运行时自动扫描所有资源包，凡是被 MTR 本体之外的资源包声明过的车辆 ID 一律不拦截。装任何新追加包都**不需要改配置**。
- **连接件（风挡）修复** — 自动纠正追加包写错的 gangway 贴图路径，并让车厢之间每一处接缝都渲染连接件；追加包 zip 一个字节都不用改。

## 安装

**Fabric**：安装 Fabric Loader ≥ 0.14.21、MTR 4.0.5 或更新版本；
**Forge**：安装 Forge 47.x、MTR 4.0.5（固定版本）。

把对应的 `mtr_no_original_trains-x.x.x.jar` 放进 `mods/`。两个加载器的 jar **不要同时装**。

## 从源码构建

需要 JDK 17。

```bash
# Fabric：先把 MTR-fabric-4.0.5+1.20.1.jar 放到 libs/
gradle build
# 产物在 build/libs/

# Forge：先把 MTR-forge-4.0.5+1.20.1.jar 放到 libs/forge/
cd forge && gradle build
# 产物在 forge/build/libs/
```

## 许可证

[MIT](LICENSE) © 2026 ChengYouHang

## 致谢与第三方声明

本模组图标由 [MTR (Minecraft Transit Railway)](https://www.minecrafttransitrailway.com/) 的官方素材
（`assets/mtr/textures/item/train.png`）派生而来。MTR 以 MIT 许可证发布，版权归 LogisticsCraft 所有：

```
MIT License
Copyright (c) 2021 LogisticsCraft
```

感谢 MTR 团队开发并开源这款模组 —— 没有它，本附属模组也无从存在。
