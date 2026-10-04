package io.github.olviia.clickableprompt;

/** A character cell of the terminal screen, 0-based: {@code col} from the left, {@code row} from the top. */
public record Cell(int col, int row) {

    /** True when this cell comes before {@code other} in reading order. */
    public boolean isBefore(Cell other) {
        return row < other.row || row == other.row && col < other.col;
    }
}
