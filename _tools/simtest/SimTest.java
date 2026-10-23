import com.mtrdisableoriginaltrains.TitleButtonPlacer;

import java.util.ArrayList;
import java.util.List;

/**
 * 离线仿真：各种窗口尺寸 / 控件布局下，验证按钮落点永远在
 * 「Mod 行 ↔ 选项/退出行」之间，绝不掉到退出行下面。
 *
 * 布局仿原版 + Mod Menu + 底部网页链接控件：
 *   4 个竖排单按钮行（单人/多人/Realms/Mod，间距 24）
 *   + 选项/退出行（间距 36，即空隙 16）
 *   + 两行网页链接（在退出行下面）。
 */
public class SimTest {

    record R(int x, int y, int w, int h) implements TitleButtonPlacer.Rect {}

    static List<TitleButtonPlacer.Rect> layout(int W, int H) {
        List<TitleButtonPlacer.Rect> l = new ArrayList<>();
        int top = H / 4 + 48;
        for (int i = 0; i < 4; i++) {           // 单人 / 多人 / Realms / Mod
            l.add(new R(W / 2 - 100, top + i * 24, 200, 20));
        }
        int bottomRow = top + 3 * 24 + 36;      // 选项/退出 行（与 Mod 行空隙 16）
        l.add(new R(W / 2 - 200, bottomRow, 98, 20));   // 语言
        l.add(new R(W / 2 - 100, bottomRow, 98, 20));   // 选项
        l.add(new R(W / 2 + 2, bottomRow, 98, 20));     // 退出
        l.add(new R(W / 2 + 102, bottomRow, 98, 20));   // 辅助功能
        int y = bottomRow + 40;                 // 网页链接（退出行下面）
        l.add(new R(60, y, 120, 10));
        l.add(new R(60, y + 12, 160, 10));
        l.add(new R(W - 220, y, 160, 10));
        return l;
    }

    static void dump(String name, int W, int H, List<TitleButtonPlacer.Rect> occ,
                     TitleButtonPlacer.Plan plan) {
        System.out.println("== " + name + " (" + W + "x" + H + ")");
        if (plan == null) {
            System.out.println("   不显示（正确：没有任何不重叠位置）");
            return;
        }
        TitleButtonPlacer.Placement p = plan.placement;
        // 找出退出行原来的 y（第二宽的多控件行）
        System.out.printf("   按钮 (%d,%d) %dx%d  shift: fromY=%d delta=%d%n",
                p.x, p.y, p.w, p.h, plan.shiftFromY, plan.shiftDelta);
        // 断言：按钮必须落在「选项/退出行」顶部或其上方（退出行 = 最下面的 90~110px 宽按钮行）
        int optionsY = -1;
        for (TitleButtonPlacer.Rect r : occ) {
            if (r.w() >= 90 && r.w() <= 110 && r.y() > optionsY) optionsY = r.y();
        }
        if (optionsY >= 0 && p.y + p.h > optionsY && plan.shiftDelta == 0) {
            System.out.println("   *** FAIL：按钮底边超过了退出行顶部，且没有下移任何控件！");
            System.exit(1);
        }
        if (plan.shiftDelta > 0) {
            // 检查下移后所有控件不超屏
            int maxBottom = 0;
            for (TitleButtonPlacer.Rect r : occ) {
                int b = r.y() >= plan.shiftFromY ? r.y() + r.h() + plan.shiftDelta : r.y() + r.h();
                maxBottom = Math.max(maxBottom, b);
            }
            if (maxBottom > H - 2) {
                System.out.println("   *** FAIL：下移后有控件超出屏幕下缘 maxBottom=" + maxBottom);
                System.exit(1);
            }
        }
        System.out.println("   OK");
    }

    public static void main(String[] args) {
        int[][] sizes = {
                {1280, 720},   // 大窗口
                {640, 360},    // 之前实测的 Fabric 布局
                {541, 325},    // 用户截图那种
                {427, 240},    // 之前实测的 Forge 小窗口
                {320, 240},    // 极小窗口
                {320, 180},    // 疯狂小窗口
        };
        for (int[] s : sizes) {
            List<TitleButtonPlacer.Rect> occ = layout(s[0], s[1]);
            dump("标准布局", s[0], s[1], occ, TitleButtonPlacer.find(s[0], s[1], occ));
        }
        // 空界面
        dump("空界面", 640, 360, new ArrayList<>(),
                TitleButtonPlacer.find(640, 360, new ArrayList<>()));
        // 没有 Mod 行（全是并排行）
        List<TitleButtonPlacer.Rect> weird = new ArrayList<>();
        weird.add(new R(100, 100, 200, 20));
        weird.add(new R(310, 100, 200, 20));
        weird.add(new R(100, 140, 200, 20));
        weird.add(new R(310, 140, 200, 20));
        dump("无单按钮行", 640, 360, weird, TitleButtonPlacer.find(640, 360, weird));
        // Forge 真实布局（427x240 实测）：Realms 和 模组 并排一行，底部一个 143px 链接
        List<TitleButtonPlacer.Rect> forge = new ArrayList<>();
        forge.add(new R(113, 108, 200, 20));   // 单人游戏
        forge.add(new R(113, 132, 200, 20));   // 多人游戏
        forge.add(new R(113, 156, 98, 20));    // Realms
        forge.add(new R(215, 156, 98, 20));    // 模组
        forge.add(new R(13, 192, 98, 20));     // 语言
        forge.add(new R(113, 192, 98, 20));    // 选项
        forge.add(new R(215, 192, 98, 20));    // 退出
        forge.add(new R(317, 192, 98, 20));    // 辅助功能
        forge.add(new R(142, 230, 143, 10));   // 网页链接
        dump("Forge真实布局", 427, 240, forge, TitleButtonPlacer.find(427, 240, forge));
        System.out.println("ALL PASS");
    }
}
