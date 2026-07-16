package com.linktomac.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE = 100;
    private MediaProjectionManager projectionManager;
    private EditText etCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Logger.init(this);
        Logger.i("MainActivity created");

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        etCode = findViewById(R.id.et_code);
        Button btnConnect = findViewById(R.id.btnConnect); // Updated ID from XML
        Button btnViewLogs = findViewById(R.id.btnViewLogs);

        if (btnViewLogs != null) {
            btnViewLogs.setOnClickListener(v -> {
                Logger.i("User clicked View Logs");
                String logs = Logger.getLogs();
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Diagnostic Logs")
                        .setMessage(logs)
                        .setPositiveButton("Close", null)
                        .setNeutralButton("Clear Logs", (d, w) -> {
                            Logger.clearLogs();
                            Logger.init(this);
                        })
                        .show();
            });
        }

        btnConnect.setOnClickListener(v -> {
            String code = etCode.getText().toString().trim();
            Logger.i("User clicked Connect with code: " + code);
            if (code.isEmpty()) {
                Logger.e("Connection failed: Empty code");
                Toast.makeText(this, "Enter support code", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE);
        });
        
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            android.net.Uri data = intent.getData();
            if (data != null && "linktomac".equals(data.getScheme())) {
                String code = data.getQueryParameter("code");
                if (code != null) {
                    etCode.setText(code);
                    // Automatically click connect
                    findViewById(R.id.btnConnect).performClick();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {
            if (resultCode != RESULT_OK) {
                Logger.e("Screen capture permission denied by user (resultCode: " + resultCode + ")");
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String code = etCode.getText().toString().trim();
            Logger.i("Screen capture permission granted. Starting ScreenCaptureService with code: " + code);
            Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
            serviceIntent.putExtra("code", code);
            serviceIntent.putExtra("resultCode", resultCode);
            serviceIntent.putExtra("data", data);
            
            startForegroundService(serviceIntent);
            Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
