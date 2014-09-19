package com.stackallocated.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CountedOutputStream extends FilterOutputStream {
    long bytes = 0;
    ProgressListener listener;

    public CountedOutputStream(OutputStream out, ProgressListener listener) {
        super(out);
        this.listener = listener;
    }

    @Override
    public void write(int b) throws IOException {
        byte[] arr = new byte[]{(byte) b};
        write(arr, 0, arr.length);
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        bytes += len;
        listener.progress(bytes);
    }
}