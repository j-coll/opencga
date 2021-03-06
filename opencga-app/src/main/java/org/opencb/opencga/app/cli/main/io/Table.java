package org.opencb.opencga.app.cli.main.io;

import org.apache.commons.lang3.StringUtils;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.fusesource.jansi.Ansi.Erase;
import static org.fusesource.jansi.Ansi.ansi;

public class Table<T> {
    public static final String DEFAULT_EMPTY_VALUE = "-";
    private final List<TableColumnSchema<T>> schema = new ArrayList<>();
    private List<TableColumn<T>> columns = new ArrayList<>();
    private final TablePrinter tablePrinter;

    public enum PrinterType {
        ASCII, JANSI, TSV,
    }

    public Table() {
        this(new JAnsiTablePrinter());
    }

    public Table(PrinterType type) {
        switch (type) {
            case ASCII:
                this.tablePrinter = new AsciiTablePrinter();
                break;
            case JANSI:
                this.tablePrinter = new JAnsiTablePrinter();
                break;
            case TSV:
                this.tablePrinter = new TsvTablePrinter();
                break;
            default:
                throw new IllegalArgumentException("Unknown type : " + type);
        }
    }

    public Table(TablePrinter tablePrinter) {
        this.tablePrinter = tablePrinter;
    }

    public Table<T> addColumn(String name, Function<T, String> get) {
        return addColumn(new TableColumnSchema<>(name, get));
    }

    public Table<T> addColumnNumber(String name, Function<T, ? extends Number> get) {
        return addColumnNumber(name, get, DEFAULT_EMPTY_VALUE);
    }

    public Table<T> addColumnNumber(String name, Function<T, ? extends Number> get, String defaultValue) {
        return addColumn(new TableColumnSchema<>(name, TableColumnSchema.wrap(get, defaultValue)));
    }

    public Table<T> addColumnEnum(String name, Function<T, ? extends Enum<?>> get) {
        return addColumnEnum(name, get, DEFAULT_EMPTY_VALUE);
    }

    public Table<T> addColumnEnum(String name, Function<T, ? extends Enum<?>> get, String defaultValue) {
        return addColumn(new TableColumnSchema<>(name, TableColumnSchema.wrap(get, defaultValue)));
    }

    public Table<T> addColumn(String name, Function<T, String> get, String defaultValue) {
        return addColumn(new TableColumnSchema<>(name, TableColumnSchema.wrap(get, defaultValue)));
    }

    public Table<T> addColumn(String name, Function<T, String> get, int maxWidth, String defaultValue) {
        return addColumn(new TableColumnSchema<>(name, TableColumnSchema.wrap(get, defaultValue), maxWidth));
    }

    public Table<T> addColumn(String name, Function<T, String> get, int maxWidth) {
        return addColumn(new TableColumnSchema<>(name, get, maxWidth));
    }

    public Table<T> addColumn(TableColumnSchema<T> e) {
        schema.add(e);
        return this;
    }

    public Table<T> addColumns(Collection<TableColumnSchema<T>> e) {
        schema.addAll(e);
        return this;
    }

    public void print(String content) {
        tablePrinter.print(content);
    }

    public void println() {
        tablePrinter.println(null);
    }

    public void println(String content) {
        tablePrinter.println(content);
    }

    public void printTable() {
//        printFullLine();
        tablePrinter.printHeader(columns);
        tablePrinter.printLine(columns);

        for (int i = 0; i < columns.get(0).values.size(); i++) {
            tablePrinter.printRow(columns, i);
        }
        printFullLine();
    }

    public void restoreCursorPosition() {
        tablePrinter.restorePosition();
    }

    public void printFullLine() {
        tablePrinter.printFullLine(columns);
    }

    public void updateTable(List<T> rows) {
        columns = schema.stream().map(column -> new TableColumn<>(column, rows)).collect(Collectors.toList());
    }


    public static class TableColumn<T> {
        private final TableColumnSchema<T> tableColumnSchema;
        private ArrayList<String> values = new ArrayList<>();
        private int width;

        public TableColumn(TableColumnSchema<T> tableColumnSchema, List<T> rows) {
            this.tableColumnSchema = tableColumnSchema;

            values.ensureCapacity(rows.size());
            for (T row : rows) {
                String value = tableColumnSchema.getGet().apply(row);
                values.add(value);
            }

            width = values.stream().mapToInt(String::length).max().orElse(5);
            width = Math.max(width, tableColumnSchema.getName().length());
            if (width > tableColumnSchema.getMaxWidth()) {
                width = tableColumnSchema.getMaxWidth();
            }
            if (width < tableColumnSchema.getMinWidth()) {
                width = tableColumnSchema.getMinWidth();
            }
        }

