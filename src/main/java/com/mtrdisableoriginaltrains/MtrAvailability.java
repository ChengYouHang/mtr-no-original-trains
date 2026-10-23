package com.mtrdisableoriginaltrains;

/**
 * 判断 MTR 本体是否在运行时存在。
 *
 * <p>用类名反射探测而不是直接 {@code import org.mtr.*}：本模组对 MTR 只有 {@code compileOnly}
 * 依赖，若用户没装 MTR，直接引用会在类加载阶段抛 {@code NoClassDefFoundError}。
 * 反射探测失败只会返回 false，主界面按钮就不出现，不会崩游戏。
 */
public final class MtrAvailability {

    private static volatile Boolean cached;

    private MtrAvailability() {
    }

    public static boolean isPresent() {
        Boolean value = cached;
        if (value == null) {
            try {
                Class.forName("org.mtr.mod.client.CustomResourceLoader", false,
                        MtrAvailability.class.getClassLoader());
                value = Boolean.TRUE;
            } catch (Throwable t) {
                value = Boolean.FALSE;
            }
            cached = value;
        }
        return value;
    }
}
