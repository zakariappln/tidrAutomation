package com.ppln.automation.core.bdd;

import io.cucumber.datatable.DataTable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** DataTable to ordered map conversions shared by UI and API glue. */
public final class DataTables {

    private DataTables() {}

    /** Two-column table with a header row ({@code name | value}, {@code path | expected}). */
    public static Map<String, String> asMap(DataTable table) {
        List<List<String>> rows = table.asLists();
        if (rows.size() < 2 || rows.getFirst().size() != 2) {
            throw new IllegalArgumentException(
                    "Expected a two-column table with a header row, got " + rows);
        }
        var out = new LinkedHashMap<String, String>();
        rows.stream().skip(1).forEach(r -> out.put(r.get(0), r.get(1) == null ? "" : r.get(1)));
        return out;
    }

    /** Vertical key/value table without a header row (used by {@code ... exists with:}). */
    public static Map<String, String> asVerticalMap(DataTable table) {
        var out = new LinkedHashMap<String, String>();
        table.asLists()
                .forEach(r -> out.put(r.get(0), r.size() > 1 && r.get(1) != null ? r.get(1) : ""));
        return out;
    }
}
