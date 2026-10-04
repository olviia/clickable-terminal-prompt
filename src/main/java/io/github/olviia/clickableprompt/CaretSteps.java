package io.github.olviia.clickableprompt;

/**
 * Decides the next keystrokes that bring the caret closer to a target cell.
 * <p>
 * One step at a time, so the caller can look where the caret really went before the next
 * step: the program behind the terminal wraps and moves its caret by its own rules.
 * Rows change by single Up/Down presses, and never Up from the box's first row, where
 * Up means "previous prompt from history" and would replace what was typed.
 */
public final class CaretSteps {

    public static final String UP = "\u001b[A";
    public static final String DOWN = "\u001b[B";
    public static final String RIGHT = "\u001b[C";
    public static final String LEFT = "\u001b[D";
    public static final String BACKSPACE = "\u007f";

    private CaretSteps() {
    }

    /** Keys moving {@code caret} toward {@code target}; empty when it is there or cannot go further safely. */
    public static String toward(Cell caret, Cell target, PromptBox box) {
        if (caret.row() > target.row()) return caret.row() > box.top() ? UP : "";
        if (caret.row() < target.row()) return caret.row() < box.bottom() ? DOWN : "";
        int dx = target.col() - caret.col();
        return (dx > 0 ? RIGHT : LEFT).repeat(Math.abs(dx));
    }

    /**
     * Backspaces deleting from {@code caret} back toward {@code start}; empty when there.
     * On the same row the count is exact; across rows only up to the row's start, so
     * re-wrapping text never makes it delete past {@code start}.
     */
    public static String deleteToward(Cell caret, Cell start, PromptBox box) {
        if (!start.isBefore(caret)) return "";
        if (caret.row() == start.row()) return BACKSPACE.repeat(caret.col() - start.col());
        return BACKSPACE.repeat(Math.max(1, caret.col() - box.textStart()));
    }
}
