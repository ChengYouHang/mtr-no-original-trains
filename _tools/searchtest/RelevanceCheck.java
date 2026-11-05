import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** 用真实包数据检查搜索相关性：搜「N号线」时不允许出现别的线路号。 */
public class RelevanceCheck {
    public static void main(String[] args) throws Exception {
        final List<String> names = new ArrayList<>();
        final List<String> ids = new ArrayList<>();
        read(new File(args[0]), names, ids);
        System.out.println("样本: " + names.size() + " 辆车 (" + args[0] + ")");
        int bad = 0;
        for (String q : new String[]{"1号线", "2号线", "3号线", "10号线", "16号线"}) {
            SearchQuery sq = new SearchQuery(q);
            Set<String> lines = new TreeSet<>();
            int hit = 0;
            for (int i = 0; i < names.size(); i++) {
                if (sq.score(names.get(i), ids.get(i)) > 0) {
                    hit++;
                    String line = lineOf(names.get(i));
                    if (line != null) {
                        lines.add(line);
                    }
                }
            }
            String want = q.replace("号线", "");
            boolean ok = lines.isEmpty() || lines.equals(Collections.singleton(want));
            System.out.println((ok ? "OK   " : "BAD  ") + "搜「" + q + "」→ " + hit + " 辆，线路: " + lines);
            if (!ok) {
                bad++;
            }
        }
        // 噪声查询：不应命中任何车
        for (String q : new String[]{"train", "mtr", "mtr_custom_train"}) {
            SearchQuery sq = new SearchQuery(q);
            int hit = 0;
            for (int i = 0; i < names.size(); i++) {
                if (sq.score(names.get(i), ids.get(i)) > 0) {
                    hit++;
                }
            }
            System.out.println((hit == 0 ? "OK   " : "BAD  ") + "噪声词「" + q + "」命中 " + hit + " 辆");
            if (hit != 0) {
                bad++;
            }
        }
        // 精确车型：只应命中该车型（样本里没有该车型时跳过）
        boolean sampleHasIt = false;
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).toLowerCase(Locale.ROOT).contains("01a05")
                    || ids.get(i).toLowerCase(Locale.ROOT).contains("01a05")) {
                sampleHasIt = true;
                break;
            }
        }
        if (!sampleHasIt) {
            System.out.println("SKIP 搜「01a05」——该包没有这个车型");
        } else {
            SearchQuery sq = new SearchQuery("01a05");
            Set<String> hitNames = new TreeSet<>();
            for (int i = 0; i < names.size(); i++) {
                if (sq.score(names.get(i), ids.get(i)) > 0) {
                    hitNames.add(names.get(i));
                }
            }
            boolean ok = hitNames.size() == 1 && hitNames.iterator().next().contains("01A05");
            System.out.println((ok ? "OK   " : "BAD  ") + "搜「01a05」→ 车型: " + hitNames);
            if (!ok) {
                bad++;
            }
        }
        System.out.println("----\n" + (bad == 0 ? "全部通过" : bad + " 项异常"));
        if (bad > 0) {
            System.exit(1);
        }
    }

    static String lineOf(String name) {
        Matcher m = Pattern.compile("(\\d+)号线").matcher(name);
        return m.find() ? m.group(1) : null;
    }

    static void read(File f, List<String> names, List<String> ids) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                if (p.length >= 2) {
                    names.add(p[0]);
                    ids.add(p[1]);
                }
            }
        }
    }
}
