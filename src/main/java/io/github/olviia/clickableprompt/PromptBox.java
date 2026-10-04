package io.github.olviia.clickableprompt;

import java.util.List;

/**
 * Where the editable prompt is on the screen: the rows it spans and the column its text starts at.
 * <p>
 * Knows the layout of a CLI agent's input box (Claude Code draws it between two horizontal
 * rules, first row starting with "> ") and falls back to a plain shell prompt, which is just
 * the cursor's row. Pure: works on screen text only, so it is unit-testable.
 */
public record PromptBox(int top, int bottom, int textStart) {

    /** A rule longer than this many cells delimits the input box. */
    private static final int MIN_RULE_LENGTH = 10;

    /** The filler JediTerm stores in the second cell of a double-width character. */
    static final char DWC = '';

    /** Prompt markers CLI agents print before the input text. */
    private static final String MARKERS = ">❯›";

    /**
     * Finds the prompt around {@code cursorRow} in {@code screen} (one string per screen row),
     * or returns null when the cursor is not in an editable prompt.
     */
    public static PromptBox find(List<String> screen, int cursorRow) {
        if (cursorRow < 0 || cursorRow >= screen.size()) return null;
        int above = cursorRow - 1;
        while (above >= 0 && !isRule(screen.get(above))) above--;
        int below = cursorRow + 1;
        while (below < screen.size() && !isRule(screen.get(below))) below++;
        boolean boxed = above >= 0 && below < screen.size();
        int top = boxed ? above + 1 : cursorRow;
        int bottom = boxed ? below - 1 : cursorRow;
        return new PromptBox(top, bottom, boxed ? textStart(screen.get(top)) : 0);
    }

    public boolean contains(Cell cell) {
        return cell.row() >= top && cell.row() <= bottom;
    }

    /** The closest cell to {@code cell} the caret can occupy: inside the box, within the row's text. */
    public Cell clamp(Cell cell, List<String> screen) {
        int row = Math.max(top, Math.min(bottom, cell.row()));
        int end = Math.max(textStart, screen.get(row).stripTrailing().length());
        return new Cell(Math.max(textStart, Math.min(end, cell.col())), row);
    }

    /**
     * The text shown in the cells from {@code from} up to (excluding) {@code to}, rows joined
     * as one line (the box wraps a single input), without the terminal's wide-character fillers.
     */
    public static String textBetween(List<String> screen, Cell from, Cell to, PromptBox box) {
        StringBuilder text = new StringBuilder();
        for (int row = from.row(); row <= to.row(); row++) {
            String line = screen.get(row);
            int start = row == from.row() ? from.col() : box.textStart;
            int end = row == to.row() ? to.col() : line.length();
            if (start < Math.min(end, line.length())) text.append(line, start, Math.min(end, line.length()));
        }
        return text.toString().replace(String.valueOf(DWC), "");
    }

    /** Column after the "> " marker of the box's first row; 0 when there is none. */
    private static int textStart(String firstRow) {
        int i = 0;
        while (i < firstRow.length() && firstRow.charAt(i) == ' ') i++;
        if (i < firstRow.length() && MARKERS.indexOf(firstRow.charAt(i)) >= 0) {
            i++;
            if (i < firstRow.length() && firstRow.charAt(i) == DWC) i++;
            if (i < firstRow.length() && firstRow.charAt(i) == ' ') i++;
            return i;
        }
        return 0;
    }

    private static boolean isRule(String line) {
        String s = line.strip();
        if (s.length() < MIN_RULE_LENGTH) return false;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) != '─' && s.charAt(i) != '━') return false;
        return true;
    }
}
