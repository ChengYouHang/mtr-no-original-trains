package com.mtrdisableoriginaltrains;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge 版主入口（仅客户端）。
 *
 * <p>真正的逻辑在 {@link com.mtrdisableoriginaltrains.mixin.CustomResourceLoaderMixin} 里，
 * 通过 Mixin 拦截 MTR 的车辆注册，跳过内置原版列车。
 */
@Mod(MtrDisableOriginalTrains.MOD_ID)
public class MtrDisableOriginalTrains {

    public static final String MOD_ID = "mtr_no_original_trains";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public MtrDisableOriginalTrains() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            throw new IllegalStateException("No Original Trains 是纯客户端模组，不能安装在服务端");
        }
        LOGGER.info("[MTR_NoOriginalTrains] 已加载（Forge 版）：将跳过 MTR 内置原版列车（追加包列车不受影响）");
    }
}
