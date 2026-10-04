package io.github.olviia.clickableprompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTest {

    private static final String RULE = "─".repeat(60);

    /** Claude Code's layout: transcript, rule, two prompt rows, rule, status line. */
    private static final List<String> CLAUDE = List.of(
            "● Some earlier reply",
            "",
            RULE,
            "> fix the bug in the caret",
            "  code please",
            RULE,
            "  ? for shortcuts");

    @Test
    void claudeBox_spansRowsBetweenRules() {
        assertEquals(new PromptBox(3, 4, 2), PromptBox.find(CLAUDE, 4));
    }

    @Test
    void plainShell_isTheCursorRow() {
        List<String> shell = List.of("PS D:\\> ls", "a.txt", "PS D:\\> git sta");
        assertEquals(new PromptBox(2, 2, 0), PromptBox.find(shell, 2));
    }

    @Test
    void cursorOutsideScreen_noBox() {
        assertNull(PromptBox.find(CLAUDE, 9));
    }

    @Test
    void clamp_keepsCaretInsideText() {
        PromptBox box = PromptBox.find(CLAUDE, 3);
        assertEquals(new Cell(2, 3), box.clamp(new Cell(0, 3), CLAUDE));    // on the "> " marker
        assertEquals(new Cell(13, 4), box.clamp(new Cell(50, 4), CLAUDE));  // past the end of "  code please"
        assertEquals(new Cell(2, 4), box.clamp(new Cell(2, 6), CLAUDE));    // below the box
    }

    @Test
    void sameRow_movesByExactCount() {
        PromptBox box = PromptBox.find(CLAUDE, 3);
        assertEquals(CaretSteps.LEFT.repeat(5), CaretSteps.toward(new Cell(10, 3), new Cell(5, 3), box));
        assertEquals(CaretSteps.RIGHT.repeat(2), CaretSteps.toward(new Cell(5, 3), new Cell(7, 3), box));
        assertEquals("", CaretSteps.toward(new Cell(5, 3), new Cell(5, 3), box));
    }

    @Test
    void rows_changeOneAtATime_neverUpFromFirstRow() {
        PromptBox box = PromptBox.find(CLAUDE, 3);
        assertEquals(CaretSteps.UP, CaretSteps.toward(new Cell(5, 4), new Cell(5, 3), box));
        assertEquals(CaretSteps.DOWN, CaretSteps.toward(new Cell(5, 3), new Cell(5, 4), box));
        assertEquals("", CaretSteps.toward(new Cell(5, 3), new Cell(5, 2), box));
    }

    @Test
    void wideMarker_textStartsAfterItsFiller() {
        List<String> screen = List.of(RULE, "❯ hello", RULE);
        assertEquals(3, PromptBox.find(screen, 1).textStart());
    }

    @Test
    void textBetween_joinsRowsOfTheBox() {
        PromptBox box = PromptBox.find(CLAUDE, 3);
        assertEquals("bug", PromptBox.textBetween(CLAUDE, new Cell(10, 3), new Cell(13, 3), box));
        assertEquals("caretcode", PromptBox.textBetween(CLAUDE, new Cell(21, 3), new Cell(6, 4), box));
    }

    @Test
    void delete_sameRowExact_otherRowOnlyToRowStart() {
        PromptBox box = PromptBox.find(CLAUDE, 3);
        assertEquals(CaretSteps.BACKSPACE.repeat(4), CaretSteps.deleteToward(new Cell(10, 3), new Cell(6, 3), box));
        assertEquals(CaretSteps.BACKSPACE.repeat(5), CaretSteps.deleteToward(new Cell(7, 4), new Cell(20, 3), box));
        assertEquals(CaretSteps.BACKSPACE, CaretSteps.deleteToward(new Cell(2, 4), new Cell(20, 3), box));
        assertTrue(CaretSteps.deleteToward(new Cell(6, 3), new Cell(6, 3), box).isEmpty());
    }
}
