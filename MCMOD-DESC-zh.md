移除 [MTR] 我的世界铁路 自带的全部内置列车，追加包列车不受影响。仅客户端。

ModID：mtr_no_original_trains
CurseForge：https://www.curseforge.com/minecraft/mc-mods/no-original-trains
MIT 协议开源，源码：{源码地址}

1.2.0 起主菜单会多一个「列车保留选择」按钮（在模组按钮和选项按钮之间），点开勾选想保留的内置列车，确认后重启生效。不改配置也能用，装上就生效。

功能：

- 移除 MTR 自带的全部内置列车（4.0.5 里是 378 个）：车辆列表不显示，模型和贴图不加载
- 追加包列车全部保留，就算和内置列车重名也不会误删
- MTR 更新版本不用等本模组跟进，4.0.0 以上都支持
- 顺手修复部分追加包车厢连接件丢失、紫黑、间距异常的问题

前置：MTR 4.0.0 以上、FerriteCore、ModernFix。后两个是硬前置，缺了模组不加载。

注意：

- 仅客户端，服务端不用装
- 联机时你自己看不到别人放的内置列车，介意别装
- 不支持 NeoForge
- 配置文件 config/mtr_no_original_trains.json：总开关、连接件修复开关、保留/额外移除指定 ID
- 是否生效：日志里搜 [MTR_NoOriginalTrains]
- 删掉模组即可恢复原样，不写存档
