package com.youlong.hd;

import java.util.concurrent.TimeUnit;

final class ShellProcessWait {
    private static final long POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

    private ShellProcessWait() {}

    static boolean waitFor(Process process, long timeout, TimeUnit unit) throws InterruptedException {
        if (hasExited(process)) return true;
        long timeoutNanos = unit.toNanos(timeout);
        if (timeoutNanos <= 0) return false;
        long started = System.nanoTime();
        while (true) {
            long elapsed = System.nanoTime() - started;
            long remaining = timeoutNanos - elapsed;
            if (remaining <= 0) return false;
            TimeUnit.NANOSECONDS.sleep(Math.min(remaining, POLL_NANOS));
            if (hasExited(process)) return System.nanoTime() - started <= timeoutNanos;
        }
    }

    private static boolean hasExited(Process process) {
        try {
            process.exitValue();
            return true;
        } catch (IllegalThreadStateException running) {
            return false;
        }
    }
}
