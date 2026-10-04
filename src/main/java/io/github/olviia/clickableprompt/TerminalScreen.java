package io.github.olviia.clickableprompt;

import java.awt.Component;
import java.awt.Point;
import javax.swing.SwingUtilities;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one door to the IDE's terminal (JediTerm): reads the screen, the caret and the selection,
 * converts mouse points to cells, and types keys into the program running in the terminal.
 * <p>
 * JediTerm's classes are not a stable public API and may live in another class loader, so this
 * adapter reaches them by reflection on the live objects. If JetBrains changes them, this is
 * the only class to fix; {@link #of} then returns null and the plugin quietly does nothing.
 */
final class TerminalScreen {

    private static final String PANEL = "com.jediterm.terminal.ui.TerminalPanel";
    private static final String WIDGET = "com.jediterm.terminal.ui.JediTermWidget";

    private final Object panel;
    private final Object widget;

    private TerminalScreen(Object panel, Object widget) {
        this.panel = panel;
        this.widget = widget;
    }

    /** The terminal {@code component} belongs to, or null when it is not inside a JediTerm terminal. */
    static TerminalScreen of(Component component) {
        Object panel = null;
        for (Component c = component; c != null; c = c.getParent()) {
            if (panel == null && isA(c.getClass(), PANEL)) panel = c;
            if (panel != null && isA(c.getClass(), WIDGET)) return new TerminalScreen(panel, c);
        }
        return null;
    }

    /** True while the program asked for mouse events itself; then clicks are its business. */
    boolean programWantsMouse() {
        Object result = call(panel, "isMouseReporting");
        return Boolean.TRUE.equals(result);
    }

    /** Converts {@code point} from {@code source}'s coordinates into the terminal panel's. */
    Point toPanel(Component source, Point point) {
        return SwingUtilities.convertPoint(source, point, (Component) panel);
    }

    /** The cell under {@code point} (panel coordinates), rows counted from the top of the screen. */
    Cell cellAt(Point point) {
        return pointToCell(call(panel, "panelToCharCoords", point));
    }

    /** Where the terminal cursor is. */
    Cell cursor() {
        Object terminal = call(widget, "getTerminal");
        Object position = call(terminal, "getCursorPosition");
        if (position == null) return null;
        int x = (Integer) call(position, "getX");
        int y = (Integer) call(position, "getY");
        return new Cell(x - 1, y - 1);
    }

    /** The text of every screen row, top to bottom. */
    List<String> lines() {
        Object buffer = call(widget, "getTerminalTextBuffer");
        int height = (Integer) call(buffer, "getHeight");
        List<String> lines = new ArrayList<>(height);
        call(buffer, "lock");
        try {
            for (int row = 0; row < height; row++) lines.add((String) call(call(buffer, "getLine", row), "getText"));
        } finally {
            call(buffer, "unlock");
        }
        return lines;
    }

    /**
     * {@code lines} with only what the user typed inside {@code box}: dimmed cells (a suggestion
     * shown in an empty prompt, like Claude Code's) become spaces, and so does a highlighted
     * caret cell that only starts such a suggestion. Clicking there must not send arrow keys,
     * because Right accepts the suggestion.
     */
    List<String> typedLines(List<String> lines, PromptBox box, Cell caret) {
        List<String> typed = new ArrayList<>(lines);
        Object buffer = call(widget, "getTerminalTextBuffer");
        Object dim = option("DIM");
        Object inverse = option("INVERSE");
        call(buffer, "lock");
        try {
            for (int row = box.top(); row <= box.bottom(); row++) {
                Object line = call(buffer, "getLine", row);
                char[] chars = lines.get(row).toCharArray();
                boolean[] dimmed = new boolean[chars.length];
                for (int col = 0; col < chars.length; col++) {
                    dimmed[col] = has(call(line, "getStyleAt", col), dim);
                    if (dimmed[col]) chars[col] = ' ';
                }
                int c = caret.col();
                if (row == caret.row() && c < chars.length && has(call(line, "getStyleAt", c), inverse)
                        && new String(chars, c + 1, chars.length - c - 1).isBlank()
                        && (c + 1 < chars.length && dimmed[c + 1])) {
                    chars[c] = ' ';
                }
                typed.set(row, new String(chars));
            }
        } finally {
            call(buffer, "unlock");
        }
        return typed;
    }

    /** A constant of JediTerm's TextStyle.Option, or null when this JediTerm has no such option. */
    private Object option(String name) {
        try {
            Object style = call(call(widget, "getTerminalTextBuffer"), "getStyleAt", 0, 0);
            Class<?> type = Class.forName("com.jediterm.terminal.TextStyle$Option", false, style.getClass().getClassLoader());
            for (Object constant : type.getEnumConstants()) if (((Enum<?>) constant).name().equals(name)) return constant;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // no such option: treat no cell as having it
        }
        return null;
    }

    private static boolean has(Object style, Object option) {
        return style != null && option != null && Boolean.TRUE.equals(call(style, "hasOption", option));
    }

    /** The selected cells as {start, end} in reading order (end exclusive), or null without a selection. */
    Cell[] selection() {
        Object selection = call(panel, "getSelection");
        if (selection == null) return null;
        Cell a = pointToCell(call(selection, "getStart"));
        Cell b = pointToCell(call(selection, "getEnd"));
        if (a == null || b == null || a.equals(b)) return null;
        return a.isBefore(b) ? new Cell[]{a, b} : new Cell[]{b, a};
    }

    /** Removes the selection highlight (UI thread). */
    void clearSelection() {
        call(panel, "updateSelection", new Object[]{null});
        ((Component) panel).repaint();
    }

    /** Types {@code keys} into the program, as if the user had pressed them. */
    void type(String keys) {
        if (keys.isEmpty()) return;
        call(call(widget, "getTerminalStarter"), "sendString", keys, true);
    }

    private static Cell pointToCell(Object point) {
        if (point == null) return null;
        try {
            return new Cell(point.getClass().getField("x").getInt(point), point.getClass().getField("y").getInt(point));
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static boolean isA(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) if (c.getName().equals(name)) return true;
        return false;
    }

    /** Calls the method {@code name} on {@code target}, searching its class hierarchy, private included. */
    private static Object call(Object target, String name, Object... args) {
        if (target == null) return null;
        Method m = METHODS.computeIfAbsent(target.getClass().getName() + "#" + name + "/" + args.length,
                key -> find(target.getClass(), name, args.length));
        try {
            return m.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("terminal call " + name + " failed", e);
        }
    }

    /** Looked-up methods by "class#name/arity"; the lookup walks the hierarchy, so it is cached. */
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();

    private static Method find(Class<?> type, String name, int arity) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == arity) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new IllegalStateException("terminal has no method " + name);
    }
}
