package com.mtrdisableoriginaltrains;

/**
 * 反射探测 MTR 是否已加载。
 *
 * <p>直接用 {@code import org.mtr.mod.client.CustomResourceLoader} 会在 MTR 缺失时
 * 抛 {@code NoClassDefFoundError}（类校验阶段就炸，catch 不住）。
 * 因此统一走 {@link Class#forName}，失败返回 false。
 */
public final class MtrAvailability {

    private static volatile Boolean present;

    private MtrAvailability() {
    }

    public static boolean isPresent() {
        Boolean cached = present;
        if (cached == null) {
            cached = probe();
            present = cached;
        }
        return cached;
    }

    private static boolean probe() {
        try {
            Class.forName("org.mtr.mod.client.CustomResourceLoader", false,
                    MtrAvailability.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
