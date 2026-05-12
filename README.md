# cordova-plugin-android-openfile

A Cordova plugin for opening files on Android. Uses MediaStore on Android 10+ and FileProvider on Android 9 and below. Designed for Android 16 scoped storage compliance.

**Platform:** Android only  
**Minimum SDK:** Android 5.0 (API 21)  
**npm:** https://www.npmjs.com/package/cordova-plugin-android-openfile

---

## Installation

```bash
cordova plugin add cordova-plugin-android-openfile
```

---

## What it does

Provides three methods on `cordova.plugins.openFile`:

| Method | Description |
|--------|-------------|
| `openDownloadedFile(path, [options], success, error)` | Opens a file by its native path |
| `openPdfUsingSAF(success, error)` | Opens the system file picker (SAF) for the user to select a PDF |
| `requestPermissions(success, error)` | Requests storage permissions (Android 9 and below only) |

### How files are opened

| Android version | Mechanism |
|----------------|-----------|
| Android 10+ (API 29+) | File is temporarily copied to `Downloads/TechView/` via MediaStore. MediaStore URIs are accessible by any app — no URI grants required. The copy is deleted when the user returns to your app (configurable). |
| Android 9 and below (API 28-) | File is served via FileProvider using a `content://` URI with explicit URI permission grants. |

The file in `cordova.file.externalCacheDirectory` is always kept as the download cache — the MediaStore copy is only created at open time.

---

## Usage

### Open a downloaded file

The file must already exist on the device. Pass the native file path — no `file://` prefix.

```javascript
var filePath = cordova.file.externalCacheDirectory.replace('file://', '') + 'myfile.pdf';

cordova.plugins.openFile.openDownloadedFile(
    filePath,
    function() {
        console.log('File opened successfully.');
    },
    function(err) {
        console.error('Error opening file:', err);
    }
);
```

### Open with options

An optional `options` object can be passed as the second argument.

```javascript
cordova.plugins.openFile.openDownloadedFile(
    filePath,
    { cleanup: true },   // default — deletes the Downloads copy when user returns to app
    function() { console.log('Opened.'); },
    function(err) { console.error(err); }
);
```

```javascript
cordova.plugins.openFile.openDownloadedFile(
    filePath,
    { cleanup: false },  // keep the file in Downloads/TechView permanently
    function() { console.log('Opened.'); },
    function(err) { console.error(err); }
);
```

#### Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `cleanup` | boolean | `true` | When `true`, the MediaStore copy in `Downloads/TechView/` is deleted when the user returns to the app. When `false`, the file remains in Downloads. Only applies to Android 10+. |

### Open the SAF file picker (user selects a PDF)

```javascript
cordova.plugins.openFile.openPdfUsingSAF(
    function() {
        console.log('PDF opened successfully.');
    },
    function(err) {
        console.error('Error:', err);
    }
);
```

### Request storage permissions (Android 9 and below)

Not required when using `externalCacheDirectory` on Android 10+.

```javascript
cordova.plugins.openFile.requestPermissions(
    function() {
        console.log('Permission granted.');
    },
    function(err) {
        console.error('Permission denied:', err);
    }
);
```

---

## Supported file types

`openDownloadedFile` detects the MIME type from the file extension so Android presents only compatible apps in the chooser.

| Extension | MIME type |
|-----------|-----------|
| `.pdf` | `application/pdf` |
| `.doc` | `application/msword` |
| `.docx` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| `.xls` | `application/vnd.ms-excel` |
| `.xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| `.jpg` / `.jpeg` | `image/jpeg` |
| `.png` | `image/png` |
| other | `*/*` |

---

## FileProvider paths (Android 9 and below)

The plugin registers a `FileProvider` with authority `{applicationId}.provider`:

```xml
<external-path name="external_files" path="." />
<external-cache-path name="external_cache_files" path="." />
<files-path name="internal_files" path="." />
<cache-path name="internal_cache" path="." />
```

Files must be within one of these paths for FileProvider to serve them.

---

## Android permission requirements

| Android version | Permission needed |
|----------------|------------------|
| Android 10+ (API 29+) | None — MediaStore is used |
| Android 9 and below (API 28-) | `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` if accessing shared storage |

---

## License

ISC
