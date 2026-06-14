package kanvas.tui;

import java.lang.foreign.*;
import java.lang.invoke.*;
import kanvas.libs.math.KVector2;

public class Terminal {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final boolean IS_MAC     = System.getProperty("os.name").toLowerCase().contains("mac");

    private static boolean rawMode = false;

    // Windows saved state
    private static int savedInputMode;
    private static int savedOutputMode;
    private static MemorySegment winInputHandle;
    private static MemorySegment winOutputHandle;

    // Unix saved state — arena must outlive enable() so termios stays valid until disable()
    private static Arena unixArena;
    private static MemorySegment savedTermios;

    // ── Windows console mode flags ────────────────────────────────────────────
    private static final int ENABLE_LINE_INPUT               = 0x0002;
    private static final int ENABLE_ECHO_INPUT               = 0x0004;
    private static final int ENABLE_WINDOW_INPUT             = 0x0008;
    private static final int ENABLE_VIRTUAL_TERMINAL_INPUT   = 0x0200;
    private static final int ENABLE_PROCESSED_OUTPUT         = 0x0001;
    private static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;

    // ── Unix termios constants ────────────────────────────────────────────────
    // c_lflag bits to clear: ISIG, ICANON, ECHO, IEXTEN (values differ per OS)
    private static final int LFLAG_CLEAR_LINUX = 0x0001 | 0x0002 | 0x0008 | 0x8000;
    private static final int LFLAG_CLEAR_MAC   = 0x0080 | 0x0100 | 0x0008 | 0x0400;
    // c_iflag bits to clear: BRKINT, INPCK, ISTRIP, ICRNL, IXON (IXON differs)
    private static final int IFLAG_CLEAR_LINUX = 0x0002 | 0x0010 | 0x0020 | 0x0100 | 0x0400;
    private static final int IFLAG_CLEAR_MAC   = 0x0002 | 0x0010 | 0x0020 | 0x0100 | 0x0200;
    // CS8 bits (CSIZE differs between Linux and macOS)
    private static final int CS8_LINUX = 0x30;
    private static final int CS8_MAC   = 0x300;
    private static final int OPOST     = 0x1;  // c_oflag: disable output processing
    private static final int TCSAFLUSH = 2;

    // termios struct layout (44 bytes on both Linux and macOS)
    //   offsets: c_iflag=0, c_oflag=4, c_cflag=8, c_lflag=12
    //   Linux: c_line=16 (1 byte), c_cc[19] starts at 17, VTIME=cc[5]=22, VMIN=cc[6]=23
    //   macOS: c_cc[20] starts at 16,                     VTIME=cc[17]=33, VMIN=cc[16]=32
    private static final int TERMIOS_SIZE  = 44;
    private static final int OFF_IFLAG     = 0;
    private static final int OFF_OFLAG     = 4;
    private static final int OFF_CFLAG     = 8;
    private static final int OFF_LFLAG     = 12;
    private static final int OFF_VTIME_LINUX = 22;
    private static final int OFF_VMIN_LINUX  = 23;
    private static final int OFF_VTIME_MAC   = 33;
    private static final int OFF_VMIN_MAC    = 32;