        public String getName() {
            return tableColumnSchema.getName();
        }

        public String getPrintName() {
            return StringUtils.rightPad(getPrintValue(tableColumnSchema.getName()), width);
        }

        public String getValue(int idx) {
            return values.get(idx);
        }

        public String getPrintValue(int idx) {
            return getPrintValue(values.get(idx));
        }

        public String getPrintValue(String value) {
            if (value.length() > width) {
                return value.substring(0, width - 3) + "...";
            } else {
                return StringUtils.rightPad(value, width);
            }
        }

        public Function<T, String> getGet() {
            return tableColumnSchema.getGet();
        }

        public int getMaxWidth() {
            return tableColumnSchema.getMaxWidth();
        }

        public int getWidth() {
            return width;
        }

        public String getLine() {
            return getLine(0);
        }

        public String getLine(int pad) {
            return StringUtils.repeat("-", width + pad);
        }
    }

    public static class TableColumnSchema<T> {
        private interface SecureGet<T> extends Function<T, String> {
        }

        public static final int DEFAULT_MAX_WIDTH = 30;
        public static final int DEFAULT_MIN_WIDTH = 5;
        private final String name;
        private final SecureGet<T> get;
        private final int minWidth;
        private final int maxWidth;

        public TableColumnSchema(String name, Function<T, String> get) {
            this(name, get, DEFAULT_MAX_WIDTH, DEFAULT_MIN_WIDTH);
        }

        public TableColumnSchema(String name, Function<T, String> get, int maxWidth) {
            this(name, get, maxWidth, DEFAULT_MIN_WIDTH);
        }

        public TableColumnSchema(String name, Function<T, String> get, int maxWidth, int minWidth) {
            this(name, get, maxWidth, minWidth, DEFAULT_EMPTY_VALUE);
        }

        public TableColumnSchema(String name, Function<T, String> get, int maxWidth, int minWidth, String defaultEmptyValue) {
            this.name = name;
            this.get = wrap(get, defaultEmptyValue);
            this.maxWidth = maxWidth;
            this.minWidth = minWidth;
        }

        private static <T> SecureGet<T> wrap(Function<T, ?> get, String defaultValue) {
            if (get instanceof TableColumnSchema.SecureGet) {
                return ((SecureGet<T>) get);
            } else {
                return t -> {
                    try {
                        Object o = get.apply(t);
                        if (o == null) {
                            return defaultValue;
                        } else {
                            String str = o.toString();
                            if (str == null || str.isEmpty()) {
                                return defaultValue;
                            } else {
                                return str;
                            }
                        }
                    } catch (RuntimeException e) {
                        return defaultValue;
                    }
                };
            }
        }

        public String getName() {
            return name;
        }

        public Function<T, String> getGet() {
            return get;
        }

        public int getMaxWidth() {
            return maxWidth;
        }

        public int getMinWidth() {
            return minWidth;
        }
    }

    public interface TablePrinter {

        // Restores initial position so content is overwritten
        void restorePosition();

        void println(String content);

        void print(String content);

        <T> void printHeader(List<Table.TableColumn<T>> columns);

        <T> void printLine(List<Table.TableColumn<T>> columns);

        <T> void printFullLine(List<Table.TableColumn<T>> columns);

        <T> void printRow(List<TableColumn<T>> columns, int i);
    }

    public static class JAnsiTablePrinter implements TablePrinter {

        private final PrintStream out;
        private String sep = " ";
        private String pad = " ";
        private int numLines = 0;

        public JAnsiTablePrinter() {
            AnsiConsole.systemInstall();
            out = AnsiConsole.out;
        }

        @Override
        public void restorePosition() {
            if (numLines > 0) {
                Ansi ansi = Ansi.ansi();
                out.print(ansi.cursorUpLine(numLines).eraseScreen(Erase.FORWARD).reset());
                numLines = 0;
            }
        }

        @Override
        public <T> void printHeader(List<TableColumn<T>> columns) {
//            ansi.restoreCursorPosition();
//            ansi.saveCursorPosition();
            Ansi ansi = ansi();
            ansi.bold().fg(Ansi.Color.BLACK).bgBright(Ansi.Color.WHITE).a(sep);
            for (TableColumn<T> column : columns) {
                ansi.a(pad).a(column.getPrintName()).a(pad + sep);
            }
            ansi.reset();
            out.println(ansi);
            numLines++;
        }

