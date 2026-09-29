package com.kbms.user;

public enum Role {
    ADMIN,
    EDITOR,
    VIEWER;

    /** ADMIN and EDITOR may see unpublished content and manage content/documents. */
    public boolean isStaff() {
        return this != VIEWER;
    }
}
