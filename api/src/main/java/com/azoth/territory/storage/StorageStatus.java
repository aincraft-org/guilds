package com.azoth.territory.storage;

/** Stable outcomes shared by all guild storage operations. */
public enum StorageStatus {
    SUCCESS,
    NOT_RESIDENT,
    WRONG_FACILITY,
    WRONG_GUILD,
    INSUFFICIENT_RANK,
    INVALID_ITEM,
    CONFLICT,
    STORAGE_ERROR
}
