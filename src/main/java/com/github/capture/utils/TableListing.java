package com.github.capture.utils;

import java.util.ArrayList;
import java.util.LinkedList;
import org.apache.commons.lang3.StringUtils;

/**
 * This class implements a "table listing" with column headers.
 *
 * Example:
 *
 * NAME   OWNER   GROUP   MODE       WEIGHT
 * pool1  andrew  andrew  rwxr-xr-x     100
 * pool2  andrew  andrew  rwxr-xr-x     100
 * pool3  andrew  andrew  rwxr-xr-x     100
 *
 */
public class TableListing {
    public enum Justification {
        LEFT,
        RIGHT;
    }

    private static class Column {
        private final ArrayList<String> rows;
        private final Justification justification;
        private final boolean wrap;

        private int wrapWidth = Integer.MAX_VALUE;
        private int maxWidth;

        Column(String title, Justification justification, boolean wrap) {
            this.rows = new ArrayList<String>();
            this.justification = justification;
            this.wrap = wrap;
            this.maxWidth = 0;
            addRow(title);
        }

        private void addRow(String val) {
            if (val == null) {
                val = "";
            }
            if ((val.length() + 1) > maxWidth) {
                maxWidth = val.length() + 1;
            }
            // Ceiling at wrapWidth, because it'll get wrapped
            if (maxWidth > wrapWidth) {
                maxWidth = wrapWidth;
            }
            rows.add(val);
        }

        private int getMaxWidth() {
            return maxWidth;
        }

        private void setWrapWidth(int width) {
            wrapWidth = width;
            // Ceiling the maxLength at wrapWidth
            if (maxWidth > wrapWidth) {
                maxWidth = wrapWidth;
            }
            // Else we need to traverse through and find the real maxWidth
            else {
                maxWidth = 0;
                for (int i=0; i<rows.size(); i++) {
                    int length = rows.get(i).length();
                    if (length > maxWidth) {
                        maxWidth = length;
                    }
                }
            }
        }

        /**
         * Return the ith row of the column as a set of wrapped strings, each at
         * most wrapWidth in length.
         */
        String[] getRow(int idx) {
            String raw = rows.get(idx);
            // Line-wrap if it's too long
            String[] lines = new String[] {raw};
            if (wrap) {
                lines = wrap(lines[0], wrapWidth, "\n", true).split("\n");
            }
            for (int i=0; i<lines.length; i++) {
                if (justification == Justification.LEFT) {
                    lines[i] = StringUtils.rightPad(lines[i], maxWidth);
                } else if (justification == Justification.RIGHT) {
                    lines[i] = StringUtils.leftPad(lines[i], maxWidth);
                }
            }
            return lines;
        }
    }

    public static class Builder {
        private final LinkedList<Column> columns = new LinkedList<Column>();
        private boolean showHeader = true;
        private int wrapWidth = Integer.MAX_VALUE;

        /**
         * Create a new Builder.
         */
        public Builder() {
        }

        public Builder addField(String title) {
            return addField(title, Justification.LEFT, false);
        }

        public Builder addField(String title, Justification justification) {
            return addField(title, justification, false);
        }

        public Builder addField(String title, boolean wrap) {
            return addField(title, Justification.LEFT, wrap);
        }

        /**
         * Add a new field to the Table under construction.
         *
         * @param title Field title.
         * @param justification Right or left justification. Defaults to left.
         * @param wrap Width at which to auto-wrap the content of the cell.
         *        Defaults to Integer.MAX_VALUE.
         * @return This Builder object
         */
        public Builder addField(String title, Justification justification,
                                boolean wrap) {
            columns.add(new Column(title, justification, wrap));
            return this;
        }

        /**
         * Whether to hide column headers in table output
         */
        public Builder hideHeaders() {
            this.showHeader = false;
            return this;
        }

        /**
         * Whether to show column headers in table output. This is the default.
         */
        public Builder showHeaders() {
            this.showHeader = true;
            return this;
        }

        /**
         * Set the maximum width of a row in the TableListing. Must have one or
         * more wrappable fields for this to take effect.
         */
        public Builder wrapWidth(int width) {
            this.wrapWidth = width;
            return this;
        }

        /**
         * Create a new TableListing.
         */
        public TableListing build() {
            return new TableListing(columns.toArray(new Column[0]), showHeader,
                    wrapWidth);
        }
    }

    private final Column columns[];

    private int numRows;
    private final boolean showHeader;
    private final int wrapWidth;

