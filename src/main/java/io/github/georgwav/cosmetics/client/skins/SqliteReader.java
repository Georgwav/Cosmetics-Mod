package io.github.georgwav.cosmetics.client.skins;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads tables of an SQLite database file, never writing to it: the launcher's own database (its saved skins) is
 * only ever looked at. Follows the file format (sqlite.org/fileformat2.html): table b-trees, records, overflow
 * pages, and the write-ahead log, whose committed pages are newer than the file's while the launcher runs.
 */
public final class SqliteReader implements AutoCloseable {
    private final FileChannel file;
    private final int pageSize;
    private final int usable;
    /** Pages the write-ahead log has newer copies of: page number -> its copy. */
    private final Map<Integer, byte[]> walPages;
    private final Map<String, Table> tables = new HashMap<>();

    /**
     * A table: where its b-tree starts, its columns' names in order, which column is the row id (or -1), and for a
     * WITHOUT ROWID table the column each record value belongs to (its key's columns come first), else null.
     */
    private record Table(int rootPage, List<String> columns, int rowidColumn, int[] recordOrder) {
    }

    private SqliteReader(FileChannel file, int pageSize, int reserved, Map<Integer, byte[]> walPages) {
        this.file = file;
        this.pageSize = pageSize;
        this.usable = pageSize - reserved;
        this.walPages = walPages;
    }

