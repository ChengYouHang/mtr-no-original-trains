package com.mtrdisableoriginaltrains;

import java.util.ArrayList;
import java.util.List;

/**
 * 在主菜单上为「MTR 列车保留选择」按钮计算落点。
 *
 * <h2>目标位置</h2>
 * 放在「Mod / Realms 那一行」与「选项... / 退出游戏那一行」<b>之间</b>。
 *
 * <h2>难点</h2>
 * 原版主菜单行距 {@code 24}、按钮高 {@code 20}，两行之间通常只有几像素的空隙，
 * 任何 20px 高的按钮都塞不进去。
 *
 * <h2>本类的做法（锚定「Mod 行 ↔ 选项行」）</h2>
 * <ol>
 *   <li>先找出「选项 / 退出行」（最下面的多按钮并排行），它的上一行就是
 *       「Mod 行」（可能是单按钮竖排，也可能是 Realms + Mod 并排）；</li>
 *   <li>按钮只放在 Mod 行与其下一行（选项... / 退出游戏 那一行）之间：</li>
 *   <ul>
 *     <li>空隙 ≥ {@value #TARGET_GAP}px → 直接放入标准高度；</li>
 *     <li>空隙不够 → <b>把选项行及其下方所有控件整体下移</b>，腾出一整行
 *         （下移量受屏幕下缘约束，实在腾不够就收缩自身高度，最小
 *         {@value #MIN_HEIGHT}px）；</li>
 *   </ul>
 *   <li><b>绝不</b>把按钮放到「退出游戏」那一行的下面——哪怕那里有空隙；</li>
 *   <li>找不到 Mod 行（极端布局）才退回「任意相邻两行之间」的通用逻辑。</li>
 * </ol>
 *
 * <p>本类只做纯计算，不碰任何 Minecraft 类；真正移动控件由两端的
 * Mixin 拿着返回的 {@link Plan} 去执行（Fabric 用 {@code setY}，
 * Forge 用 {@code setPosition}），因此跨平台（含 Mac/Linux）零反射、零风险。
 */
public final class TitleButtonPlacer {

    /** 标准按钮高度（原版一致）。 */
    public static final int HEIGHT = 20;
    /** 允许收缩到的最小高度：再矮就点不准了。 */
    public static final int MIN_HEIGHT = 12;
    /** 横向安全边距。 */
    private static final int PAD = 2;
    /** 候选宽度，从宽到窄依次尝试。 */
    private static final int[] WIDTHS = {200, 150, 100};
    /** 下移后的控件与屏幕下缘至少保留的像素。 */
    private static final int BOTTOM_MARGIN = 2;
    /** 腾位时希望达到的空隙高度（标准高度 + 上下各 1px 呼吸空间）。 */
    private static final int TARGET_GAP = HEIGHT + 2;
    /** 「多按钮行」里单个按钮的最小宽度（低于此算图标，不算按钮）。 */
    private static final int MIN_BUTTON_WIDTH = 60;
    /** 「多按钮行」里单个按钮的最大宽度（超过此算整宽按钮/链接，不算并排按钮）。 */
    private static final int MAX_BUTTON_WIDTH = 130;

    private TitleButtonPlacer() {
    }

    /** 一个可选中的矩形区域。 */
    public interface Rect {
        int x();

        int y();

        int w();

        int h();
    }

    /** 简单矩形，用于「虚拟下移后」的碰撞检测副本。 */
    private record SimpleRect(int x, int y, int w, int h) implements Rect {
    }

    /** 一行控件。 */
    private static final class Row {
        final int y;
        final int h;
        final int count;

        Row(int y, int h, int count) {
            this.y = y;
            this.h = h;
            this.count = count;
        }

        int bottom() {
            return y + h;
        }
    }

    /** {@link #find} 的结果。 */
    public static final class Placement {
        /** 按钮 x / y / 宽 / 高。 */
        public final int x;
        public final int y;
        public final int w;
        public final int h;

