package com.linktomac.client;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Logger {
    private static final String TAG = "LinktoMac";
    private static File logFile;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    public static void init(Context context) {
        if (logFile == null) {
            logFile = new File(context.getExternalFilesDir(null), "linktomax_debug.log");
            // Clear old logs if they get too big (e.g. > 2MB)
            if (logFile.exists() && logFile.length() > 2 * 1024 * 1024) {
                logFile.delete();
            }
            i("Logger initialized at " + logFile.getAbsolutePath());
        }
    }

    public static void i(String message) {
        Log.i(TAG, message);
        appendToFile("[INFO] " + message);
    }

    public static void e(String message) {
        Log.e(TAG, message);
        appendToFile("[ERROR] " + message);
    }
    
    public static void e(String message, Throwable t) {
        Log.e(TAG, message, t);
        appendToFile("[ERROR] " + message + " | Exception: " + t.toString());
    }

    public static void d(String message) {
        Log.d(TAG, message);
        appendToFile("[DEBUG] " + message);
    }

    private static synchronized void appendToFile(String log) {
        if (logFile == null) return;
        try (FileWriter fw = new FileWriter(logFile, true)) {
            fw.write(sdf.format(new Date()) + " " + log + "\n");
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to log file", e);
        }
    }
    
    public static String getLogs() {
        if (logFile == null || !logFile.exists()) return "No logs available.";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            return "Failed to read logs: " + e.getMessage();
        }
        return sb.toString();
    }
    
    public static void clearLogs() {
        if (logFile != null && logFile.exists()) {
            logFile.delete();
        }
    }
}
