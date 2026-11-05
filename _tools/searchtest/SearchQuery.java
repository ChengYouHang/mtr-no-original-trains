import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 搜索词解析与匹配（1.3.0）。
 *
 * <p>比 1.2.x 的「整串 contains」多做了四件事：
 * <ol>
 *   <li><b>空格分词</b>：{@code 上海 16} 表示同时命中「上海」和「16」，而不是找这七个字连在一起；</li>
 *   <li><b>数字边界</b>：关键词以数字开头时，命中位置前面不能还是数字 ——
 *       搜「2号线」不会再带出「12号线」，搜「1号线」不会再带出「11号线」。
 *       纯数字词还要求后面也不能紧跟数字，所以搜「1」不会命中「16A01」；</li>
 *   <li><b>忽略 ID 固定前缀</b>：{@code mtr_custom_train_} / {@code mtr:} 先剥掉再比，
 *       搜「train」「mtr」不会再命中全部列车；</li>
 *   <li><b>命中质量分</b>：名称全等 &gt; 名称前缀 &gt; 名称包含 &gt; 仅 ID 命中，
 *       搜索结果按分数从高到低排，把「一看就是它」的放最前面。</li>
 * </ol>
 */
public final class SearchQuery {

    /** 空搜索词：一律命中、分数相同，保持原有顺序。 */
    public static final SearchQuery EMPTY = new SearchQuery("");

    /** 匹配前剥掉的 ID 前缀（不含包名/命名空间信息，只会制造噪声）。 */
    private static final String[] ID_PREFIXES = {"mtr_custom_train_", "mtr:"};

    private final List<String> tokens;

    public SearchQuery(String raw) {
        final List<String> out = new ArrayList<>();
        if (raw != null) {
            for (String t : raw.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        }
        this.tokens = out;
    }

    public boolean isEmpty() {
        return this.tokens.isEmpty();
    }

    /**
     * 命中质量分：0 = 不命中；1 = 仅 ID 命中；2 = 名称包含；3 = 名称前缀；4 = 名称全等。
     * 多个关键词时逐词取最佳来源后相加，任一词不命中即为 0。
     */
    public int score(String name, String id) {
        if (isEmpty()) {
            return 1;
        }
        final String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        final String i = normalizeId(id);
        int total = 0;
        for (String token : this.tokens) {
            int best = 0;
            if (!n.isEmpty()) {
                if (n.equals(token)) {
                    best = 4;
                } else if (n.startsWith(token)) {
                    best = 3;
                } else if (indexOf(n, token) >= 0) {
                    best = 2;
                }
            }
            if (best < 3 && !i.isEmpty()) {
                if (i.equals(token)) {
                    best = Math.max(best, 3);
                } else if (best == 0 && indexOf(i, token) >= 0) {
                    best = 1;
                }
            }
            if (best == 0) {
                return 0;
            }
            total += best;
        }
        return total;
    }

    /** 只有名称这一个字段时（包名等）是否命中。 */
    public boolean matches(String text) {
        return score(text, null) > 0;
    }

    /**
     * 名称/车系名本身是否命中全部关键词。
     *
     * <p>用来区分「搜到了这一族」和「只是族里某一辆车被搜到」：
     * 前者应该整族展开给用户看，后者只展开命中的那几辆。
     */
    public boolean hitsName(String name) {
        return score(name, null) > 0;
    }

    private static String normalizeId(String id) {
        if (id == null) {
            return "";
        }
        String s = id.toLowerCase(Locale.ROOT);
        for (String p : ID_PREFIXES) {
            if (s.startsWith(p)) {
                s = s.substring(p.length());
                break;
            }
        }
        return s;
    }

    /** 子串查找，但对数字做边界检查（规则见类注释第 ② 条）。 */
    static int indexOf(String text, String token) {
        final boolean digitStart = isDigit(token.charAt(0));
        final boolean allDigits = isAllDigits(token);
        int from = 0;
        while (from <= text.length() - token.length()) {
            final int i = text.indexOf(token, from);
            if (i < 0) {
                return -1;
            }
            boolean ok = true;
            if (digitStart && i > 0 && isDigit(text.charAt(i - 1))) {
                ok = false;
            }
            final int end = i + token.length();
            if (ok && allDigits && end < text.length() && isDigit(text.charAt(end))) {
                ok = false;
            }
            if (ok) {
                return i;
            }
            from = i + 1;
        }
        return -1;
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!isDigit(s.charAt(i))) {
                return false;
            }
        }
        return !s.isEmpty();
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
