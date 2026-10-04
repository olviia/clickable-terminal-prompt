package io.github.olviia.clickableprompt;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import javax.swing.SwingUtilities;
import com.intellij.openapi.diagnostic.Logger;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Turns mouse gestures in a terminal into prompt edits: a plain click moves the prompt's caret
 * there, Backspace or Delete with a selection in the prompt deletes the selection.
 * <p>
 * Watches the IDE's event queue, because the terminal itself has no hooks for this. Leaves
 * every other event alone, and stands aside while the program in the terminal reads the mouse.
 */
public final class TerminalMouseWatcher implements AppLifecycleListener {

    private static final int MODIFIERS = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK
            | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK;

    private static final Logger LOG = Logger.getInstance(TerminalMouseWatcher.class);

    /** How far the mouse may move between press and release and still count as a click. */
    private static final int CLICK_SLOP_PX = 3;

    /** Set after a consumed key press whose KEY_TYPED twin must be dropped too (UI thread only). */
    private static boolean swallowTyped;

    /** The character a Ctrl+Z press types. */
    private static final char CTRL_Z_CHAR = 0x1A;

    /** Where the left button went down, on screen; null when no click is in progress (UI thread only). */
    private static Point pressedAt;

    @Override
    public void appFrameCreated(@NotNull List<String> commandLineArgs) {
        IdeEventQueue.getInstance().addDispatcher(TerminalMouseWatcher::onEvent, ApplicationManager.getApplication());
    }

    /** Returns true when the event was used up as a prompt edit. */
    private static boolean onEvent(@NotNull AWTEvent event) {
        try {
            if (event instanceof MouseEvent mouse) onMouse(mouse);
            else if (event instanceof KeyEvent key) return onKey(key);
        } catch (RuntimeException e) {
            // an unknown terminal version: behave as if the plugin were not installed
            LOG.debug("event handling failed: " + e);
        }
        return false;
    }

    /** A click is a press and release of the left button at nearly the same spot, no modifiers. */
    private static void onMouse(MouseEvent mouse) {
        if (mouse.getButton() != MouseEvent.BUTTON1 || (mouse.getModifiersEx() & MODIFIERS) != 0) return;
        Point onScreen = mouse.getLocationOnScreen();
        if (mouse.getID() == MouseEvent.MOUSE_PRESSED) {
            pressedAt = mouse.getClickCount() == 1 ? onScreen : null;
            return;
        }
        if (mouse.getID() != MouseEvent.MOUSE_RELEASED || pressedAt == null) return;
        boolean click = pressedAt.distance(onScreen) <= CLICK_SLOP_PX;
        pressedAt = null;
        if (!click) return;
        // queue events are addressed to the window; find the component actually under the mouse
        Component target = SwingUtilities.getDeepestComponentAt(mouse.getComponent(), mouse.getX(), mouse.getY());
        TerminalScreen screen = TerminalScreen.of(target);
        if (screen == null) return;
        if (screen.programWantsMouse()) {
            LOG.debug("click ignored: the program reads the mouse itself");
            return;
        }
        Cell cell = screen.cellAt(screen.toPanel(mouse.getComponent(), mouse.getPoint()));
        if (cell != null) PromptEditor.moveCaret(screen, cell);
    }

    private static boolean onKey(KeyEvent key) {
        if (key.getID() == KeyEvent.KEY_TYPED && swallowTyped) {
            swallowTyped = false;
            char c = key.getKeyChar();
            return c == '\b' || c == KeyEvent.VK_DELETE || c == CTRL_Z_CHAR;
        }
        if (key.getID() != KeyEvent.KEY_PRESSED) return false;
        if (key.getKeyCode() == KeyEvent.VK_Z && (key.getModifiersEx() & MODIFIERS) == InputEvent.CTRL_DOWN_MASK) {
            TerminalScreen screen = TerminalScreen.of(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
            if (screen == null || !PromptEditor.undoDelete(screen)) return false;
            swallowTyped = true;
            return true;
        }
        if ((key.getModifiersEx() & MODIFIERS) != 0) return false;
        if (key.getKeyCode() != KeyEvent.VK_BACK_SPACE && key.getKeyCode() != KeyEvent.VK_DELETE) return false;
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        TerminalScreen screen = TerminalScreen.of(focus);
        if (screen == null) return false;
        Cell[] selection = screen.selection();
        if (selection == null) return false;
        Cell caret = screen.cursor();
        PromptBox box = caret == null ? null : PromptBox.find(screen.lines(), caret.row());
        if (box == null || !box.contains(selection[0]) || !box.contains(selection[1])) return false;
        PromptEditor.deleteRange(screen, selection[0], selection[1]);
        screen.clearSelection();
        swallowTyped = true; // the key's KEY_TYPED twin must not reach the terminal either
        return true;
    }
}
