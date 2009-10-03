/*
 * Copyright (c) 2002-2007, Marc Prud'hommeaux. All rights reserved.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 */
package jline;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * <p>
 * Terminal implementation for Microsoft Windows. Terminal initialization in
 * {@link #initializeTerminal} is accomplished by extracting the
 * <em>jline_<i>version</i>.dll</em>, saving it to the system temporary
 * directoy (determined by the setting of the <em>java.io.tmpdir</em> System
 * property), loading the library, and then calling the Win32 APIs <a
 * href="http://msdn.microsoft.com/library/default.asp?
 * url=/library/en-us/dllproc/base/setconsolemode.asp">SetConsoleMode</a> and
 * <a href="http://msdn.microsoft.com/library/default.asp?
 * url=/library/en-us/dllproc/base/getconsolemode.asp">GetConsoleMode</a> to
 * disable character echoing.
 * </p>
 * <p/>
 * <p>
 * By default, the {@link #readCharacter} method will attempt to test to see if
 * the specified {@link InputStream} is {@link System#in} or a wrapper around
 * {@link FileDescriptor#in}, and if so, will bypass the character reading to
 * directly invoke the readc() method in the JNI library. This is so the class
 * can read special keys (like arrow keys) which are otherwise inaccessible via
 * the {@link System#in} stream. Using JNI reading can be bypassed by setting
 * the <code>jline.WindowsTerminal.disableDirectConsole</code> system property
 * to <code>true</code>.
 * </p>
 *
 * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
 */
public class WindowsTerminal
    extends Terminal
{
    // constants copied from wincon.h

    /**
     * The ReadFile or ReadConsole function returns only when a carriage return
     * character is read. If this mode is disable, the functions return when one
     * or more characters are available.
     */
    private static final int ENABLE_LINE_INPUT = 2;

    /**
     * Characters read by the ReadFile or ReadConsole function are written to
     * the active screen buffer as they are read. This mode can be used only if
     * the ENABLE_LINE_INPUT mode is also enabled.
     */
    private static final int ENABLE_ECHO_INPUT = 4;

    /**
     * CTRL+C is processed by the system and is not placed in the input buffer.
     * If the input buffer is being read by ReadFile or ReadConsole, other
     * control keys are processed by the system and are not returned in the
     * ReadFile or ReadConsole buffer. If the ENABLE_LINE_INPUT mode is also
     * enabled, backspace, carriage return, and linefeed characters are handled
     * by the system.
     */
    private static final int ENABLE_PROCESSED_INPUT = 1;

    /**
     * User interactions that change the size of the console screen buffer are
     * reported in the console's input buffee. Information about these events
     * can be read from the input buffer by applications using
     * theReadConsoleInput function, but not by those using ReadFile
     * orReadConsole.
     */
    private static final int ENABLE_WINDOW_INPUT = 8;

    /**
     * If the mouse pointer is within the borders of the console window and the
     * window has the keyboard focus, mouse events generated by mouse movement
     * and button presses are placed in the input buffer. These events are
     * discarded by ReadFile or ReadConsole, even when this mode is enabled.
     */
    private static final int ENABLE_MOUSE_INPUT = 16;

    /**
     * When enabled, text entered in a console window will be inserted at the
     * current cursor location and all text following that location will not be
     * overwritten. When disabled, all following text will be overwritten. An OR
     * operation must be performed with this flag and the ENABLE_EXTENDED_FLAGS
     * flag to enable this functionality.
     */
    private static final int ENABLE_PROCESSED_OUTPUT = 1;

    /**
     * This flag enables the user to use the mouse to select and edit text. To
     * enable this option, use the OR to combine this flag with
     * ENABLE_EXTENDED_FLAGS.
     */
    private static final int ENABLE_WRAP_AT_EOL_OUTPUT = 2;

    /**
     * On windows terminals, this character indicates that a 'special' key has
     * been pressed. This means that a key such as an arrow key, or delete, or
     * home, etc. will be indicated by the next character.
     */
    public static final int SPECIAL_KEY_INDICATOR = 224;

    /**
     * On windows terminals, this character indicates that a special key on the
     * number pad has been pressed.
     */
    public static final int NUMPAD_KEY_INDICATOR = 0;

    /**
     * When following the SPECIAL_KEY_INDICATOR or NUMPAD_KEY_INDICATOR,
     * this character indicates an left arrow key press.
     */
    public static final int LEFT_ARROW_KEY = 75;

    /**
     * When following the SPECIAL_KEY_INDICATOR or NUMPAD_KEY_INDICATOR
     * this character indicates an
     * right arrow key press.
     */
    public static final int RIGHT_ARROW_KEY = 77;

    /**
     * When following the SPECIAL_KEY_INDICATOR or NUMPAD_KEY_INDICATOR
     * this character indicates an up
     * arrow key press.
     */
    public static final int UP_ARROW_KEY = 72;

    /**
     * When following the SPECIAL_KEY_INDICATOR or NUMPAD_KEY_INDICATOR
     * this character indicates an
     * down arrow key press.
     */
    public static final int DOWN_ARROW_KEY = 80;

    /**
     * When following the SPECIAL_KEY_INDICATOR or NUMPAD_KEY_INDICATOR
     * this character indicates that
     * the delete key was pressed.
     */
    public static final int DELETE_KEY = 83;

    /**
     * When following the SPECIAL_KEY_INDICATOR or NUMPAD_KEY_INDICATOR
     * this character indicates that
     * the home key was pressed.
     */
    public static final int HOME_KEY = 71;

    /**
     * When following the SPECIAL_KEY_INDICATOR or NUMPAD_KEY_INDICATOR
     * this character indicates that
     * the end key was pressed.
     */
    public static final char END_KEY = 79;

    /**
     * When following the SPECIAL_KEY_INDICATOR or NUMPAD_KEY_INDICATOR
     * this character indicates that
     * the page up key was pressed.
     */
    public static final char PAGE_UP_KEY = 73;

    /**
     * When following the SPECIAL_KEY_INDICATOR or NUMPAD_KEY_INDICATOR
     * this character indicates that
     * the page down key was pressed.
     */
    public static final char PAGE_DOWN_KEY = 81;

    /**
     * When following the SPECIAL_KEY_INDICATOR or NUMPAD_KEY_INDICATOR
     * this character indicates that
     * the insert key was pressed.
     */
    public static final char INSERT_KEY = 82;

    /**
     * When following the SPECIAL_KEY_INDICATOR or NUMPAD_KEY_INDICATOR,
     * this character indicates that the escape key was pressed.
     */
    public static final char ESCAPE_KEY = 0;

    private Boolean directConsole;

    private boolean echoEnabled;

    private int originalMode;

    private Thread shutdownHook;

    String encoding = System.getProperty("jline.WindowsTerminal.input.encoding", System.getProperty("file.encoding"));
    ReplayPrefixOneCharInputStream replayStream = new ReplayPrefixOneCharInputStream(encoding);
    InputStreamReader replayReader;

    public WindowsTerminal() {
        String dir = System.getProperty("jline.WindowsTerminal.directConsole");

        if ("true".equals(dir)) {
            directConsole = Boolean.TRUE;
        }
        else if ("false".equals(dir)) {
            directConsole = Boolean.FALSE;
        }

        try {
            replayReader = new InputStreamReader(replayStream, encoding);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private native int getConsoleMode();

    private native void setConsoleMode(final int mode);

    private native int readByte();

    private native int getWindowsTerminalWidth();

    private native int getWindowsTerminalHeight();

    public int readCharacter(final InputStream in) throws IOException {
        // if we can detect that we are directly wrapping the system
        // input, then bypass the input stream and read directly (which
        // allows us to access otherwise unreadable strokes, such as
        // the arrow keys)
        if (directConsole == Boolean.FALSE) {
            return super.readCharacter(in);
        }
        else if ((directConsole == Boolean.TRUE)
            || ((in == System.in) || (in instanceof FileInputStream
            && (((FileInputStream) in).getFD() == FileDescriptor.in)))) {
            return readByte();
        }
        else {
            return super.readCharacter(in);
        }
    }

    public void initializeTerminal() throws Exception {
        loadLibrary("jline");

        originalMode = getConsoleMode();

        setConsoleMode(originalMode & ~ENABLE_ECHO_INPUT);

        // set the console to raw mode
        int newMode = originalMode
            & ~(ENABLE_LINE_INPUT | ENABLE_ECHO_INPUT
            | ENABLE_PROCESSED_INPUT | ENABLE_WINDOW_INPUT);
        echoEnabled = false;
        setConsoleMode(newMode);

        // at exit, restore the original tty configuration (for JDK 1.3+)
        try {
            Thread thread = new Thread()
            {
                public void start() {
                    try {
                        restoreTerminal();
                    }
                    catch (Exception e) {
                        consumeException(e);
                    }
                }
            };
            Runtime.getRuntime().addShutdownHook(thread);
            shutdownHook = thread;
        }
        catch (AbstractMethodError ame) {
            // JDK 1.3+ only method. Bummer.
            consumeException(ame);
        }
    }

    /**
     * Restore the original terminal configuration, which can be used when
     * shutting down the console reader. The ConsoleReader cannot be
     * used after calling this method.
     */
    public void restoreTerminal() throws Exception {
        // restore the old console mode
        setConsoleMode(originalMode);
        resetTerminalIfThis();
        // Remove shutdown hook
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }
            catch (AbstractMethodError ame) {
                // JDK 1.3+ only method. Bummer.
                consumeException(ame);
            }
            catch (IllegalStateException e) {
                // The VM is shutting down, not a big deal
                consumeException(e);
            }
            shutdownHook = null;
        }
    }

    private void loadLibrary(final String name) throws IOException {
        // store the DLL in the temporary directory for the System
        String version = getClass().getPackage().getImplementationVersion();

        if (version == null) {
            version = "";
        }

        version = version.replace('.', '_');

        File f = new File(System.getProperty("java.io.tmpdir"), name + "_"
            + version + ".dll");
        boolean exists = f.isFile(); // check if it already exists

        // extract the embedded jline.dll file from the jar and save
        // it to the current directory
        int bits = 32;

        // check for 64-bit systems and use to appropriate DLL
        if (System.getProperty("os.arch").indexOf("64") != -1) {
            bits = 64;
        }

        InputStream in = new BufferedInputStream(getClass()
            .getResourceAsStream(name + bits + ".dll"));

        try {
            OutputStream fout = new BufferedOutputStream(
                new FileOutputStream(f));
            byte[] bytes = new byte[1024 * 10];

            for (int n = 0; n != -1; n = in.read(bytes)) {
                fout.write(bytes, 0, n);
            }

            fout.close();
        }
        catch (IOException ioe) {
            // We might get an IOException trying to overwrite an existing
            // jline.dll file if there is another process using the DLL.
            // If this happens, ignore errors.
            if (!exists) {
                throw ioe;
            }
        }

        // try to clean up the DLL after the JVM exits
        f.deleteOnExit();

        // now actually load the DLL
        System.load(f.getAbsolutePath());
    }

    public int readVirtualKey(InputStream in) throws IOException {
        int indicator = readCharacter(in);

        // in Windows terminals, arrow keys are represented by
        // a sequence of 2 characters. E.g., the up arrow
        // key yields 224, 72
        if (indicator == SPECIAL_KEY_INDICATOR
            || indicator == NUMPAD_KEY_INDICATOR) {
            int key = readCharacter(in);

            switch (key) {
                case UP_ARROW_KEY:
                    return CTRL_P; // translate UP -> CTRL-P
                case LEFT_ARROW_KEY:
                    return CTRL_B; // translate LEFT -> CTRL-B
                case RIGHT_ARROW_KEY:
                    return CTRL_F; // translate RIGHT -> CTRL-F
                case DOWN_ARROW_KEY:
                    return CTRL_N; // translate DOWN -> CTRL-N
                case DELETE_KEY:
                    return CTRL_QM; // translate DELETE -> CTRL-?
                case HOME_KEY:
                    return CTRL_A;
                case END_KEY:
                    return CTRL_E;
                case PAGE_UP_KEY:
                    return CTRL_K;
                case PAGE_DOWN_KEY:
                    return CTRL_L;
                case ESCAPE_KEY:
                    return CTRL_OB; // translate ESCAPE -> CTRL-[
                case INSERT_KEY:
                    return CTRL_C;
                default:
                    return 0;
            }
        }
        else if (indicator > 128) {
            // handle unicode characters longer than 2 bytes,
            // thanks to Marc.Herbert@continuent.com
            replayStream.setInput(indicator, in);
            // replayReader = new InputStreamReader(replayStream, encoding);
            indicator = replayReader.read();

        }

        return indicator;

    }

    public boolean isSupported() {
        return true;
    }

    /**
     * Windows doesn't support ANSI codes by default; disable them.
     */
    public boolean isANSISupported() {
        return false;
    }

    public boolean getEcho() {
        return false;
    }

    /**
     * Unsupported; return the default.
     *
     * @see Terminal#getTerminalWidth
     */
    public int getTerminalWidth() {
        return getWindowsTerminalWidth();
    }

    /**
     * Unsupported; return the default.
     *
     * @see Terminal#getTerminalHeight
     */
    public int getTerminalHeight() {
        return getWindowsTerminalHeight();
    }

    /**
     * No-op for exceptions we want to silently consume.
     */
    private void consumeException(final Throwable e) {
    }

    /**
     * Whether or not to allow the use of the JNI console interaction.
     */
    public void setDirectConsole(Boolean directConsole) {
        this.directConsole = directConsole;
    }

    /**
     * Whether or not to allow the use of the JNI console interaction.
     */
    public Boolean getDirectConsole() {
        return this.directConsole;
    }

    public synchronized boolean isEchoEnabled() {
        return echoEnabled;
    }

    public synchronized void enableEcho() {
        // Must set these four modes at the same time to make it work fine.
        setConsoleMode(getConsoleMode() | ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT
            | ENABLE_PROCESSED_INPUT | ENABLE_WINDOW_INPUT);
        echoEnabled = true;
    }

    public synchronized void disableEcho() {
        // Must set these four modes at the same time to make it work fine.
        setConsoleMode(getConsoleMode()
            & ~(ENABLE_LINE_INPUT | ENABLE_ECHO_INPUT
            | ENABLE_PROCESSED_INPUT | ENABLE_WINDOW_INPUT));
        echoEnabled = true;
    }

    public InputStream getDefaultBindings() {
        return getClass().getResourceAsStream("windowsbindings.properties");
    }

    /**
     * This is awkward and inefficient, but probably the minimal way to add
     * UTF-8 support to JLine
     *
     * @author <a href="mailto:Marc.Herbert@continuent.com">Marc Herbert</a>
     */
    static class ReplayPrefixOneCharInputStream
        extends InputStream
    {
        byte firstByte;
        int byteLength;
        InputStream wrappedStream;
        int byteRead;

        final String encoding;

        public ReplayPrefixOneCharInputStream(String encoding) {
            this.encoding = encoding;
        }

        public void setInput(int recorded, InputStream wrapped) throws IOException {
            this.byteRead = 0;
            this.firstByte = (byte) recorded;
            this.wrappedStream = wrapped;

            byteLength = 1;
            if (encoding.equalsIgnoreCase("UTF-8")) {
                setInputUTF8(recorded, wrapped);
            }
            else if (encoding.equalsIgnoreCase("UTF-16")) {
                byteLength = 2;
            }
            else if (encoding.equalsIgnoreCase("UTF-32")) {
                byteLength = 4;
            }
        }


        public void setInputUTF8(int recorded, InputStream wrapped) throws IOException {
            // 110yyyyy 10zzzzzz
            if ((firstByte & (byte) 0xE0) == (byte) 0xC0) {
                this.byteLength = 2;
            }
            // 1110xxxx 10yyyyyy 10zzzzzz
            else if ((firstByte & (byte) 0xF0) == (byte) 0xE0) {
                this.byteLength = 3;
            }
            // 11110www 10xxxxxx 10yyyyyy 10zzzzzz
            else if ((firstByte & (byte) 0xF8) == (byte) 0xF0) {
                this.byteLength = 4;
            }
            else {
                throw new IOException("invalid UTF-8 first byte: " + firstByte);
            }
        }

        public int read() throws IOException {
            if (available() == 0) {
                return -1;
            }

            byteRead++;

            if (byteRead == 1) {
                return firstByte;
            }

            return wrappedStream.read();
        }

        /**
         * InputStreamReader is greedy and will try to read bytes in advance. We
         * do NOT want this to happen since we use a temporary/"losing bytes"
         * InputStreamReader above, that's why we hide the real
         * wrappedStream.available() here.
         */
        public int available() {
            return byteLength - byteRead;
        }
    }

}
