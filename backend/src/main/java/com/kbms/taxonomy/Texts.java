package com.kbms.taxonomy;

public final class Texts {

    private Texts() {}

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