        @Override
        public void println(String content) {
            if (content == null) {
                out.println();
                numLines++;
            } else {
                long count = content.chars().filter(ch -> ch == '\n').count();
                out.println(content);
                numLines = numLines + (int) count + 1;
            }
        }

        @Override
        public void print(String content) {
            long count = content.chars().filter(ch -> ch == '\n').count();
            out.print(content);
            numLines = numLines + (int) count;
        }

        @Override
        public <T> void printLine(List<TableColumn<T>> columns) {
            Ansi ansi = new Ansi();
            ansi.a(sep);
            for (TableColumn<T> column : columns) {
                ansi.a(column.getLine(pad.length() * 2)).a(sep);
            }
            ansi.reset();
            out.println(ansi);
            numLines++;
        }

        @Override
        public <T> void printFullLine(List<TableColumn<T>> columns) {
            for (TableColumn<T> column : columns) {
                out.print(column.getLine());
                out.print(StringUtils.repeat("-", pad.length() * 2 + sep.length()));
            }
            out.println("-");
            numLines++;
        }

        @Override
        public <T> void printRow(List<TableColumn<T>> columns, int i) {
            Ansi ansi = ansi();
            ansi.a(sep);
//            boolean colour = false;
            for (TableColumn<T> column : columns) {
//                if (colour) {
//                    ansi.bg(Ansi.Color.BLACK);
//                } else {
//                    ansi.bg(Ansi.Color.DEFAULT);
//                }
//                colour = !colour;
                ansi.a(pad).a(column.getPrintValue(i)).a(pad + sep);
            }
            ansi.reset();
            out.println(ansi);
            numLines++;
        }
    }

    public static class AsciiTablePrinter implements TablePrinter {
        private String sep = "|";
        private String pad = " ";
        private PrintStream out;

        public AsciiTablePrinter() {
            out = System.out;
        }

        @Override
        public void restorePosition() {
            return;
        }

        @Override
        public void println(String content) {

        }

        @Override
        public void print(String content) {

        }

        @Override
        public <T> void printHeader(List<TableColumn<T>> columns) {
            out.print(sep);
            for (TableColumn<T> column : columns) {
                out.print(pad);
                out.print(column.getPrintName());
                out.print(pad + sep);
            }
            out.println();
        }

        @Override
        public <T> void printLine(List<TableColumn<T>> columns) {
            out.print(sep);
            for (TableColumn<T> column : columns) {
                out.print(column.getLine(pad.length() * 2));
                out.print(sep);
            }
            out.println();
        }

        @Override
        public <T> void printFullLine(List<TableColumn<T>> columns) {
            for (TableColumn<T> column : columns) {
                out.print(column.getLine());
                out.print(StringUtils.repeat("-", pad.length() * 2 + sep.length()));
            }
            out.println("-");
        }

        @Override
        public <T> void printRow(List<TableColumn<T>> columns, int i) {
            out.print(sep);
            for (TableColumn<T> column : columns) {
                out.print(pad);
                out.print(column.getPrintValue(i));
                out.print(pad + sep);
            }
            out.println();
        }
    }

    public static class TsvTablePrinter implements TablePrinter {
        private String sep = "\t";
        private PrintStream out;

        public TsvTablePrinter() {
            this(System.out);
        }

        public TsvTablePrinter(PrintStream out) {
            this.out = out;
        }

        @Override
        public void restorePosition() {
            return;
        }

        @Override
        public void println(String content) {
            out.println(content);
        }

        @Override
        public void print(String content) {
            out.print(content);
        }

        @Override
        public <T> void printHeader(List<TableColumn<T>> columns) {
            Iterator<TableColumn<T>> iterator = columns.iterator();
            out.print("#");
            while (iterator.hasNext()) {
                TableColumn<T> column = iterator.next();
                out.print(column.getName());
                if (iterator.hasNext()) {
                    out.print(sep);
                }
            }
            out.println();
        }

        @Override
        public <T> void printLine(List<TableColumn<T>> columns) {
        }

        @Override
        public <T> void printFullLine(List<TableColumn<T>> columns) {
        }

        @Override
        public <T> void printRow(List<TableColumn<T>> columns, int i) {
            Iterator<TableColumn<T>> iterator = columns.iterator();
            while (iterator.hasNext()) {
                TableColumn<T> column = iterator.next();
                out.print(column.getValue(i));
                if (iterator.hasNext()) {
                    out.print(sep);
                }
            }
            out.println();
        }
    }
}