    TableListing(Column columns[], boolean showHeader, int wrapWidth) {
        this.columns = columns;
        this.numRows = 0;
        this.showHeader = showHeader;
        this.wrapWidth = wrapWidth;
    }

    /**
     * Add a new row.
     *
     * @param row    The row of objects to add-- one per column.
     */
    public void addRow(String... row) {
        if (row.length != columns.length) {
            throw new RuntimeException("trying to add a row with " + row.length +
                    " columns, but we have " + columns.length + " columns.");
        }
        for (int i = 0; i < columns.length; i++) {
            columns[i].addRow(row[i]);
        }
        numRows++;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        // Calculate the widths of each column based on their maxWidths and
        // the wrapWidth for the entire table
        int width = (columns.length-1)*2; // inter-column padding
        for (int i=0; i<columns.length; i++) {
            width += columns[i].maxWidth;
        }
        // Decrease the column size of wrappable columns until the goal width
        // is reached, or we can't decrease anymore
        while (width > wrapWidth) {
            boolean modified = false;
            for (int i=0; i<columns.length; i++) {
                Column column = columns[i];
                if (column.wrap) {
                    int maxWidth = column.getMaxWidth();
                    if (maxWidth > 4) {
                        column.setWrapWidth(maxWidth-1);
                        modified = true;
                        width -= 1;
                        if (width <= wrapWidth) {
                            break;
                        }
                    }
                }
            }
            if (!modified) {
                break;
            }
        }

        int startrow = 0;
        if (!showHeader) {
            startrow = 1;
        }
        String[][] columnLines = new String[columns.length][];
        for (int i = startrow; i < numRows + 1; i++) {
            int maxColumnLines = 0;
            for (int j = 0; j < columns.length; j++) {
                columnLines[j] = columns[j].getRow(i);
                if (columnLines[j].length > maxColumnLines) {
                    maxColumnLines = columnLines[j].length;
                }
            }

            for (int c = 0; c < maxColumnLines; c++) {
                // First column gets no left-padding
                String prefix = "";
                for (int j = 0; j < columns.length; j++) {
                    // Prepend padding
                    builder.append(prefix);
                    prefix = " ";
                    if (columnLines[j].length > c) {
                        builder.append(columnLines[j][c]);
                    } else {
                        builder.append(StringUtils.repeat(" ", columns[j].maxWidth));
                    }
                }
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    /**
     * Same as WordUtils#wrap in commons-lang 2.6. Unlike commons-lang3, leading
     * spaces on the first line are NOT stripped.
     *
     * @param str  the String to be word wrapped, may be null
     * @param wrapLength  the column to wrap the words at, less than 1 is treated
     *                   as 1
     * @param newLineStr  the string to insert for a new line,
     *  <code>null</code> uses the system property line separator
     * @param wrapLongWords  true if long words (such as URLs) should be wrapped
     * @return a line with newlines inserted, <code>null</code> if null input
     */
    private static String wrap(String str, int wrapLength, String newLineStr,
                               boolean wrapLongWords) {
        if(str == null) {
            return null;
        } else {
            if(newLineStr == null) {
                newLineStr = System.lineSeparator();
            }

            if(wrapLength < 1) {
                wrapLength = 1;
            }

            int inputLineLength = str.length();
            int offset = 0;
            StringBuffer wrappedLine = new StringBuffer(inputLineLength + 32);

            while(inputLineLength - offset > wrapLength) {
                if(str.charAt(offset) == 32) {
                    ++offset;
                } else {
                    int spaceToWrapAt = str.lastIndexOf(32, wrapLength + offset);
                    if(spaceToWrapAt >= offset) {
                        wrappedLine.append(str.substring(offset, spaceToWrapAt));
                        wrappedLine.append(newLineStr);
                        offset = spaceToWrapAt + 1;
                    } else if(wrapLongWords) {
                        wrappedLine.append(str.substring(offset, wrapLength + offset));
                        wrappedLine.append(newLineStr);
                        offset += wrapLength;
                    } else {
                        spaceToWrapAt = str.indexOf(32, wrapLength + offset);
                        if(spaceToWrapAt >= 0) {
                            wrappedLine.append(str.substring(offset, spaceToWrapAt));
                            wrappedLine.append(newLineStr);
                            offset = spaceToWrapAt + 1;
                        } else {
                            wrappedLine.append(str.substring(offset));
                            offset = inputLineLength;
                        }
                    }
                }
            }

            wrappedLine.append(str.substring(offset));
            return wrappedLine.toString();
        }
    }

}