        Placement(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    /** 计算结果：按钮落点 + 需要整体下移的控件范围。 */
    public static final class Plan {
        /** 按钮落点。 */
        public final Placement placement;
        /** 所有 {@code y >= shiftFromY} 的控件需整体下移 {@code shiftDelta} 像素；0 表示不动。 */
        public final int shiftFromY;
        public final int shiftDelta;

        Plan(Placement placement, int shiftFromY, int shiftDelta) {
            this.placement = placement;
            this.shiftFromY = shiftFromY;
            this.shiftDelta = shiftDelta;
        }
    }

    /**
     * 计算落点（含必要的下移方案）。
     *
     * @param screenW  界面宽（GUI 坐标）
     * @param screenH  界面高（GUI 坐标）
     * @param occupied 当前所有已有控件的矩形
     * @return 计划；实在放不下返回 {@code null}
     */
    public static Plan find(int screenW, int screenH, List<? extends Rect> occupied) {
        final Plan result = compute(screenW, screenH, occupied);
        if (result != null) {
            if (result.shiftDelta > 0) {
                MtrDisableOriginalTrains.LOGGER.info(
                        "[MTR_NoOriginalTrains] 主菜单按钮落点：界面 {}x{}，按钮 ({}, {}) 尺寸 {}x{}，"
                                + "已将 y>={} 的控件整体下移 {}px 腾位；行布局：{}",
                        screenW, screenH, result.placement.x, result.placement.y,
                        result.placement.w, result.placement.h,
                        result.shiftFromY, result.shiftDelta,
                        describeRows(groupRows(occupied), occupied));
            } else {
                MtrDisableOriginalTrains.LOGGER.info(
                        "[MTR_NoOriginalTrains] 主菜单按钮落点：界面 {}x{}，按钮 ({}, {}) 尺寸 {}x{}，无需移动原版控件；行布局：{}",
                        screenW, screenH, result.placement.x, result.placement.y,
                        result.placement.w, result.placement.h,
                        describeRows(groupRows(occupied), occupied));
            }
        } else {
            MtrDisableOriginalTrains.LOGGER.warn(
                    "[MTR_NoOriginalTrains] 主菜单按钮：界面 {}x{} 找不到不重叠的位置（下移也放不下），本次不显示；行布局：{}",
                    screenW, screenH, describeRows(groupRows(occupied), occupied));
        }
        return result;
    }

    private static Plan compute(int screenW, int screenH, List<? extends Rect> occupied) {
        if (occupied.isEmpty()) {
            final int w = WIDTHS[0];
            final int x = (screenW - w) / 2;
            return new Plan(new Placement(x, screenH / 4 + 48, w, HEIGHT), 0, 0);
        }

        final List<Row> rows = groupRows(occupied);

        // ── 正路：锚定「选项/退出 行」正上方的那一行 ──
        // 选项行 = 最下面的「多按钮」行（选项/退出/语言/辅助功能并排）。
        // 它上面一行就是「Mod 行」（可能是单按钮竖排，也可能是 Realms+Mod 并排）。
        final int optionsIdx = findOptionsRow(rows, occupied);
        if (optionsIdx > 0) {
            final Row upper = rows.get(optionsIdx - 1);
            final Row lower = rows.get(optionsIdx);
            final int gap = lower.y - upper.bottom();
            // 空隙已经够标准高度 → 直接放
            if (gap >= TARGET_GAP) {
                final Plan p = fitBetween(screenW, upper, lower, occupied);
                if (p != null) {
                    return p;
                }
            }
            // 空隙不够 → 下移选项行及其下方所有控件，腾出整行
            final Plan shifted = shiftFit(screenW, screenH, upper, lower, occupied);
            if (shifted != null) {
                return shifted;
            }
            // 下移也腾不够（屏幕下缘被顶死）→ 收缩自身高度塞进现有空隙
            final Plan shrunk = fitBetween(screenW, upper, lower, occupied);
            if (shrunk != null) {
                return shrunk;
            }
            // 锚定失败（罕见布局）→ 掉到下面的通用逻辑
        }

        // ── 兜底：任意相邻两行之间（先直接塞，再下移塞）──
        for (int i = rows.size() - 2; i >= 0; i--) {
            final Plan p = fitBetween(screenW, rows.get(i), rows.get(i + 1), occupied);
            if (p != null) {
                return p;
            }
        }
        for (int i = rows.size() - 2; i >= 0; i--) {
            final Plan p = shiftFit(screenW, screenH, rows.get(i), rows.get(i + 1), occupied);
            if (p != null) {
                return p;
            }
        }

        MtrDisableOriginalTrains.LOGGER.warn(
                "[MTR_NoOriginalTrains] 行布局诊断：{}",
                describeRows(rows, occupied));
        return null;
    }

    /** 把行布局拼成人类可读的字符串（诊断用）。 */
    private static String describeRows(List<Row> rows, List<? extends Rect> occupied) {
        final StringBuilder sb = new StringBuilder();
        for (final Row row : rows) {
            int maxW = 0;
            for (final Rect r : occupied) {
                if (r.y() >= row.y && r.y() < row.bottom()) {
                    maxW = Math.max(maxW, r.w());
                }
            }
            sb.append(String.format("[y=%d 高=%d 个数=%d 最宽=%d] ",
                    row.y, row.h, row.count, maxW));
        }
        return sb.toString();
    }

    /**
     * 找「选项 / 退出行」：最下面的「多按钮」行——一行里至少两个控件，
     * 且都是按钮宽度（最宽不超过 {@value #MAX_BUTTON_WIDTH}px、至少
     * {@value #MIN_BUTTON_WIDTH}px）。像「选项... / 退出游戏 / 语言 / 辅助功能」
     * 或「Realms / 模组」并排的行都符合；而单个 200px 宽的竖排主按钮、
     * 底部较宽（>130px）的网页链接行都不符合。
     *
     * @return 行下标；找不到返回 {@code -1}
     */
    private static int findOptionsRow(List<Row> rows, List<? extends Rect> occupied) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            final Row row = rows.get(i);
            if (row.count < 2) {
                continue;
            }
            int maxW = 0;
            for (final Rect r : occupied) {
                if (r.y() >= row.y && r.y() < row.bottom()) {
                    maxW = Math.max(maxW, r.w());
                }
            }
            if (maxW >= MIN_BUTTON_WIDTH && maxW <= MAX_BUTTON_WIDTH) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 在两行之间放按钮（不下移控件）：空隙够高就用标准高度，略窄就收缩高度填满空隙。
     *
     * @return 放不下返回 {@code null}
     */
    private static Plan fitBetween(int screenW, Row upper, Row lower,
                                   List<? extends Rect> occupied) {
        final int from = upper.bottom();
        final int to = lower.y;
        final int gap = to - from;
        // 上下各留 1px 呼吸空间，避免和相邻行的按钮贴成一片
        final int usable = gap - 2;
        if (usable < MIN_HEIGHT) {
            return null;
        }
        final int h = Math.min(HEIGHT, usable);
        final int y = from + (gap - h) / 2;
        final int[] w = pickWidth(screenW, y, h, occupied);
        if (w == null) {
            return null;
        }
        return new Plan(new Placement((screenW - w[0]) / 2, y, w[0], h), 0, 0);
    }

    /**
     * 通过「下移下方控件」在两行之间腾出一整行。
     *
     * <p>把 lower 行及其下方所有控件整体下移，下移量按需计算
     * （目标空隙 {@value #TARGET_GAP}px），但受屏幕下缘约束；
     * 下移量不足时按实际空隙收缩按钮高度，最小 {@value #MIN_HEIGHT}px。
     *
     * @return 腾不出返回 {@code null}
     */
    private static Plan shiftFit(int screenW, int screenH, Row upper, Row lower,
                                 List<? extends Rect> occupied) {
        final int from = upper.bottom();
        final int gap = lower.y - from;
        final int needed = TARGET_GAP - gap;
        if (needed <= 0) {
            return null; // 空隙本来就够，不需要下移
        }
        // 找出需要下移的控件（lower 行及其下所有）里最靠下的底边
        int maxBottom = Integer.MIN_VALUE;
        for (final Rect r : occupied) {
            if (r.y() >= lower.y) {
                maxBottom = Math.max(maxBottom, r.y() + r.h());
            }
        }
        if (maxBottom == Integer.MIN_VALUE) {
            return null;
        }
        final int allowed = (screenH - BOTTOM_MARGIN) - maxBottom;
        final int delta = Math.min(needed, allowed);
        if (delta <= 0) {
            return null; // 屏幕下方没有富余空间，下移不了
        }
        final int newGap = gap + delta;
        final int usable = newGap - 2;
        if (usable < MIN_HEIGHT) {
            return null; // 下移量被屏幕边缘截断得太狠
        }
        final int h = Math.min(HEIGHT, usable);
        final int y = from + (newGap - h) / 2;
        // 用「虚拟下移后」的矩形做碰撞检测
        final List<Rect> shifted = new ArrayList<>(occupied.size());
        for (final Rect r : occupied) {
            shifted.add(r.y() >= lower.y
                    ? new SimpleRect(r.x(), r.y() + delta, r.w(), r.h()) : r);
        }
        final int[] w = pickWidth(screenW, y, h, shifted);
        if (w == null) {
            return null;
        }
        return new Plan(new Placement((screenW - w[0]) / 2, y, w[0], h), lower.y, delta);
    }

    /** 在指定位置按宽度优先级试放，返回 {@code {width}} 或 {@code null}。 */
    private static int[] pickWidth(int screenW, int y, int h, List<? extends Rect> occupied) {
        for (final int w : WIDTHS) {
            final int x = (screenW - w) / 2;
            if (!collides(x, y, w, h, occupied)) {
                return new int[]{w};
            }
        }
        return null;
    }

    /** 把控件按 y 聚成「行」，并统计每行控件数。 */
    private static List<Row> groupRows(List<? extends Rect> occupied) {
        final List<Row> rows = new ArrayList<>();
        final List<Rect> sorted = new ArrayList<>(occupied);
        sorted.sort((a, b) -> Integer.compare(a.y(), b.y()));
        for (final Rect r : sorted) {
            boolean merged = false;
            for (int i = 0; i < rows.size(); i++) {
                final Row rb = rows.get(i);
                if (Math.abs(rb.y - r.y()) <= 2) {
                    rows.set(i, new Row(Math.min(rb.y, r.y()),
                            Math.max(rb.h, r.h()), rb.count + 1));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                rows.add(new Row(r.y(), r.h(), 1));
            }
        }
        rows.sort((a, b) -> Integer.compare(a.y, b.y));
        return rows;
    }

    /**
     * 该横向条带是否与任一控件相交。
     *
     * <p>这里<b>不加</b>上下安全边距：按钮高度本来就是按空隙算出来的，
     * 加了边距会把自己判成与相邻行重叠。横向仍保留 {@value #PAD}px 边距。
     */
    private static boolean collides(int x, int y, int w, int h, List<? extends Rect> occupied) {
        final int left = x - PAD;
        final int right = x + w + PAD;
        for (final Rect r : occupied) {
            final boolean hOverlap = left < r.x() + r.w() && right > r.x();
            final boolean vOverlap = y < r.y() + r.h() && y + h > r.y();
            if (hOverlap && vOverlap) {
                return true;
            }
        }
        return false;
    }
}
