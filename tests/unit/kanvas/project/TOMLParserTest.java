package kanvas.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TOMLParserTest {
    private static Map<String, Object> parse(String toml) throws KanvasProjectException { return TOMLParser.parse(toml, "kanvas.toml"); }

    private static Object get(Map<String, Object> table, String path) {
        Object current = table;
        for (String part : path.split("\\.")) {
            if (current instanceof List<?> list) current = list.get(Integer.parseInt(part));
            else current = ((Map<?, ?>) current).get(part);
        }
        return current;
    }

    @Test
    void parsesTablesArraysAndInlineTables() throws Exception {
        Map<String, Object> toml = parse("""
            title = "TOML Example"
            [owner]
            dob = 1979-05-27T07:32:00-08:00
            [database]
            ports = [ 8000, 8001, 8002 ]
            data = [ ["delta", "phi"], [3.14] ]
            temp_targets = { cpu = 79.5, case = 72.0 }
            [servers.alpha]
            ip = "10.0.0.1"
            [[products]]
            name = "Hammer"
            [[products]]
            [[products]]
            name = "Nail"
            """);
        assertEquals("TOML Example", toml.get("title"));
        assertEquals(OffsetDateTime.parse("1979-05-27T07:32:00-08:00"), get(toml, "owner.dob"));
        assertEquals(List.of(8000L, 8001L, 8002L), get(toml, "database.ports"));
        assertEquals(List.of(List.of("delta", "phi"), List.of(3.14)), get(toml, "database.data"));
        assertEquals(79.5, get(toml, "database.temp_targets.cpu"));
        assertEquals("10.0.0.1", get(toml, "servers.alpha.ip"));
        assertEquals(3, ((List<?>) toml.get("products")).size());
        assertEquals(Map.of(), get(toml, "products.1"));
        assertEquals("Nail", get(toml, "products.2.name"));
    }

    @Test
    void parsesDottedAndQuotedKeys() throws Exception {
        Map<String, Object> toml = parse("a.b.c = 1\na.b.d = 2\nsite.\"google.com\" = true\n\"key with spaces\" = 'x'\na . e = 3");
        assertEquals(1L, get(toml, "a.b.c"));
        assertEquals(2L, get(toml, "a.b.d"));
        assertEquals(3L, get(toml, "a.e"));
        assertEquals(true, ((Map<?, ?>) toml.get("site")).get("google.com"));
        assertEquals("x", toml.get("key with spaces"));
    }

    @Test
    void allowsSubtablesOfDottedTablesAndLaterDefinitionOfImplicitTables() throws Exception {
        assertEquals(true, get(parse("[fruit]\napple.color = \"red\"\n[fruit.apple.texture]\nsmooth = true"), "fruit.apple.texture.smooth"));
        assertEquals(2L, get(parse("[a.b.c]\nz = 1\n[a]\nx = 2"), "a.x"));
    }

    @Test
    void parsesNestedArraysOfTables() throws Exception {
        Map<String, Object> toml = parse("""
            [[fruits]]
            name = "apple"
            [fruits.physical]
            color = "red"
            [[fruits.varieties]]
            name = "red delicious"
            [[fruits.varieties]]
            name = "granny smith"
            [[fruits]]
            name = "banana"
            [[fruits.varieties]]
            name = "plantain"
            """);
        assertEquals("granny smith", get(toml, "fruits.0.varieties.1.name"));
        assertEquals("plantain", get(toml, "fruits.1.varieties.0.name"));
        assertEquals("red", get(toml, "fruits.0.physical.color"));
    }

    @Test
    void parsesMultiLineArrays() throws Exception {
        Map<String, Object> toml = parse("""
            deps = [
              "a", # first
              # standalone comment
              "b",
            ]
            mixed = [1, "two", 3.0, [4], { five = 5 }]
            empty = [

            ]
            """);
        assertEquals(List.of("a", "b"), toml.get("deps"));
        assertEquals(5L, get(toml, "mixed.4.five"));
        assertEquals(List.of(), toml.get("empty"));
    }

    @Test
    void parsesStrings() throws Exception {
        Map<String, Object> toml = parse("""
            basic = "tab\\there \\u00E9 \\U0001F600 \\"q\\""
            literal = 'C:\\Users\\path'
            ml = \"\"\"
            The quick \\
               brown \\
            fox\"\"\"
            quotes = \"\"\"a\"\"\"\"\"
            mll = '''
            line1
            line2'''
            empty = ""
            """);
        assertEquals("tab\there \u00E9 \uD83D\uDE00 \"q\"", toml.get("basic"));
        assertEquals("C:\\Users\\path", toml.get("literal"));
        assertEquals("The quick brown fox", toml.get("ml"));
        assertEquals("a\"\"", toml.get("quotes"));
        assertEquals("line1\nline2", toml.get("mll"));
        assertEquals("", toml.get("empty"));
    }

    @Test
    void parsesNumbersAndBooleans() throws Exception {
        Map<String, Object> toml = parse("""
            hex = 0xDEAD_BEEF
            oct = 0o755
            bin = 0b1101
            big = 1_000
            neg = -17
            f1 = +3.5e-2
            f2 = 224_617.445_991
            pinf = inf
            nan = nan
            max = 9223372036854775807
            t = true
            """);
        assertEquals(0xDEADBEEFL, toml.get("hex"));
        assertEquals(493L, toml.get("oct"));
        assertEquals(13L, toml.get("bin"));
        assertEquals(1000L, toml.get("big"));
        assertEquals(-17L, toml.get("neg"));
        assertEquals(0.035, toml.get("f1"));
        assertEquals(224617.445991, toml.get("f2"));
        assertEquals(Double.POSITIVE_INFINITY, toml.get("pinf"));
        assertTrue(((Double) toml.get("nan")).isNaN());
        assertEquals(Long.MAX_VALUE, toml.get("max"));
        assertEquals(true, toml.get("t"));
    }

    @Test
    void parsesDatesAndTimes() throws Exception {
        Map<String, Object> toml = parse("""
            ld = 1979-05-27
            lt = 07:32:00.999999
            odt = 1979-05-27 07:32:00Z
            ldt = 1979-05-27T00:32:00.999999
            precise = 07:32:00.1234567891234
            """);
        assertEquals(LocalDate.of(1979, 5, 27), toml.get("ld"));
        assertEquals(LocalTime.parse("07:32:00.999999"), toml.get("lt"));
        assertEquals(OffsetDateTime.parse("1979-05-27T07:32:00Z"), toml.get("odt"));
        assertEquals(LocalDateTime.parse("1979-05-27T00:32:00.999999"), toml.get("ldt"));
        assertEquals(LocalTime.parse("07:32:00.123456789"), toml.get("precise"));
    }

    @Test
    void handlesCrlfAndByteOrderMark() throws Exception {
        Map<String, Object> toml = parse("\uFEFFa = 1\r\n[t]\r\nb = [\r\n  2,\r\n]\r\ns = \"\"\"x\r\ny\"\"\"\r\n");
        assertEquals(1L, toml.get("a"));
        assertEquals(List.of(2L), get(toml, "t.b"));
        assertEquals("x\ny", get(toml, "t.s"));
    }

    @Test
    void keepsKeyOrder() throws Exception {
        assertEquals(List.of("z", "a", "m"), new ArrayList<>(parse("z = 1\na = 2\nm = 3").keySet()));
    }

    static Stream<Arguments> invalidDocuments() {
        return Stream.of(
            Arguments.of("a = 1\na = 2", "kanvas.toml:2:1: Duplicate key 'a'"),
            Arguments.of("[a]\n[a]", "Table [a] is already defined"),
            Arguments.of("a.b = 1\n[a]", "Table [a] is already defined"),
            Arguments.of("[a.b.c]\nz = 9\n[a]\nb.c.t = 9", "Cannot assign 'b.c.t'"),
            Arguments.of("a = { b = 1 }\na.c = 2", "Cannot assign 'a.c'"),
            Arguments.of("a = { b = 1 }\n[a.c]", "'a' is not a table"),
            Arguments.of("a = []\n[[a]]", "Cannot append to [[a]]"),
            Arguments.of("a = { b = 1, }", "Trailing comma is not allowed"),
            Arguments.of("a = { b = 1\n}", "Inline tables must be on a single line"),
            Arguments.of("a = \"abc", "kanvas.toml:1:5: Unterminated string"),
            Arguments.of("a = \"bad \\q\"", "Invalid escape sequence '\\q'"),
            Arguments.of("a = \"\\uD800\"", "not a valid scalar value"),
            Arguments.of("a = 01", "Invalid value '01'"),
            Arguments.of("a = +0x10", "Invalid value '+0x10'"),
            Arguments.of("a = 9223372036854775808", "is out of range"),
            Arguments.of("a = 1 b = 2", "kanvas.toml:1:7: Expected end of line"),
            Arguments.of("= 1", "Expected a key"),
            Arguments.of("a = ", "Expected a value"),
            Arguments.of("a = [1 2]", "Expected ',' or ']' in array"),
            Arguments.of("a = 2021-02-30", "Invalid date-time"),
            Arguments.of("a = 1\rb = 2", "carriage return without a line feed"),
            Arguments.of("a = \"x\u0001y\"", "Control character U+0001"),
            Arguments.of("a = TRUE", "Invalid value 'TRUE'"));
    }

    @ParameterizedTest
    @MethodSource("invalidDocuments")
    void rejectsInvalidDocuments(String toml, String expectedMessage) {
        KanvasProjectException error = assertThrows(KanvasProjectException.class, () -> parse(toml));
        assertTrue(error.getMessage().contains(expectedMessage), error.getMessage());
    }
}
