package com.youlong.hd;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public final class ShellProcessWaitCheck {
    private static final class FakeProcess extends Process {
        private final int pollsUntilExit;
        private int polls;

        FakeProcess(int pollsUntilExit) { this.pollsUntilExit = pollsUntilExit; }

        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() {
            if (++polls >= pollsUntilExit) return 7;
            throw new IllegalThreadStateException("still running");
        }
        @Override public void destroy() {}
    }

    public static void main(String[] args) throws Exception {
        if (!ShellProcessWait.waitFor(new FakeProcess(1), 0, TimeUnit.NANOSECONDS)) {
            throw new AssertionError("already-exited process should return immediately");
        }
        if (ShellProcessWait.waitFor(new FakeProcess(Integer.MAX_VALUE), 0, TimeUnit.NANOSECONDS)) {
            throw new AssertionError("running process should time out immediately");
        }
        if (!ShellProcessWait.waitFor(new FakeProcess(3), 1, TimeUnit.SECONDS)) {
            throw new AssertionError("process exit within timeout was missed");
        }
        System.out.println("PASS process timeout wait on API 24-compatible path");
    }
}
