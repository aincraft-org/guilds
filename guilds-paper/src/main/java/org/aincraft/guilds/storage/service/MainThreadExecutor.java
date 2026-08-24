package org.aincraft.guilds.storage.service;

/** Runs work on the Paper main thread. */
@FunctionalInterface
public interface MainThreadExecutor {
    void run(Runnable task);
}
