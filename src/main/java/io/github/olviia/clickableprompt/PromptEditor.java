package io.github.olviia.clickableprompt;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Carries out mouse edits on the prompt by typing keys: moves the caret to a clicked cell,
 * deletes a selected range.
 * <p>
 * Works as a feedback loop: type a step from {@link CaretSteps}, wait until the program has
 * moved its caret, look again, repeat. Runs off the UI thread; a newer edit cancels an older one.
 */
final class PromptEditor {

    private static final Logger LOG = Logger.getInstance(PromptEditor.class);

    /** Upper bound of steps per edit, so a caret that cannot reach its target never loops forever. */
    private static final int MAX_STEPS = 200;

    /** How long to wait for the program to move its caret after a step. */
    private static final long SETTLE_MS = 600;

    /** The caret counts as at rest after it kept still this long. */
    private static final long REST_MS = 60;

    private static final AtomicInteger generation = new AtomicInteger();

    private PromptEditor() {
    }

    /** Moves the caret of the prompt to {@code target}, clamped to the prompt's text. */
    static void moveCaret(TerminalScreen screen, Cell target) {
        int id = generation.incrementAndGet();
        ApplicationManager.getApplication().executeOnPooledThread(() -> run(id, () -> {
            Prompt prompt = Prompt.read(screen);
            LOG.debug("click " + target + " caret=" + (prompt == null ? null : prompt.caret)
                    + " box=" + (prompt == null ? null : prompt.box));
            if (prompt == null || !prompt.box.contains(target)) return;
            Cell goal = prompt.box.clamp(target, prompt.lines);
            LOG.debug("move caret " + prompt.caret + " -> " + goal + " box=" + prompt.box);
            walk(id, screen, prompt, goal);
        }));
    }

    /** Deletes the text from {@code start} up to (excluding) {@code end} in the prompt. */
    static void deleteRange(TerminalScreen screen, Cell start, Cell end) {
        int id = generation.incrementAndGet();
        ApplicationManager.getApplication().executeOnPooledThread(() -> run(id, () -> {
            Prompt prompt = Prompt.read(screen);
            if (prompt == null) return;
            Cell from = prompt.box.clamp(start, prompt.lines);
            Cell to = prompt.box.clamp(end, prompt.lines);
            LOG.debug("delete " + from + " .. " + to + " caret=" + prompt.caret + " box=" + prompt.box);
            lastDeletion = new Deletion(from, PromptBox.textBetween(prompt.lines, from, to, prompt.box));
            prompt = walk(id, screen, prompt, to);
            for (int step = 0; prompt != null && step < MAX_STEPS && id == generation.get(); step++) {
                String keys = CaretSteps.deleteToward(prompt.caret, from, prompt.box);
                if (keys.isEmpty()) return;
                prompt = typeAndSettle(screen, prompt, keys);
            }
        }));
    }

    /**
     * Types the text of the last {@link #deleteRange} back where it was. Returns false, doing
     * nothing, when there is no deletion to undo; each deletion can be undone once.
     */
    static boolean undoDelete(TerminalScreen screen) {
        Deletion deletion = lastDeletion;
        if (deletion == null || deletion.text.isEmpty()) return false;
        lastDeletion = null;
        int id = generation.incrementAndGet();
        ApplicationManager.getApplication().executeOnPooledThread(() -> run(id, () -> {
            Prompt prompt = Prompt.read(screen);
            if (prompt == null) return;
            LOG.debug("undo: retype " + deletion.text.length() + " chars at " + deletion.at);
            prompt = walk(id, screen, prompt, deletion.at);
            if (prompt != null && prompt.caret.equals(deletion.at)) screen.type(deletion.text);
        }));
        return true;
    }

    /** What the last deletion removed and where; null when there is nothing to undo. */
    private static volatile Deletion lastDeletion;

    private record Deletion(Cell at, String text) {
    }

    /** Steps the caret toward {@code goal}; returns the prompt as last seen. */
    private static Prompt walk(int id, TerminalScreen screen, Prompt prompt, Cell goal) {
        for (int step = 0; prompt != null && step < MAX_STEPS && id == generation.get(); step++) {
            String keys = CaretSteps.toward(prompt.caret, goal, prompt.box);
            if (keys.isEmpty()) return prompt;
            prompt = typeAndSettle(screen, prompt, keys);
        }
        return prompt;
    }

    /** Types {@code keys}, then waits until the caret moved and came to rest; null when it never moved. */
    private static Prompt typeAndSettle(TerminalScreen screen, Prompt before, String keys) {
        screen.type(keys);
        long deadline = System.currentTimeMillis() + SETTLE_MS;
        Prompt last = null;
        long lastChange = 0;
        while (System.currentTimeMillis() < deadline) {
            sleep(15);
            Prompt now = Prompt.read(screen);
            if (now == null) continue;
            boolean moved = !Objects.equals(now.caret, before.caret);
            if (moved && (last == null || !Objects.equals(now.caret, last.caret))) {
                last = now;
                lastChange = System.currentTimeMillis();
            } else if (last != null && System.currentTimeMillis() - lastChange >= REST_MS) {
                return now;
            }
        }
        if (last == null) LOG.debug("caret did not move after " + keys.length() + " key chars");
        return last;
    }

    private static void run(int id, Runnable edit) {
        try {
            if (id == generation.get()) edit.run();
        } catch (RuntimeException e) {
            LOG.debug("edit failed: " + e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** One look at the terminal: its rows, the caret, and the prompt box around the caret. */
    private record Prompt(List<String> lines, Cell caret, PromptBox box) {
        static Prompt read(TerminalScreen screen) {
            List<String> lines = screen.lines();
            Cell caret = screen.cursor();
            PromptBox box = caret == null ? null : PromptBox.find(lines, caret.row());
            return box == null ? null : new Prompt(lines, caret, box);
        }
    }
}
