package com.linktomac;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class Connection {

    public static void readFully(InputStream is, byte[] buffer, int offset, int length) throws IOException {
        readFully(is, buffer, offset, length, 0);
    }
    
    public static void readFully(InputStream is, byte[] buffer, int offset, int length, int timeoutMs) throws IOException {
        int read = 0;
        long startTime = System.currentTimeMillis();
        while (read < length) {
            // Check timeout
            if (timeoutMs > 0 && System.currentTimeMillis() - startTime > timeoutMs) {
                throw new IOException("Read timeout");
            }
            
            int result = is.read(buffer, offset + read, length - read);
            if (result == -1) {
                throw new IOException("End of stream reached");
            }
            read += result;
        }
    }

    public static void writeFully(OutputStream os, byte[] buffer, int offset, int length) throws IOException {
        os.write(buffer, offset, length);
        os.flush();
    }
    
    public static void writeFully(OutputStream os, ByteBuffer buffer) throws IOException {
        byte[] arr = new byte[buffer.remaining()];
        buffer.get(arr);
        writeFully(os, arr, 0, arr.length);
    }
}
