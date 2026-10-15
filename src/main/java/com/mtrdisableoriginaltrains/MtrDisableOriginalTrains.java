package com.mtrdisableoriginaltrains;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 主入口（仅客户端）。
 *
 * 真正的逻辑在 {@link com.mtrdisableoriginaltrains.mixin.CustomResourceLoaderMixin} 里，
 * 通过 Mixin 拦截 MTR 的车辆注册，跳过内置原版列车。
 */
public class MtrDisableOriginalTrains implements ClientModInitializer {

    public static final String MOD_ID = "mtr_no_original_trains";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[MTR_NoOriginalTrains] 已加载：将跳过 MTR 内置原版列车（追加包列车不受影响）");
    }
}
