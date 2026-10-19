package com.ppln.automation.api.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Paged {@code data} wrapper used by list endpoints: {@code { totalRecords, data: [...] }}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Paged<T>(long totalRecords, List<T> data) {

    public List<T> items() {
        return data == null ? List.of() : data;
    }
}
