package nz.bdt.androidopenfile;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;

import android.Manifest;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.app.Activity;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class OpenFile extends CordovaPlugin {
    private static final int PICK_PDF_REQUEST_CODE = 1;
    private static final int REQUEST_CODE_MANAGE_STORAGE = 1001;
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1002;
    private CallbackContext callbackContext;
    private Uri pendingMediaStoreUri = null; // set when cleanup:true, cleared in onResume

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;

        if ("openPdfUsingSAF".equals(action)) {
            openPdfWithSAF();
            return true;
        } else if ("openDownloadedFile".equals(action)) {
            try {
                String filePath = args.getString(0);
                boolean cleanup = true;
                if (args.length() > 1) {
                    org.json.JSONObject options = args.optJSONObject(1);
                    if (options != null) {
                        cleanup = options.optBoolean("cleanup", true);
                    }
                }
                openDownloadedFile(filePath, cleanup);
                return true;
            } catch (Exception e) {
                callbackContext.error("Error processing the file path: " + e.getMessage());
                return false;
            }
        } else if ("requestStoragePermissions".equals(action)) {
            requestStoragePermission();
            return true;
        }
        return false;
    }

    private void requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", cordova.getActivity().getPackageName(), null);
                intent.setData(uri);
                cordova.getActivity().startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE);
            } catch (Exception e) {
                callbackContext.error("Failed to request manage storage permission: " + e.getMessage());
            }
        }
    }

    private void requestStoragePermission() {
        if (hasStoragePermission()) {
            callbackContext.success("Storage permission already granted");
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(cordova.getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                ActivityCompat.shouldShowRequestPermissionRationale(cordova.getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE)) {
                callbackContext.error("Storage permission is required to proceed.");
            }
            ActivityCompat.requestPermissions(
                cordova.getActivity(),
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                REQUEST_CODE_STORAGE_PERMISSION
            );
        }
    }

    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(cordova.getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(cordova.getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                callbackContext.success("Storage permission granted");
            } else {
                callbackContext.error("Storage permission denied by the user");
            }
        } else {
            super.onRequestPermissionResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_PDF_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            openFile(uri);
        } else if (requestCode == REQUEST_CODE_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                callbackContext.success("Manage storage permission granted");
            } else {
                callbackContext.error("Manage storage permission denied");
            }
        } else {
            callbackContext.error("No PDF selected or an error occurred.");
        }
    }

    private void openPdfWithSAF() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        cordova.setActivityResultCallback(this);
        cordova.getActivity().startActivityForResult(intent, PICK_PDF_REQUEST_CODE);
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        if (pendingMediaStoreUri != null) {
            cordova.getActivity().getContentResolver().delete(pendingMediaStoreUri, null, null);
            pendingMediaStoreUri = null;
        }
    }

    private void openDownloadedFile(String filePath, boolean cleanup) {
        File file = new File(filePath);
        String mimeType = getMimeType(filePath);
        Uri uriToOpen;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: publish to MediaStore Downloads — accessible by any app without URI grants.
            // FileProvider URIs require explicit per-app grants which Android 16 does not forward
            // reliably through the system chooser.
            uriToOpen = publishToMediaStore(file, mimeType);
            if (uriToOpen == null) {
                callbackContext.error("Failed to publish file to shared storage");
                return;
            }
            pendingMediaStoreUri = cleanup ? uriToOpen : null;
        } else {
            // Android 9 and below: FileProvider with explicit grants
            uriToOpen = FileProvider.getUriForFile(cordova.getActivity(),
                    cordova.getActivity().getApplicationContext().getPackageName() + ".provider", file);
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uriToOpen, mimeType);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("", uriToOpen));
            List<ResolveInfo> resInfoList = cordova.getActivity().getPackageManager()
                    .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                String pkg = resolveInfo.activityInfo.packageName;
                cordova.getActivity().grantUriPermission(pkg, uriToOpen, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // MediaStore URI — no permission issues, start directly so Android shows
                // "Always / Just once" and lets the user set a default app
                cordova.getActivity().startActivity(intent);
            } else {
                // FileProvider URI — use createChooser so URI grants propagate correctly
                Intent chooser = Intent.createChooser(intent, null);
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                cordova.getActivity().startActivity(chooser);
            }
            callbackContext.success();
        } catch (Exception e) {
            callbackContext.error("Failed to open the file: " + e.getMessage());
        }
    }

    // Publishes the file to MediaStore Downloads (Android 10+ only).
    // Reuses an existing entry if one with the same name and size already exists.
    @SuppressWarnings("NewApi")
    private Uri publishToMediaStore(File file, String mimeType) {
        ContentResolver resolver = cordova.getActivity().getContentResolver();

        // Reuse if already published with the same size
        String[] projection = { MediaStore.Downloads._ID };
        String selection = MediaStore.Downloads.DISPLAY_NAME + "=?"
                + " AND " + MediaStore.Downloads.SIZE + "=?"
                + " AND " + MediaStore.Downloads.IS_PENDING + "=0";
        String[] selArgs = { file.getName(), String.valueOf(file.length()) };
        try (Cursor cursor = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, selArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0));
            }
        } catch (Exception ignored) {}

        // Insert new entry
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, file.getName());
        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TechView");
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri mediaUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (mediaUri == null) return null;

        try (OutputStream os = resolver.openOutputStream(mediaUri);
             InputStream is = new FileInputStream(file)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) != -1) {
                os.write(buf, 0, n);
            }
        } catch (Exception e) {
            resolver.delete(mediaUri, null, null);
            return null;
        }

        values.clear();
        values.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(mediaUri, values, null, null);
        return mediaUri;
    }

    private String getMimeType(String filePath) {
        int dot = filePath.lastIndexOf('.');
        String ext = dot >= 0 ? filePath.substring(dot + 1).toLowerCase() : "";
        switch (ext) {
            case "pdf":  return "application/pdf";
            case "doc":  return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":  return "application/vnd.ms-excel";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "png":  return "image/png";
            default:     return "*/*";
        }
    }

    private void openFile(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            cordova.getActivity().startActivity(intent);
            callbackContext.success();
        } catch (Exception e) {
            callbackContext.error("Failed to open the file: " + e.getMessage());
        }
    }
}
