package kanvas.dependency;

import java.math.BigInteger;
import java.util.*;

public final class MavenVersion implements Comparable<MavenVersion> {
    private static final Map<String, Integer> QUALIFIERS = Map.ofEntries(
        Map.entry("alpha", 0), Map.entry("a", 0), Map.entry("beta", 1), Map.entry("b", 1),
        Map.entry("milestone", 2), Map.entry("m", 2), Map.entry("rc", 3), Map.entry("cr", 3),
        Map.entry("snapshot", 4), Map.entry("", 5), Map.entry("ga", 5), Map.entry("final", 5), Map.entry("release", 5),
        Map.entry("sp", 6));
    private static final int RELEASE = 5, UNKNOWN = 7;

    private final String text;
    private final List<Object> items = new ArrayList<>(); // BigInteger or String

    public MavenVersion(String text) {
        this.text = text;
        StringBuilder token = new StringBuilder();
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            boolean separator = ((c == '.') || (c == '-') || (c == '_'));
            boolean switches = (!token.isEmpty() && Character.isDigit(c) != Character.isDigit(token.charAt(token.length() - 1)));
            if (separator || switches) {
                add(token);
                token.setLength(0);
            }
            if (!separator) token.append(c);
        }
        add(token);
        while (!items.isEmpty() && isPadding(items.getLast())) items.removeLast();
    }

    private void add(StringBuilder token) {
        if (token.isEmpty()) return;
        String value = token.toString();
        items.add(Character.isDigit(value.charAt(0)) ? new BigInteger(value) : value);
    }

    private static boolean isPadding(Object item) {
        return (item instanceof BigInteger n) ? (n.signum() == 0)
        : ((QUALIFIERS.get((String) item) instanceof Integer rank) && (rank == RELEASE));
    }

    @Override
    public int compareTo(MavenVersion other) {
        for (int i = 0; i < Math.max(items.size(), other.items.size()); i++) {
            int result = compareItems(((i < items.size()) ? items.get(i) : null), ((i < other.items.size()) ? other.items.get(i) : null));
            if (result != 0) return result;
        }
        return 0;
    }

    private static int compareItems(Object a, Object b) {
        if (a == null) return -compareItems(b, null);
        if (a instanceof BigInteger x) {
            if (b == null) return x.signum();
            return ((b instanceof BigInteger y) ? x.compareTo(y) : 1);
        }
        String q = (String) a;
        if (b == null) return Integer.compare(rank(q), RELEASE);
        if (b instanceof BigInteger) return -1;
        String r = (String) b;
        int byRank = Integer.compare(rank(q), rank(r));
        return ((byRank != 0) ? byRank : q.compareTo(r));
    }

    private static int rank(String qualifier) { return QUALIFIERS.getOrDefault(qualifier, UNKNOWN); }
    public static String highest(Collection<String> versions) { return versions.stream().max(Comparator.comparing(MavenVersion::new)).orElse(null); }

    @Override
    public boolean equals(Object o) { return o instanceof MavenVersion v && compareTo(v) == 0; }
    @Override
    public int hashCode() { return items.hashCode(); }
    @Override
    public String toString() { return text; }
}
