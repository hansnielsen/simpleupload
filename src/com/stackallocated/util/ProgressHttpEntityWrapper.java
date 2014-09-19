package com.stackallocated.util;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

public class ProgressHttpEntityWrapper extends HttpEntityWrapper {
    ProgressListener listener;

    public ProgressHttpEntityWrapper(final HttpEntity entity, final ProgressListener listener) {
        super(entity);
        this.listener = listener;
    }

    public void writeTo(OutputStream output) throws IOException {
        OutputStream wrapper = output;
        if (output.getClass() != CountedOutputStream.class && this.listener != null) {
            wrapper = new CountedOutputStream(output, listener);
        }
        this.wrappedEntity.writeTo(wrapper);
    }
}