    static {
        // Restore terminal on JVM exit, even if disable() was never called explicitly
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (rawMode) {
                exitAltScreen();
                showCursor();
                disable();
            }
        }));
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static void enable() {
        if (rawMode) return;
        try {
            if (IS_WINDOWS) enableWindows();
            else enableUnix();
            rawMode = true;
        } catch (Throwable t) {
            System.err.println("Failed to enable raw mode: " + t.getMessage());
        }
    }

    public static void disable() {
        if (!rawMode) return;
        try {
            if (IS_WINDOWS) disableWindows();
            else disableUnix();
        } catch (Throwable t) {
            System.err.println("Failed to disable raw mode: " + t.getMessage());
        }
        rawMode = false;
    }

    public static boolean isRawMode() { return rawMode; }

    public static KVector2 getSize() {
        try {
            return IS_WINDOWS ? getSizeWindows() : getSizeUnix();
        } catch (Throwable t) {
            return new KVector2(80, 24);
        }
    }

    public static void enterAltScreen() { System.out.print("\033[?1049h"); System.out.flush(); }
    public static void exitAltScreen()  { System.out.print("\033[?1049l"); System.out.flush(); }
    public static void hideCursor()     { System.out.print("\033[?25l");   System.out.flush(); }
    public static void showCursor()     { System.out.print("\033[?25h");   System.out.flush(); }

    // ── Windows ───────────────────────────────────────────────────────────────

    private static void enableWindows() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", arena);

            MethodHandle getStdHandle = linker.downcallHandle(
                kernel32.find("GetStdHandle").get(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );
            MethodHandle getConsoleMode = linker.downcallHandle(
                kernel32.find("GetConsoleMode").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );
            MethodHandle setConsoleMode = linker.downcallHandle(
                kernel32.find("SetConsoleMode").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );

            // Handles returned by GetStdHandle are kernel objects — global scope, not arena-scoped
            winInputHandle  = (MemorySegment) getStdHandle.invoke(-10); // STD_INPUT_HANDLE
            winOutputHandle = (MemorySegment) getStdHandle.invoke(-11); // STD_OUTPUT_HANDLE

            MemorySegment modeBuf = arena.allocate(ValueLayout.JAVA_INT);

            getConsoleMode.invoke(winInputHandle, modeBuf);
            savedInputMode = modeBuf.get(ValueLayout.JAVA_INT, 0);
            int rawInput = (savedInputMode & ~ENABLE_LINE_INPUT & ~ENABLE_ECHO_INPUT)
                    | ENABLE_VIRTUAL_TERMINAL_INPUT | ENABLE_WINDOW_INPUT;
            setConsoleMode.invoke(winInputHandle, rawInput);

            getConsoleMode.invoke(winOutputHandle, modeBuf);
            savedOutputMode = modeBuf.get(ValueLayout.JAVA_INT, 0);
            int rawOutput = savedOutputMode | ENABLE_PROCESSED_OUTPUT | ENABLE_VIRTUAL_TERMINAL_PROCESSING;
            setConsoleMode.invoke(winOutputHandle, rawOutput);
        }
    }

    private static void disableWindows() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", arena);

            MethodHandle setConsoleMode = linker.downcallHandle(
                kernel32.find("SetConsoleMode").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );
            setConsoleMode.invoke(winInputHandle, savedInputMode);
            setConsoleMode.invoke(winOutputHandle, savedOutputMode);
        }
    }

    private static KVector2 getSizeWindows() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", arena);

            MethodHandle getStdHandle = linker.downcallHandle(
                kernel32.find("GetStdHandle").get(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );
            MethodHandle getConsoleScreenBufferInfo = linker.downcallHandle(
                kernel32.find("GetConsoleScreenBufferInfo").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            MemorySegment handle     = (MemorySegment) getStdHandle.invoke(-11);
            MemorySegment bufferInfo = arena.allocate(22); // sizeof(CONSOLE_SCREEN_BUFFER_INFO)
            int success = (int) getConsoleScreenBufferInfo.invoke(handle, bufferInfo);
            if (success != 0) {
                // srWindow: Left(10), Top(12), Right(14), Bottom(16)
                short left   = bufferInfo.get(ValueLayout.JAVA_SHORT, 10);
                short top    = bufferInfo.get(ValueLayout.JAVA_SHORT, 12);
                short right  = bufferInfo.get(ValueLayout.JAVA_SHORT, 14);
                short bottom = bufferInfo.get(ValueLayout.JAVA_SHORT, 16);
                return new KVector2((right - left) + 1, (bottom - top) + 1);
            }
        }
        return new KVector2(80, 24);
    }

    // ── Unix ──────────────────────────────────────────────────────────────────

    private static void enableUnix() throws Throwable {
        unixArena = Arena.ofShared(); // must outlive this method — closed in disableUnix()
        Linker linker = Linker.nativeLinker();
        SymbolLookup libc = linker.defaultLookup();

        MethodHandle tcgetattr = linker.downcallHandle(
            libc.find("tcgetattr").get(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        MethodHandle tcsetattr = linker.downcallHandle(
            libc.find("tcsetattr").get(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );

        savedTermios = unixArena.allocate(TERMIOS_SIZE);
        tcgetattr.invoke(0, savedTermios); // fd 0 = stdin

        MemorySegment raw = unixArena.allocate(TERMIOS_SIZE);
        MemorySegment.copy(savedTermios, 0, raw, 0, TERMIOS_SIZE);

        int iflagClear  = IS_MAC ? IFLAG_CLEAR_MAC  : IFLAG_CLEAR_LINUX;
        int lflagClear  = IS_MAC ? LFLAG_CLEAR_MAC  : LFLAG_CLEAR_LINUX;
        int cs8         = IS_MAC ? CS8_MAC           : CS8_LINUX;
        int vtimeOffset = IS_MAC ? OFF_VTIME_MAC     : OFF_VTIME_LINUX;
        int vminOffset  = IS_MAC ? OFF_VMIN_MAC      : OFF_VMIN_LINUX;

        raw.set(ValueLayout.JAVA_INT, OFF_IFLAG, raw.get(ValueLayout.JAVA_INT, OFF_IFLAG) & ~iflagClear);
        raw.set(ValueLayout.JAVA_INT, OFF_OFLAG, raw.get(ValueLayout.JAVA_INT, OFF_OFLAG) & ~OPOST);
        raw.set(ValueLayout.JAVA_INT, OFF_CFLAG, raw.get(ValueLayout.JAVA_INT, OFF_CFLAG) | cs8);
        raw.set(ValueLayout.JAVA_INT, OFF_LFLAG, raw.get(ValueLayout.JAVA_INT, OFF_LFLAG) & ~lflagClear);
        raw.set(ValueLayout.JAVA_BYTE, vtimeOffset, (byte) 0); // VTIME=0: no timeout
        raw.set(ValueLayout.JAVA_BYTE, vminOffset,  (byte) 1); // VMIN=1: block until 1 byte

        tcsetattr.invoke(0, TCSAFLUSH, raw);
    }

    private static void disableUnix() throws Throwable {
        Linker linker = Linker.nativeLinker();
        MethodHandle tcsetattr = linker.downcallHandle(
            linker.defaultLookup().find("tcsetattr").get(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        tcsetattr.invoke(0, TCSAFLUSH, savedTermios);
        unixArena.close();
        unixArena = null;
        savedTermios = null;
    }

    private static KVector2 getSizeUnix() {
        try {
            Process p = new ProcessBuilder("sh", "-c", "stty size < /dev/tty").start();
            try (java.util.Scanner sc = new java.util.Scanner(p.getInputStream())) {
                if (sc.hasNextLine()) {
                    String[] parts = sc.nextLine().trim().split("\\s+");
                    return new KVector2(Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
                }
            }
        } catch (Exception ignored) {}
        return new KVector2(80, 24);
    }
}