    public static SqliteReader open(Path db) throws IOException {
        FileChannel file = FileChannel.open(db, StandardOpenOption.READ);
        try {
            ByteBuffer header = ByteBuffer.allocate(100);
            readFully(file, header, 0);
            byte[] magic = new byte[16];
            header.get(0, magic);
            if (!new String(magic, StandardCharsets.US_ASCII).equals("SQLite format 3\0")) {
                throw new IOException(db + " isn't an SQLite database");
            }
            int pageSize = Short.toUnsignedInt(header.getShort(16));
            if (pageSize == 1) {
                pageSize = 65536;
            }
            int reserved = Byte.toUnsignedInt(header.get(20));
            if (header.getInt(56) > 1) {
                throw new IOException(db + " stores text as UTF-16, which isn't read");
            }
            Map<Integer, byte[]> wal = readWal(Path.of(db + "-wal"), pageSize);
            SqliteReader reader = new SqliteReader(file, pageSize, reserved, wal);
            reader.readSchema();
            return reader;
        } catch (IOException | RuntimeException e) {
            file.close();
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    private static void readFully(FileChannel file, ByteBuffer into, long position) throws IOException {
        while (into.hasRemaining()) {
            int n = file.read(into, position + into.position());
            if (n < 0) {
                throw new IOException("unexpected end of the database file");
            }
        }
    }

    /**
     * The newest committed copy of each page in the write-ahead log. Frames count only up to the last one that ends
     * a transaction, and only while their salts match the log's header (older frames from before a checkpoint
     * don't).
     */
    private static Map<Integer, byte[]> readWal(Path wal, int pageSize) throws IOException {
        Map<Integer, byte[]> pages = new HashMap<>();
        if (!Files.isRegularFile(wal) || Files.size(wal) < 32) {
            return pages;
        }
        byte[] all = Files.readAllBytes(wal);
        ByteBuffer b = ByteBuffer.wrap(all);
        int magic = b.getInt(0);
        if ((magic & 0xFFFFFFFE) != 0x377F0682 || b.getInt(8) != pageSize) {
            return pages;
        }
        int salt1 = b.getInt(16);
        int salt2 = b.getInt(20);
        Map<Integer, byte[]> pending = new HashMap<>();
        for (int at = 32; at + 24 + pageSize <= all.length; at += 24 + pageSize) {
            if (b.getInt(at + 8) != salt1 || b.getInt(at + 12) != salt2) {
                break;
            }
            int page = b.getInt(at);
            byte[] copy = new byte[pageSize];
            System.arraycopy(all, at + 24, copy, 0, pageSize);
            pending.put(page, copy);
            if (b.getInt(at + 4) != 0) {
                // The frame that commits a transaction: everything up to it counts.
                pages.putAll(pending);
                pending.clear();
            }
        }
        return pages;
    }

    private ByteBuffer page(int number) throws IOException {
        byte[] fromWal = walPages.get(number);
        if (fromWal != null) {
            return ByteBuffer.wrap(fromWal);
        }
        ByteBuffer page = ByteBuffer.allocate(pageSize);
        readFully(file, page, (long) (number - 1) * pageSize);
        return page;
    }

    // ------------------------------------------------------------------ schema

    private void readSchema() throws IOException {
        // sqlite_schema: type, name, tbl_name, rootpage, sql.
        for (Object[] row : rows(1)) {
            if ("table".equals(row[0]) && row[1] instanceof String name && row[3] instanceof Long root
                    && row[4] instanceof String sql) {
                List<String> columns = new ArrayList<>();
                List<String> key = new ArrayList<>();
                int rowid = columns(sql, columns, key);
                int[] order = null;
                if (sql.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").contains("WITHOUT ROWID")) {
                    // Stored as an index: the key's columns, then the others in order.
                    rowid = -1;
                    List<Integer> o = new ArrayList<>();
                    for (String k : key) {
                        o.add(indexOf(columns, k));
                    }
                    for (int c = 0; c < columns.size(); c++) {
                        if (!o.contains(c)) {
                            o.add(c);
                        }
                    }
                    order = o.stream().mapToInt(Integer::intValue).toArray();
                }
                tables.put(name.toLowerCase(Locale.ROOT), new Table(root.intValue(), columns, rowid, order));
            }
        }
    }

    private static int indexOf(List<String> columns, String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static String unquote(String name) {
        String n = name.trim();
        if (n.length() >= 2 && (n.charAt(0) == '"' || n.charAt(0) == '`' || n.charAt(0) == '[')) {
            return n.substring(1, n.length() - 1);
        }
        return n;
    }

    /**
     * The column names of a CREATE TABLE statement, into {@code out}, and its primary key's columns into
     * {@code key}; returns the index of the INTEGER PRIMARY KEY column (stored as the row id, not in the record), or
     * -1.
     */
    static int columns(String sql, List<String> out, List<String> key) {
        int open = sql.indexOf('(');
        int close = sql.lastIndexOf(')');
        if (open < 0 || close < open) {
            return -1;
        }
        int rowid = -1;
        for (String def : splitTopLevel(sql.substring(open + 1, close))) {
            String d = def.trim();
            String upper = d.toUpperCase(Locale.ROOT);
            if (upper.startsWith("PRIMARY KEY")) {
                int open2 = d.indexOf('(');
                int close2 = d.lastIndexOf(')');
                if (open2 >= 0 && close2 > open2) {
                    for (String k : splitTopLevel(d.substring(open2 + 1, close2))) {
                        String col = k.trim().split("\\s+")[0];
                        key.add(unquote(col));
                    }
                }
                continue;
            }
            if (upper.startsWith("UNIQUE") || upper.startsWith("FOREIGN KEY") || upper.startsWith("CHECK")
                    || upper.startsWith("CONSTRAINT")) {
                continue;
            }
            String name;
            String rest;
            char q = d.isEmpty() ? ' ' : d.charAt(0);
            if (q == '"' || q == '`' || q == '[') {
                int end = d.indexOf(q == '[' ? ']' : q, 1);
                name = d.substring(1, end);
                rest = d.substring(end + 1);
            } else {
                int end = 0;
                while (end < d.length() && !Character.isWhitespace(d.charAt(end))) {
                    end++;
                }
                name = d.substring(0, end);
                rest = d.substring(end);
            }
            String type = rest.trim().toUpperCase(Locale.ROOT);
            if (type.contains("PRIMARY KEY")) {
                key.add(name);
                if (type.startsWith("INTEGER") && !type.contains("DESC")) {
                    rowid = out.size();
                }
            }
            out.add(name);
        }
        return rowid;
    }

    private static List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"' || c == '`') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    public boolean hasTable(String name) {
        return tables.containsKey(name.toLowerCase(Locale.ROOT));
    }

    /** Every row of a table, each as column name -> value (Long, Double, String, byte[] or null). */
    public List<Map<String, Object>> table(String name) throws IOException {
        Table table = tables.get(name.toLowerCase(Locale.ROOT));
        if (table == null) {
            throw new IOException("no table " + name);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        List<long[]> rowids = new ArrayList<>();
        List<Object[]> rows = rows(table.rootPage, rowids);
        for (int r = 0; r < rows.size(); r++) {
            Object[] values = rows.get(r);
            Object[] byColumn = new Object[table.columns.size()];
            for (int i = 0; i < values.length; i++) {
                int c = table.recordOrder == null ? i : i < table.recordOrder.length ? table.recordOrder[i] : -1;
                if (c >= 0 && c < byColumn.length) {
                    byColumn[c] = values[i];
                }
            }
            if (table.rowidColumn >= 0) {
                byColumn[table.rowidColumn] = rowids.get(r)[0];
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int c = 0; c < byColumn.length; c++) {
                row.put(table.columns.get(c), byColumn[c]);
            }
            result.add(row);
        }
        return result;
    }

    // ------------------------------------------------------------------ b-trees and records

    private List<Object[]> rows(int root) throws IOException {
        return rows(root, new ArrayList<>());
    }

    private List<Object[]> rows(int root, List<long[]> rowids) throws IOException {
        List<Object[]> out = new ArrayList<>();
        walk(root, out, rowids, 0);
        return out;
    }

    private void walk(int number, List<Object[]> out, List<long[]> rowids, int depth) throws IOException {
        if (depth > 64) {
            throw new IOException("b-tree too deep: the file is damaged");
        }
        ByteBuffer page = page(number);
        int base = number == 1 ? 100 : 0;
        int type = Byte.toUnsignedInt(page.get(base));
        int cells = Short.toUnsignedInt(page.getShort(base + 3));
        if (type == 0x05) {
            // Interior table page: each cell points at a child left of its key, then the right-most child.
            for (int i = 0; i < cells; i++) {
                int cell = Short.toUnsignedInt(page.getShort(base + 12 + 2 * i));
                walk(page.getInt(cell), out, rowids, depth + 1);
            }
            walk(page.getInt(base + 8), out, rowids, depth + 1);
        } else if (type == 0x0D) {
            for (int i = 0; i < cells; i++) {
                int cell = Short.toUnsignedInt(page.getShort(base + 8 + 2 * i));
                long[] at = {cell};
                long payload = varint(page, at);
                long rowid = varint(page, at);
                byte[] record = payload(page, (int) at[0], (int) payload, false);
                out.add(record(record));
                rowids.add(new long[]{rowid});
            }
        } else if (type == 0x02) {
            // Interior index page (a WITHOUT ROWID table): rows sit in the interior cells too, in key order.
            for (int i = 0; i < cells; i++) {
                int cell = Short.toUnsignedInt(page.getShort(base + 12 + 2 * i));
                walk(page.getInt(cell), out, rowids, depth + 1);
                long[] at = {cell + 4};
                long payload = varint(page, at);
                out.add(record(payload(page, (int) at[0], (int) payload, true)));
                rowids.add(new long[]{0});
            }
            walk(page.getInt(base + 8), out, rowids, depth + 1);
        } else if (type == 0x0A) {
            for (int i = 0; i < cells; i++) {
                int cell = Short.toUnsignedInt(page.getShort(base + 8 + 2 * i));
                long[] at = {cell};
                long payload = varint(page, at);
                out.add(record(payload(page, (int) at[0], (int) payload, true)));
                rowids.add(new long[]{0});
            }
        } else {
            throw new IOException("page " + number + " isn't a table page (type " + type + ")");
        }
    }

    /** A cell's whole payload: the part on its page, then any overflow pages (index pages keep less on the page). */
    private byte[] payload(ByteBuffer page, int start, int size, boolean index) throws IOException {
        int maxLocal = index ? (usable - 12) * 64 / 255 - 23 : usable - 35;
        int local;
        if (size <= maxLocal) {
            local = size;
        } else {
            int minLocal = (usable - 12) * 32 / 255 - 23;
            int k = minLocal + (size - minLocal) % (usable - 4);
            local = k <= maxLocal ? k : minLocal;
        }
        byte[] out = new byte[size];
        page.get(start, out, 0, local);
        int filled = local;
        int next = local < size ? page.getInt(start + local) : 0;
        int hops = 0;
        while (filled < size && next != 0) {
            if (++hops > 1_000_000) {
                throw new IOException("overflow chain loops: the file is damaged");
            }
            ByteBuffer overflow = page(next);
            int n = Math.min(usable - 4, size - filled);
            overflow.get(4, out, filled, n);
            filled += n;
            next = overflow.getInt(0);
        }
        if (filled < size) {
            throw new IOException("a row's overflow pages end early: the file is damaged");
        }
        return out;
    }

    private static Object[] record(byte[] record) {
        ByteBuffer b = ByteBuffer.wrap(record);
        long[] at = {0};
        long headerSize = varint(b, at);
        List<Long> types = new ArrayList<>();
        while (at[0] < headerSize) {
            types.add(varint(b, at));
        }
        Object[] values = new Object[types.size()];
        int pos = (int) headerSize;
        for (int i = 0; i < types.size(); i++) {
            long t = types.get(i);
            if (t == 0) {
                values[i] = null;
            } else if (t >= 1 && t <= 6) {
                int n = t <= 4 ? (int) t : t == 5 ? 6 : 8;
                long v = b.get(pos);
                for (int k = 1; k < n; k++) {
                    v = v << 8 | Byte.toUnsignedLong(b.get(pos + k));
                }
                values[i] = v;
                pos += n;
            } else if (t == 7) {
                values[i] = b.getDouble(pos);
                pos += 8;
            } else if (t == 8 || t == 9) {
                values[i] = t - 8;
            } else if (t >= 12) {
                int n = (int) ((t - 12) / 2);
                byte[] bytes = new byte[n];
                b.get(pos, bytes);
                values[i] = t % 2 == 0 ? bytes : new String(bytes, StandardCharsets.UTF_8);
                pos += n;
            }
        }
        return values;
    }

    /** SQLite's big-endian varint (1 to 9 bytes) at at[0], which moves past it. */
    private static long varint(ByteBuffer b, long[] at) {
        long v = 0;
        int p = (int) at[0];
        for (int i = 0; i < 8; i++) {
            int c = Byte.toUnsignedInt(b.get(p + i));
            v = v << 7 | (c & 0x7F);
            if ((c & 0x80) == 0) {
                at[0] = p + i + 1;
                return v;
            }
        }
        v = v << 8 | Byte.toUnsignedInt(b.get(p + 8));
        at[0] = p + 9;
        return v;
    }
}
