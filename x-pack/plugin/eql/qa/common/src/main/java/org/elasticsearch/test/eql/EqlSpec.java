/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.test.eql;

import org.elasticsearch.common.Strings;

import java.util.Arrays;
import java.util.Objects;

public class EqlSpec {
    private String description;
    private String note;
    private String[] tags;
    private String query;
    private long[] expectedEventIds;

    // flag to dictate which modes are supported for the test
    // at least one must be true
    private boolean caseSensitive = false;
    private boolean caseInsensitive = false;

    public String description() {
        return description;
    }

    public void description(String description) {
        this.description = description;
    }

    public String note() {
        return note;
    }

    public void note(String note) {
        this.note = note;
    }

    public String[] tags() {
        return tags;
    }

    public void tags(String[] tags) {
        this.tags = tags;
    }

    public String query() {
        return query;
    }

    public void query(String query) {
        this.query = query;
    }

    public long[] expectedEventIds() {
        return expectedEventIds;
    }

    public void expectedEventIds(long[] expectedEventIds) {
        this.expectedEventIds = expectedEventIds;
    }

    public void caseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public void caseInsensitive(boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
    }

    public boolean caseSensitive() {
        return this.caseSensitive;
    }

    public boolean caseInsensitive() {
        return this.caseInsensitive;
    }

    @Override
    public String toString() {
        String str = "";
        str = appendWithComma(str, "query", query);
        str = appendWithComma(str, "description", description);
        str = appendWithComma(str, "note", note);

        if (caseInsensitive) {
            str = appendWithComma(str, "case_insensitive", Boolean.toString(caseInsensitive));
        }

        if (caseSensitive) {
            str = appendWithComma(str, "case_sensitive", Boolean.toString(caseSensitive));
        }

        if (tags != null) {
            str = appendWithComma(str, "tags", Arrays.toString(tags));
        }

        if (expectedEventIds != null) {
            str = appendWithComma(str, "expected_event_ids", Arrays.toString(expectedEventIds));
        }
        return str;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        EqlSpec that = (EqlSpec) other;

        return Objects.equals(this.query(), that.query())
                && Objects.equals(this.caseSensitive, that.caseSensitive)
                && Objects.equals(this.caseInsensitive, that.caseInsensitive);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.query, this.caseSensitive, this.caseInsensitive);
    }

    private static String appendWithComma(String str, String name, String append) {
        if (!Strings.isNullOrEmpty(append)) {
            if (!Strings.isNullOrEmpty(str)) {
                str += ", ";
            }
            str += name + ": " + append;
        }
        return str;
    }
}
