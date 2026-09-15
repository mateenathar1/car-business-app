package com.carbusiness.app;

import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.webkit.*;
import android.net.Uri;
import android.app.AlertDialog;
import android.widget.EditText;
import android.text.InputType;

import androidx.fragment.app.FragmentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends FragmentActivity {

    private WebView webView;
    private byte[] pendingBytes;
    private ValueCallback<Uri[]> fileChooserCallback;

    private ActivityResultLauncher<Intent> saveLauncher;
    private ActivityResultLauncher<Intent> openLauncher;

    private SharedPreferences prefs;
    private long backgroundAt = 0;
    private boolean unlocked = false;

    private File backupDir;
    private File permanentDir;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        prefs = getSharedPreferences("mw_secure", MODE_PRIVATE);

        backupDir = new File(
                getFilesDir(),
                "My Wheels/Backups/Recovery"
        );

        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        permanentDir = new File(
                getFilesDir(),
                "My Wheels/Backups/Permanent"
        );

        if (!permanentDir.exists()) {
            permanentDir.mkdirs();
        }

        saveLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {

                            if (result.getResultCode() == RESULT_OK
                                    && result.getData() != null
                                    && result.getData().getData() != null
                                    && pendingBytes != null) {

                                try (OutputStream out =
                                             getContentResolver()
                                                     .openOutputStream(
                                                             result.getData().getData()
                                                     )) {

                                    out.write(pendingBytes);
                                    out.flush();

                                    js("toast('File saved successfully')");

                                } catch (Exception e) {

                                    js(
                                            "alert('Could not save file. " +
                                            "Please try another folder.')"
                                    );
                                }
                            }

                            pendingBytes = null;
                        }
                );

        openLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {

                            if (fileChooserCallback == null) {
                                return;
                            }

                            Uri[] uris = null;

                            if (result.getResultCode() == RESULT_OK
                                    && result.getData() != null
                                    && result.getData().getData() != null) {

                                uris = new Uri[]{
                                        result.getData().getData()
                                };
                            }

                            fileChooserCallback.onReceiveValue(uris);
                            fileChooserCallback = null;
                        }
                );

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(
                new WebChromeClient() {

                    @Override
                    public boolean onShowFileChooser(
                            WebView view,
                            ValueCallback<Uri[]> callback,
                            FileChooserParams params
                    ) {

                        if (fileChooserCallback != null) {
                            fileChooserCallback.onReceiveValue(null);
                        }

                        fileChooserCallback = callback;

                        Intent intent =
                                new Intent(Intent.ACTION_OPEN_DOCUMENT);

                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");

                        openLauncher.launch(intent);

                        return true;
                    }
                }
        );

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "AndroidBridge"
        );

        webView.loadUrl(
                "file:///android_asset/index.html"
        );

        if (isLockEnabled()) {
            showUnlock();
        } else {
            unlocked = true;
        }
    }

    private void js(String code) {
        runOnUiThread(
                () -> webView.evaluateJavascript(
                        code,
                        null
                )
        );
    }

    private String sha(String value) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] result =
                    digest.digest(
                            value.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder output =
                    new StringBuilder();

            for (byte item : result) {
                output.append(
                        String.format(
                                "%02x",
                                item
                        )
                );
            }

            return output.toString();

        } catch (Exception e) {
            return "";
        }
    }

    private boolean isLockEnabled() {
        return !prefs
                .getString(
                        "pin_hash",
                        ""
                )
                .isEmpty();
    }

    private void showUnlock() {

        if (unlocked) {
            return;
        }

        int result =
                androidx.biometric.BiometricManager
                        .from(this)
                        .canAuthenticate(
                                androidx.biometric.BiometricManager
                                        .Authenticators
                                        .BIOMETRIC_STRONG
                                        |
                                androidx.biometric.BiometricManager
                                        .Authenticators
                                        .DEVICE_CREDENTIAL
                        );

        if (result ==
                androidx.biometric.BiometricManager
                        .BIOMETRIC_SUCCESS) {

            doBiometric();
            return;
        }

        showPinDialog();
    }

    private void doBiometric() {

        BiometricPrompt prompt =
                new BiometricPrompt(
                        this,
                        ContextCompat.getMainExecutor(this),
                        new BiometricPrompt.AuthenticationCallback() {

                            @Override
                            public void onAuthenticationSucceeded(
                                    BiometricPrompt.AuthenticationResult result
                            ) {

                                super.onAuthenticationSucceeded(result);

                                unlocked = true;
                            }

                            @Override
                            public void onAuthenticationError(
                                    int errorCode,
                                    CharSequence errString
                            ) {

                                super.onAuthenticationError(
                                        errorCode,
                                        errString
                                );

                                showPinDialog();
                            }
                        }
                );

        BiometricPrompt.PromptInfo promptInfo =
                new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(
                                "Unlock My Wheels"
                        )
                        .setSubtitle(
                                "Use fingerprint, face or device credential"
                        )
                        .setAllowedAuthenticators(
                                androidx.biometric.BiometricManager
                                        .Authenticators
                                        .BIOMETRIC_STRONG
                                        |
                                androidx.biometric.BiometricManager
                                        .Authenticators
                                        .DEVICE_CREDENTIAL
                        )
                        .build();

        prompt.authenticate(promptInfo);
    }

    private void showPinDialog() {

        EditText input =
                new EditText(this);

        input.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        |
                InputType.TYPE_NUMBER_VARIATION_PASSWORD
        );

        input.setHint("PIN");

        new AlertDialog.Builder(this)
                .setTitle(
                        "My Wheels Locked"
                )
                .setMessage(
                        "Enter PIN. Use Recovery if you forgot it."
                )
                .setView(input)
                .setCancelable(false)
                .setPositiveButton(
                        "Unlock",
                        (dialog, which) -> {

                            String entered =
                                    input
                                            .getText()
                                            .toString();

                            String saved =
                                    prefs.getString(
                                            "pin_hash",
                                            ""
                                    );

                            if (sha(entered).equals(saved)) {

                                unlocked = true;

                            } else {

                                new AlertDialog.Builder(this)
                                        .setMessage(
                                                "Incorrect PIN"
                                        )
                                        .setPositiveButton(
                                                "Try again",
                                                (d, w) ->
                                                        showPinDialog()
                                        )
                                        .show();
                            }
                        }
                )
                .setNegativeButton(
                        "Recovery",
                        (dialog, which) ->
                                showRecoveryDialog()
                )
                .show();
    }

    private void showRecoveryDialog() {

        EditText input =
                new EditText(this);

        input.setHint(
                "Recovery code"
        );

        new AlertDialog.Builder(this)
                .setTitle(
                        "Recover App Access"
                )
                .setMessage(
                        "Enter your recovery code. " +
                        "Your business data will not be deleted."
                )
                .setView(input)
                .setCancelable(false)
                .setPositiveButton(
                        "Verify",
                        (dialog, which) -> {

                            String code =
                                    input
                                            .getText()
                                            .toString()
                                            .trim()
                                            .toUpperCase(
                                                    Locale.ROOT
                                            );

                            String stored =
                                    prefs.getString(
                                            "recovery_hash",
                                            ""
                                    );

                            if (sha(code).equals(stored)) {

                                prefs.edit()
                                        .remove(
                                                "pin_hash"
                                        )
                                        .apply();

                                unlocked = true;

                                js(
                                        "toast('PIN reset. " +
                                        "Set a new PIN in Settings.')"
                                );

                            } else {

                                showPinDialog();
                            }
                        }
                )
                .setNegativeButton(
                        "Back",
                        (dialog, which) ->
                                showPinDialog()
                )
                .show();
    }

    private String ensureRecovery() {

        String raw =
                prefs.getString(
                        "recovery_plain",
                        ""
                );

        if (raw.isEmpty()) {

            raw =
                    "MW-"
                    +
                    UUID.randomUUID()
                            .toString()
                            .replace("-", "")
                            .substring(0, 4)
                            .toUpperCase()
                    +
                    "-"
                    +
                    UUID.randomUUID()
                            .toString()
                            .replace("-", "")
                            .substring(0, 4)
                            .toUpperCase()
                    +
                    "-"
                    +
                    UUID.randomUUID()
                            .toString()
                            .replace("-", "")
                            .substring(0, 4)
                            .toUpperCase();

            prefs.edit()
                    .putString(
                            "recovery_plain",
                            raw
                    )
                    .putString(
                            "recovery_hash",
                            sha(raw)
                    )
                    .apply();
        }

        return raw;
    }

    private void writeAuto(
            String text
    ) {

        try {

            String name =
                    "MyWheels_"
                    +
                    new SimpleDateFormat(
                            "yyyy-MM-dd_HHmm",
                            Locale.US
                    )
                            .format(
                                    new Date()
                            )
                    +
                    ".backup";

            File file =
                    new File(
                            backupDir,
                            name
                    );

            try (FileOutputStream output =
                         new FileOutputStream(file)) {

                output.write(
                        text.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
            }

            prune();

            prefs.edit()
                    .putLong(
                            "last_backup",
                            System.currentTimeMillis()
                    )
                    .apply();

            js(
                    "toast('Automatic backup saved')"
            );

        } catch (Exception e) {

            js(
                    "toast('Automatic backup failed')"
            );
        }
    }

    private void writePermanent(
            String text
    ) {

        try {

            String name =
                    "MyWheels_Weekly_"
                    +
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                    )
                            .format(
                                    new Date()
                            )
                    +
                    ".backup";

            File file =
                    new File(
                            permanentDir,
                            name
                    );

            if (!file.exists()) {

                try (FileOutputStream output =
                             new FileOutputStream(file)) {

                    output.write(
                            text.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );
                }

                prefs.edit()
                        .putLong(
                                "last_permanent_backup",
                                System.currentTimeMillis()
                        )
                        .apply();

                js(
                        "toast('Permanent weekly backup saved')"
                );
            }

        } catch (Exception e) {

            js(
                    "toast('Permanent backup failed')"
            );
        }
    }

    private void prune() {

        int days =
                prefs.getInt(
                        "retention",
                        14
                );

        long cutoff =
                System.currentTimeMillis()
                -
                days * 86400000L;

        File[] files =
                backupDir.listFiles();

        if (files != null) {

            for (File file : files) {

                if (file.lastModified() < cutoff) {
                    file.delete();
                }
            }
        }
    }

    private void maybeBackup() {

        String schedule =
                prefs.getString(
                        "schedule",
                        "twice"
                );

        long now =
                System.currentTimeMillis();

        if (!schedule.equals("off")) {

            long gap =
                    schedule.equals("daily")
                            ? 86400000L
                            : 43200000L;

            if (
                    now
                    -
                    prefs.getLong(
                            "last_backup",
                            0
                    )
                    >=
                    gap
            ) {

                js(
                        "if(window.AndroidBridge){" +
                        "AndroidBridge.createAutoBackup(" +
                        "JSON.stringify({...state," +
                        "exportedAt:new Date().toISOString()}))}"
                );
            }
        }

        if (
                now
                -
                prefs.getLong(
                        "last_permanent_backup",
                        0
                )
                >=
                604800000L
        ) {

            js(
                    "if(window.AndroidBridge){" +
                    "AndroidBridge.createPermanentBackup(" +
                    "JSON.stringify({...state," +
                    "exportedAt:new Date().toISOString()}))}"
            );
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        if (
                backgroundAt > 0
                &&
                isLockEnabled()
        ) {

            int minutes =
                    prefs.getInt(
                            "lock_minutes",
                            0
                    );

            if (
                    minutes == 0
                    ||
                    System.currentTimeMillis()
                    -
                    backgroundAt
                    >=
                    minutes * 60000L
            ) {

                unlocked = false;
                showUnlock();
            }
        }

        maybeBackup();
    }

    @Override
    protected void onPause() {

        backgroundAt =
                System.currentTimeMillis();

        super.onPause();
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void saveTextFile(
                String name,
                String mime,
                String text
        ) {

            pendingBytes =
                    text.getBytes(
                            StandardCharsets.UTF_8
                    );

            runOnUiThread(
                    () -> {

                        Intent intent =
                                new Intent(
                                        Intent.ACTION_CREATE_DOCUMENT
                                );

                        intent.addCategory(
                                Intent.CATEGORY_OPENABLE
                        );

                        intent.setType(mime);

                        intent.putExtra(
                                Intent.EXTRA_TITLE,
                                name
                        );

                        saveLauncher.launch(intent);
                    }
            );
        }

        @JavascriptInterface
        public String setAppPin(
                String pin,
                int minutes
        ) {

            if (!pin.matches("\\d{4,8}")) {
                return "Invalid PIN";
            }

            prefs.edit()
                    .putString(
                            "pin_hash",
                            sha(pin)
                    )
                    .putInt(
                            "lock_minutes",
                            minutes
                    )
                    .apply();

            unlocked = true;

            return "PIN enabled. Recovery code: "
                    +
                    ensureRecovery();
        }

        @JavascriptInterface
        public boolean isLockEnabled() {
            return MainActivity.this
                    .isLockEnabled();
        }

        @JavascriptInterface
        public String getRecoveryCode() {
            return ensureRecovery();
        }

        @JavascriptInterface
        public void requestBiometric() {

            runOnUiThread(
                    () -> doBiometric()
            );
        }

        @JavascriptInterface
        public void configureBackups(
                String schedule,
                int days
        ) {

            prefs.edit()
                    .putString(
                            "schedule",
                            schedule
                    )
                    .putInt(
                            "retention",
                            days
                    )
                    .apply();

            prune();
        }

        @JavascriptInterface
        public void createAutoBackup(
                String text
        ) {

            writeAuto(text);
        }

        @JavascriptInterface
        public void createPermanentBackup(
                String text
        ) {

            writePermanent(text);
        }

        @JavascriptInterface
        public String getBackupHistory(
                String kind
        ) {

            JSONArray array =
                    new JSONArray();

            File directory =
                    "permanent".equals(kind)
                            ? permanentDir
                            : backupDir;

            try {

                File[] files =
                        directory.listFiles();

                if (files != null) {

                    Arrays.sort(
                            files,
                            (a, b) ->
                                    Long.compare(
                                            b.lastModified(),
                                            a.lastModified()
                                    )
                    );

                    for (File file : files) {

                        JSONObject object =
                                new JSONObject();

                        object.put(
                                "name",
                                file.getName()
                        );

                        object.put(
                                "time",
                                new SimpleDateFormat(
                                        "dd MMM yyyy, hh:mm a",
                                        Locale.US
                                )
                                        .format(
                                                new Date(
                                                        file.lastModified()
                                                )
                                        )
                        );

                        object.put(
                                "size",
                                Math.max(
                                        1,
                                        file.length() / 1024
                                )
                                +
                                " KB"
                        );

                        object.put(
                                "kind",
                                kind
                        );

                        array.put(object);
                    }
                }

            } catch (Exception ignored) {
            }

            return array.toString();
        }

        @JavascriptInterface
        public void restoreBackup(
                String kind,
                String name
        ) {

            try {

                File directory =
                        "permanent".equals(kind)
                                ? permanentDir
                                : backupDir;

                File file =
                        new File(
                                directory,
                                name
                        );

                String text =
                        new String(
                                java.nio.file.Files
                                        .readAllBytes(
                                                file.toPath()
                                        ),
                                StandardCharsets.UTF_8
                        );

                String quoted =
                        JSONObject.quote(text);

                js(
                        "try{" +
                        "const x=JSON.parse("
                        +
                        quoted
                        +
                        ");" +
                        "if(confirm('Restore this backup? " +
                        "Current data will be replaced.')){" +
                        "state={" +
                        "cars:x.cars||[]," +
                        "ledger:x.ledger||[]," +
                        "clients:x.clients||[]," +
                        "meta:{...(x.meta||{}),version:10}" +
                       
