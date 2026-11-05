import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 搜索准度评测：用真实包数据，把「旧版子串匹配」和「新版 SearchQuery」放在同一套
 * 标准答案上比精确率 / 召回率，并列出旧版的误报车名。
 *
 * 标准答案由独立规则给出（正则提线路号、原文包含关键词、ID 包含），不复用 SearchQuery。
 */
public class AccuracyEval {

    static final class Item {
        final String pack;
        final String name;
        final String id;
        Item(String pack, String name, String id) { this.pack = pack; this.name = name; this.id = id; }
    }

    static final class Case {
        final String query;
        final String note;
        final Predicate<Item> gt;
        Case(String query, String note, Predicate<Item> gt) {
            this.query = query; this.note = note; this.gt = gt;
        }
    }

    static int lineOf(String name) {
        Matcher m = Pattern.compile("(\\d+)号线").matcher(name);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    static boolean containsCi(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    /** 旧版 1.3.0 前算法：名称或原始 ID 直接整串 contains。 */
    static boolean oldMatch(Item it, String query) {
        final String q = query.trim().toLowerCase(Locale.ROOT);
        return containsCi(it.name, q) || containsCi(it.id, q);
    }

    public static void main(String[] args) throws Exception {
        final List<Item> sh = load(args[0], "上海包");
        final List<Item> hf = load(args[1], "合肥包");
        final List<Item> all = new ArrayList<>();
        all.addAll(sh);
        all.addAll(hf);

        final List<Case> cases = new ArrayList<>();
        // 上海包
        cases.add(new Case("1号线", "上海：线路号边界", it -> it.pack.equals("上海包") && lineOf(it.name) == 1));
        cases.add(new Case("2号线", "上海：线路号边界", it -> it.pack.equals("上海包") && lineOf(it.name) == 2));
        cases.add(new Case("10号线", "上海：两位数线路", it -> it.pack.equals("上海包") && lineOf(it.name) == 10));
        cases.add(new Case("16号线", "上海：两位数线路", it -> it.pack.equals("上海包") && lineOf(it.name) == 16));
        cases.add(new Case("上海", "上海：城市词", it -> it.pack.equals("上海包") && containsCi(it.name, "上海")));
        cases.add(new Case("抹茶", "上海：昵称", it -> it.pack.equals("上海包") && containsCi(it.name, "抹茶")));
        cases.add(new Case("胖头鱼", "上海：昵称", it -> it.pack.equals("上海包") && containsCi(it.name, "胖头鱼")));
        cases.add(new Case("01a05", "上海：车型号(小写)", it -> it.pack.equals("上海包") && containsCi(it.name, "01a05")));
        cases.add(new Case("16A01", "上海：车型号(大写)", it -> it.pack.equals("上海包") && containsCi(it.name, "16a01")));
        cases.add(new Case("上海 16", "上海：空格分词", it -> it.pack.equals("上海包")
                && containsCi(it.name, "上海") && lineOf(it.name) == 16));
        cases.add(new Case("1号", "上海：半截输入(边界+半截)", it -> it.pack.equals("上海包") && lineOf(it.name) == 1));
        cases.add(new Case("16A", "上海：车型号前缀", it -> it.pack.equals("上海包") && containsCi(it.name, "16a")));
        cases.add(new Case("上海地铁", "上海：长词", it -> it.pack.equals("上海包") && containsCi(it.name, "上海地铁")));
        cases.add(new Case("CRH6F", "上海：英文型号整词", it -> it.pack.equals("上海包") && containsCi(it.name, "crh6f")));
        cases.add(new Case("train", "噪声词：不应命中", it -> false));
        // 合肥包
        cases.add(new Case("1号线", "合肥：线路号边界", it -> it.pack.equals("合肥包") && lineOf(it.name) == 1));
        cases.add(new Case("2号线", "合肥：线路号边界", it -> it.pack.equals("合肥包") && lineOf(it.name) == 2));
        cases.add(new Case("3号线", "合肥：线路号边界", it -> it.pack.equals("合肥包") && lineOf(it.name) == 3));
        cases.add(new Case("S1号线", "合肥：字母线路", it -> it.pack.equals("合肥包") && containsCi(it.name, "s1号线")));
        cases.add(new Case("xcan", "合肥：批次词", it -> it.pack.equals("合肥包") && containsCi(it.name, "xcan")));
        cases.add(new Case("hf02", "合肥：按 ID 搜", it -> it.pack.equals("合肥包") && containsCi(it.id, "hf02")));
        cases.add(new Case("合肥", "合肥：城市词", it -> it.pack.equals("合肥包") && containsCi(it.name, "合肥")));
        // 跨包
        cases.add(new Case("2号线", "跨包：两包都应命中 2 号线", it -> lineOf(it.name) == 2));

        final StringBuilder md = new StringBuilder();
        md.append("# 搜索准度评测报告\n\n");
        md.append("- 数据：上海包 ").append(sh.size()).append(" 辆，合肥包 ").append(hf.size())
                .append(" 辆，合计 ").append(all.size()).append(" 辆（真实车名 + 真实 ID）\n");
        md.append("- 标准答案：由独立规则生成（正则提取线路号 / 原文包含 / ID 包含），不经过被测代码\n");
        md.append("- 旧算法：1.3.0 之前 = 名称或原始 ID 整串 contains\n");
        md.append("- 新算法：SearchQuery（数字边界 / 空格分词 / 剥 ID 前缀）\n\n");
        md.append("| 查询 | 场景 | 标准数 | 旧:命中/TP/FP | 旧P | 旧R | 新:命中/TP/FP | 新P | 新R |\n");
        md.append("|---|---|---:|---|---:|---:|---|---:|---:|\n");

        double oldPsum = 0, newPsum = 0, oldRsum = 0, newRsum = 0;
        int n = 0, oldFpTotal = 0, newFpTotal = 0;
        final StringBuilder fps = new StringBuilder("\n## 旧算法误报明细（新版为 0 的项）\n\n");

        for (Case c : cases) {
            final List<Item> pool = c.query.equals("2号线") && c.note.startsWith("跨包") ? all
                    : (c.note.startsWith("合肥") ? hf : sh);
            final List<Item> gt = new ArrayList<>();
            for (Item it : pool) {
                if (c.gt.test(it)) gt.add(it);
            }
            // 旧算法
            final List<Item> oldHits = new ArrayList<>();
            for (Item it : pool) {
                if (oldMatch(it, c.query)) oldHits.add(it);
            }
            int oldTp = countTp(oldHits, gt);
            // 新算法
            final SearchQuery q = new SearchQuery(c.query);
            final List<Item> newHits = new ArrayList<>();
            for (Item it : pool) {
                if (q.score(it.name, it.id) > 0) newHits.add(it);
            }
            int newTp = countTp(newHits, gt);

            final double oldP = oldHits.isEmpty() ? 1.0 : oldTp * 1.0 / oldHits.size();
            final double oldR = gt.isEmpty() ? 1.0 : oldTp * 1.0 / gt.size();
            final double newP = newHits.isEmpty() ? 1.0 : newTp * 1.0 / newHits.size();
            final double newR = gt.isEmpty() ? 1.0 : newTp * 1.0 / gt.size();

            oldPsum += oldP; newPsum += newP; oldRsum += oldR; newRsum += newR; n++;
            oldFpTotal += oldHits.size() - oldTp;
            newFpTotal += newHits.size() - newTp;

            md.append(String.format(Locale.ROOT,
                    "| `%s` | %s | %d | %d/%d/%d | %.0f%% | %.0f%% | %d/%d/%d | %.0f%% | %.0f%% |%n",
                    c.query, c.note, gt.size(),
                    oldHits.size(), oldTp, oldHits.size() - oldTp, oldP * 100, oldR * 100,
                    newHits.size(), newTp, newHits.size() - newTp, newP * 100, newR * 100));

            if (oldHits.size() > oldTp) {
                final Set<String> fpNames = new LinkedHashSet<>();
                for (Item it : oldHits) {
                    if (!gt.contains(it)) fpNames.add(it.name);
                }
                fps.append("- `").append(c.query).append("`：旧版多出 ").append(fpNames.size())
                        .append(" 条，例如 ");
                int shown = 0;
                for (String nm : fpNames) {
                    if (shown == 3) break;
                    if (shown > 0) fps.append("、");
                    fps.append("「").append(nm).append("」");
                    shown++;
                }
                if (fpNames.size() > 3) fps.append(" 等");
                fps.append("\n");
            }
            if (newHits.size() < gt.size()) {
                md.append(String.format(Locale.ROOT, "  - ⚠ 漏检 %d 条（%s）%n",
                        gt.size() - newTp, c.query));
            }
        }

        md.append(String.format(Locale.ROOT,
                "%n**平均**：旧版 P=%.1f%% / R=%.1f%%（误报 %d 条）；新版 P=%.1f%% / R=%.1f%%（误报 %d 条）%n",
                oldPsum / n * 100, oldRsum / n * 100, oldFpTotal,
                newPsum / n * 100, newRsum / n * 100, newFpTotal));
        md.append(fps);
        md.append("\n## 说明\n\n");
        md.append("- 精确率 = 命中里有多少是真正想要的；召回率 = 该找到的找到了多少。\n");
        md.append("- 噪声词（`train`）标准答案为空，旧版会命中全部 248 条，精确率计 0%。\n");

        try (Writer w = new OutputStreamWriter(new FileOutputStream("accuracy-report.md"), StandardCharsets.UTF_8)) {
            w.write(md.toString());
        }
        System.out.println("report written: accuracy-report.md");
        System.out.printf(Locale.ROOT, "cases=%d oldP=%.1f%% oldR=%.1f%% newP=%.1f%% newR=%.1f%%%n",
                n, oldPsum / n * 100, oldRsum / n * 100, newPsum / n * 100, newRsum / n * 100);
        System.out.println("oldFP=" + oldFpTotal + " newFP=" + newFpTotal);
    }

    static int countTp(List<Item> hits, List<Item> gt) {
        int n = 0;
        for (Item h : hits) {
            if (gt.contains(h)) n++;
        }
        return n;
    }

    static List<Item> load(String path, String pack) throws IOException {
        final List<Item> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                final String[] p = line.split("\t");
                if (p.length >= 2) out.add(new Item(pack, p[0], p[1]));
            }
        }
        return out;
    }
}
