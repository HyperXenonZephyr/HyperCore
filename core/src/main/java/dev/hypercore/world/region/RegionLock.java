package dev.hypercore.world.region;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Advisory read/write lock for a single region.
 *
 * <p>Region locks guarantee that only one thread mutates a region at a time,
 * while allowing concurrent readers. They are used by
 * {@link dev.hypercore.world.RegionExecutionService} to serialize Bukkit-triggered
 * world mutations and by {@link dev.hypercore.region.RegionTaskCoordinator} to
 * protect region ticks running on worker threads.
 */
public final class RegionLock {
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Runs the given task while holding the read lock.
     */
    public void read(Runnable task) {
        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            task.run();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Runs the given task while holding the read lock and returns its result.
     */
    public <T> T read(Callable<T> task) throws Exception {
        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            return task.call();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Runs the given task while holding the write lock.
     */
    public void write(Runnable task) {
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            task.run();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Runs the given task while holding the write lock and returns its result.
     */
    public <T> T write(Callable<T> task) throws Exception {
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            return task.call();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns {@code true} if the current thread holds the write lock.
     */
    public boolean isWriteLockedByCurrentThread() {
        return ((ReentrantReadWriteLock) lock).isWriteLockedByCurrentThread();
    }
}
