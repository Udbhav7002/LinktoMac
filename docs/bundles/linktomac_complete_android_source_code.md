# LinkToMac — Complete Android Source Code (`allcode/LinkToMac-Android`)

Every file below belongs inside the codebase. Run `python3 scripts/stitch_allcode.py` to extract them automatically.

---

### FILE: allcode/LinkToMac-Android/app/build.gradle.kts
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.linktomac.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.linktomac.android"
        minSdk = 29
        targetSdk = 34
        versionCode = 161
        versionName = "1.61"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // ZXing QR Scanner for 1-tap QR code pairing
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
```

---

### FILE: allcode/LinkToMac-Android/build.gradle.kts
```kotlin
plugins {
    id("com.android.application") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

---

### FILE: allcode/LinkToMac-Android/settings.gradle.kts
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "LinkToMacAndroid"
include(":app")
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/AndroidManifest.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Local Wi-Fi & Foreground Service -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <!-- SMS, Contacts & Call Controls -->
    <uses-permission android:name="android.permission.READ_SMS" />
    <uses-permission android:name="android.permission.SEND_SMS" />
    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.READ_CALL_LOG" />
    <uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
    <uses-permission android:name="android.permission.CALL_PHONE" />

    <!-- Files, Photos, Camera & Microphone -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" tools:ignore="ScopedStorage" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="LinkToMac"
        android:supportsRtl="true"
        android:theme="@style/Theme.LinkToMac">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <!-- Share Sheet Target: "Send to Mac (LinkToMac)" -->
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="*/*" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND_MULTIPLE" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="*/*" />
            </intent-filter>
        </activity>

        <service
            android:name=".LinkConnectionService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="connectedDevice|camera|microphone|mediaProjection" />

        <service
            android:name=".LinkNotificationService"
            android:label="LinkToMac Notification &amp; Media Bridge"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

        <service
            android:name=".LinkTouchAccessibilityService"
            android:label="LinkToMac Wireless Touch &amp; Mirror Bridge"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/link_accessibility_config" />
        </service>
    </application>
</manifest>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/drawable/ic_accessibility.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M12,4.7 l0.01,0"
        android:strokeColor="#FF000000"
        android:strokeWidth="2.4"
        android:strokeLineCap="round" />
    <path
        android:pathData="M4.7,9.4 H19.3 M12,7.6 V15.2 M12,15.2 L8.7,20.3 M12,15.2 L15.3,20.3"
        android:strokeColor="#FF000000"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/drawable/ic_battery.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M10.2,3.2 H13.8 M9.6,5.2 H14.4 C15.62,5.2 16.6,6.18 16.6,7.4 V17.8 C16.6,19.02 15.62,20 14.4,20 H9.6 C8.38,20 7.4,19.02 7.4,17.8 V7.4 C7.4,6.18 8.38,5.2 9.6,5.2 Z"
        android:strokeColor="#FF000000"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/drawable/ic_bell.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M6.2,10.2 C6.2,6.9 8.8,4.3 12,4.3 C15.2,4.3 17.8,6.9 17.8,10.2 V14.2 L19.2,17 H4.8 L6.2,14.2 Z"
        android:strokeColor="#FF000000"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
    <path
        android:pathData="M9.7,17 C9.7,18.2 10.7,19.2 12,19.2 C13.3,19.2 14.3,18.2 14.3,17"
        android:strokeColor="#FF000000"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/drawable/ic_chat.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M7.3,4.5 H16.7 C18.63,4.5 20.2,6.07 20.2,8 V13 C20.2,14.93 18.63,16.5 16.7,16.5 H7.3 C5.37,16.5 3.8,14.93 3.8,13 V8 C3.8,6.07 5.37,4.5 7.3,4.5 Z"
        android:strokeColor="#FF000000"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
    <path
        android:pathData="M9.2,16.5 L7.8,20.2 L12.4,16.9"
        android:strokeColor="#FF000000"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/drawable/ic_folder.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M3.5,18.5 L3.5,7 C3.5,6.17 4.17,5.5 5,5.5 L9.2,5.5 L11.2,7.5 L19,7.5 C19.83,7.5 20.5,8.17 20.5,9 L20.5,18.5 C20.5,19.33 19.83,20 19,20 L5,20 C4.17,20 3.5,19.33 3.5,18.5 Z"
        android:strokeColor="#FF000000"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/drawable/ic_launcher_foreground.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:pivotX="54"
        android:pivotY="54"
        android:rotation="-45">
        <path
            android:pathData="M37.35,50.85 H52.65 A12.15,12.15 0 0 1 52.65,75.15 H37.35 A12.15,12.15 0 0 1 37.35,50.85 Z"
            android:strokeColor="#FFFFFFFF"
            android:strokeWidth="7.5"
            android:strokeLineCap="round"
            android:strokeLineJoin="round" />
        <path
            android:pathData="M55.35,32.85 H70.65 A12.15,12.15 0 0 1 70.65,57.15 H55.35 A12.15,12.15 0 0 1 55.35,32.85 Z"
            android:strokeColor="#FFFFFFFF"
            android:strokeWidth="7.5"
            android:strokeLineCap="round"
            android:strokeLineJoin="round" />
    </group>
</vector>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/drawable/ic_link.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <group
        android:pivotX="12"
        android:pivotY="12"
        android:rotation="-45">
        <path
            android:pathData="M8.3,11.3 H11.7 A2.7,2.7 0 0 1 11.7,16.7 H8.3 A2.7,2.7 0 0 1 8.3,11.3 Z"
            android:strokeColor="#FF000000"
            android:strokeWidth="1.8"
            android:strokeLineCap="round"
            android:strokeLineJoin="round" />
        <path
            android:pathData="M12.3,7.3 H15.7 A2.7,2.7 0 0 1 15.7,12.7 H12.3 A2.7,2.7 0 0 1 12.3,7.3 Z"
            android:strokeColor="#FF000000"
            android:strokeWidth="1.8"
            android:strokeLineCap="round"
            android:strokeLineJoin="round" />
    </group>
</vector>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/drawable/ic_north_east.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M6,18 L18,6 M12.2,6 H18 V11.8"
        android:strokeColor="#FF000000"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/drawable/ic_qr.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M4,4 H10.25 V10.25 H4 Z M13.75,4 H20 V10.25 H13.75 Z M4,13.75 H10.25 V20 H4 Z"
        android:strokeColor="#FF000000"
        android:strokeWidth="1.7"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
    <path
        android:pathData="M14.2,14.2 l0.01,0 M17.1,14.2 l0.01,0 M20,14.2 l0.01,0 M14.2,17.1 l0.01,0 M17.1,17.1 l0.01,0 M14.2,20 l0.01,0 M20,20 l0.01,0"
        android:strokeColor="#FF000000"
        android:strokeWidth="2.4"
        android:strokeLineCap="round" />
</vector>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/drawable/ic_usb.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M10.3,3.8 V7 M13.7,3.8 V7 M12,13 C12,16.4 8.2,15.9 8.2,19.9"
        android:strokeColor="#FF000000"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
    <path
        android:pathData="M10.2,7 H13.8 C14.8,7 15.6,7.8 15.6,8.8 V11.2 C15.6,12.2 14.8,13 13.8,13 H10.2 C9.2,13 8.4,12.2 8.4,11.2 V8.8 C8.4,7.8 9.2,7 10.2,7 Z"
        android:strokeColor="#FF000000"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/drawable/ic_wifi.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M3.8,9.4 A11.6,11.6 0 0 1 20.2,9.4 M6.27,11.87 A8.1,8.1 0 0 1 17.73,11.87 M8.89,14.49 A4.4,4.4 0 0 1 15.11,14.49"
        android:strokeColor="#FF000000"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
    <path
        android:pathData="M12,17.6 l0.01,0"
        android:strokeColor="#FF000000"
        android:strokeWidth="2.4"
        android:strokeLineCap="round" />
</vector>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/values/colors.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#18181B</color>
</resources>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/values/strings.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">LinkToMac</string>
    <string name="accessibility_service_desc">Enables remote wireless touch input, navigation gestures, and screen capture streaming from your Mac.</string>
</resources>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/values/themes.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.LinkToMac" parent="@android:style/Theme.DeviceDefault.DayNight">
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowActionBar">false</item>
    </style>
</resources>
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/xml/accessibility_service_config.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_desc"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="50"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:canTakeScreenshot="true" />
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/xml/link_accessibility_config.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_desc"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:canPerformGestures="true"
    android:canTakeScreenshot="true"
    android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagReportViewIds|flagRetrieveInteractiveWindows" />
```

---

### FILE: allcode/LinkToMac-Android/gradle.properties
```properties
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.builtInKotlin=false
android.newDsl=false
kotlin.compiler.execution.strategy=in-process
```

---

### FILE: allcode/LinkToMac-Android/gradle/wrapper/gradle-wrapper.properties
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
networkTimeout=10000
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/FilePhotosBridge.kt
```kotlin
package com.linktomac.android

import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Base64
import android.util.Size
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID

object FilePhotosBridge {

    fun sendDirectoryListing(path: String) {
        val dir = File(path)
        val arr = JSONArray()
        dir.listFiles()?.forEach { f ->
            arr.put(JSONObject().apply {
                put("name", f.name)
                put("path", f.absolutePath)
                put("sizeBytes", if (f.isDirectory) 0L else f.length())
                put("isDirectory", f.isDirectory)
                put("lastModified", f.lastModified())
            })
        }
        val frame = WireProtocol.packJson(
            type = "FILE_LIST_RES",
            payloadMap = mapOf("path" to path)
        ) {
            put("files", arr)
        }
        LinkConnectionService.instance?.sendRawFrame(frame)
    }

    fun sendPhotosList(context: Context) {
        val arr = JSONArray()
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.SIZE
            )
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, null,
                MediaStore.Images.Media.DATE_TAKEN + " DESC"
            )?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count < 40) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1) ?: "photo.jpg"
                    val path = cursor.getString(2) ?: ""
                    val date = cursor.getLong(3)
                    val size = cursor.getLong(4)

                    var thumbB64 = ""
                    try {
                        val contentUri = android.content.ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                        )
                        val bmp = context.contentResolver.loadThumbnail(contentUri, Size(180, 180), null)
                        val baos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 65, baos)
                        thumbB64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    } catch (_: Exception) {}

                    arr.put(JSONObject().apply {
                        put("id", id.toString())
                        put("name", name)
                        put("path", path)
                        put("dateTaken", date)
                        put("sizeBytes", size)
                        put("isVideo", false)
                        put("thumbnailBase64", thumbB64)
                    })
                    count++
                }
            }
        } catch (_: Exception) {}

        val frame = WireProtocol.packJson("PHOTOS_LIST_RES") {
            put("photos", arr)
        }
        LinkConnectionService.instance?.sendRawFrame(frame)
    }

    fun streamFileToMac(path: String, fileName: String, revealInFinder: String = "true") {
        val file = File(path)
        if (!file.exists() || !file.isFile) return
        val tid = UUID.randomUUID().toString() // 36 chars

        val startFrame = WireProtocol.packJson(
            type = "FILE_TRANSFER_START",
            payloadMap = mapOf(
                "transferId" to tid,
                "fileName" to fileName,
                "totalBytes" to file.length().toString()
            )
        )
        LinkConnectionService.instance?.sendRawFrame(startFrame)

        val tidBytes = tid.padEnd(36, ' ').toByteArray(Charsets.UTF_8)
        FileInputStream(file).use { fis ->
            val buf = ByteArray(65536)
            while (true) {
                val read = fis.read(buf)
                if (read <= 0) break
                val chunkPayload = ByteArray(36 + read)
                System.arraycopy(tidBytes, 0, chunkPayload, 0, 36)
                System.arraycopy(buf, 0, chunkPayload, 36, read)
                val packed = WireProtocol.packFrame(WireProtocol.FRAME_FILE_CHUNK, chunkPayload)
                LinkConnectionService.instance?.sendRawFrame(packed)
                Thread.sleep(2)
            }
        }

        val endFrame = WireProtocol.packJson(
            type = "FILE_TRANSFER_END",
            payloadMap = mapOf(
                "transferId" to tid,
                "fileName" to fileName,
                "revealInFinder" to revealInFinder
            )
        )
        LinkConnectionService.instance?.sendRawFrame(endFrame)
    }

    fun streamUriToMac(context: Context, uri: android.net.Uri, fileName: String, revealInFinder: String = "true") {
        var size = -1L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}
        if (size <= 0) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    size = pfd.statSize
                }
            } catch (_: Exception) {}
        }
        if (size < 0) size = 0L

        val tid = UUID.randomUUID().toString()
        val startFrame = WireProtocol.packJson(
            type = "FILE_TRANSFER_START",
            payloadMap = mapOf(
                "transferId" to tid,
                "fileName" to fileName,
                "totalBytes" to size.toString()
            )
        )
        LinkConnectionService.instance?.sendRawFrame(startFrame)

        val tidBytes = tid.padEnd(36, ' ').toByteArray(Charsets.UTF_8)
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buf = ByteArray(65536)
                while (true) {
                    val read = inputStream.read(buf)
                    if (read <= 0) break
                    val chunkPayload = ByteArray(36 + read)
                    System.arraycopy(tidBytes, 0, chunkPayload, 0, 36)
                    System.arraycopy(buf, 0, chunkPayload, 36, read)
                    val packed = WireProtocol.packFrame(WireProtocol.FRAME_FILE_CHUNK, chunkPayload)
                    LinkConnectionService.instance?.sendRawFrame(packed)
                    Thread.sleep(2)
                }
            }
        } catch (_: Exception) {}

        val endFrame = WireProtocol.packJson(
            type = "FILE_TRANSFER_END",
            payloadMap = mapOf(
                "transferId" to tid,
                "fileName" to fileName,
                "revealInFinder" to revealInFinder
            )
        )
        LinkConnectionService.instance?.sendRawFrame(endFrame)
    }
}
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/LinkConnectionService.kt
```kotlin
package com.linktomac.android

import android.app.*
import android.content.*
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class LinkConnectionService : Service() {

    companion object {
        var instance: LinkConnectionService? = null
        var isConnectedState: Boolean = false
        var statusMessage: String = "Disconnected"
        var discoveredMacHost: String = ""
        var discoveredMacPort: Int = 54921
        var connectedMacName: String = ""
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val incomingFiles = mutableMapOf<String, FileOutputStream>()
    private var reconnectJob: Job? = null
    private var userRequestedDisconnect: Boolean = false
    private var activeRingtone: Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundNotification("LinkToMac Ready — Waiting for Mac")
        startMdnsDiscovery()
        ClipboardManagerBridge.init(this)
        SmsContactsCallBridge.registerCallStateReceiver(this)

        // Automatically connect to remembered device on startup
        connectedMacName = getSharedPreferences("linktomac", MODE_PRIVATE).getString("lastMacName", "") ?: ""
        autoConnectRemembered()
    }

    private fun autoConnectRemembered() {
        val prefs = getSharedPreferences("linktomac", MODE_PRIVATE)
        val savedHost = prefs.getString("lastHost", null)
        val savedPort = prefs.getInt("lastPort", 54921)
        val savedToken = prefs.getString("lastToken", "") ?: ""
        if (!savedHost.isNullOrBlank()) {
            connectToMac(savedHost, savedPort, savedToken)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "CONNECT_HOST") {
            userRequestedDisconnect = false
            reconnectJob?.cancel()
            val host = intent.getStringExtra("host") ?: "127.0.0.1"
            val port = intent.getIntExtra("port", 54921)
            val token = intent.getStringExtra("token") ?: ""
            val macName = intent.getStringExtra("macName") ?: ""
            if (macName.isNotBlank()) {
                connectedMacName = macName
                getSharedPreferences("linktomac", MODE_PRIVATE).edit()
                    .putString("lastMacName", macName)
                    .apply()
            }
            connectToMac(host, port, token)
        } else if (action == "DISCONNECT") {
            userRequestedDisconnect = true
            reconnectJob?.cancel()
            closeSocket()
        }
        return START_STICKY
    }

    fun connectToMac(host: String, port: Int, token: String) {
        userRequestedDisconnect = false
        reconnectJob?.cancel()
        serviceScope.launch {
            closeSocketInternal()
            try {
                statusMessage = "Connecting to $host:$port..."
                val s = Socket()
                s.tcpNoDelay = true
                s.keepAlive = true
                s.connect(InetSocketAddress(host, port), 4000)
                socket = s
                outputStream = s.getOutputStream()
                isConnectedState = true
                val transport = if (host == "127.0.0.1") "USB" else "Local Wi-Fi"
                statusMessage = "Connected ($transport • $host)"
                startForegroundNotification("Connected to Mac ($transport)")

                // Save last successful host
                getSharedPreferences("linktomac", MODE_PRIVATE).edit()
                    .putString("lastHost", host)
                    .putInt("lastPort", port)
                    .putString("lastToken", token)
                    .apply()

                sendHelloAndStatus(transport, token)
                startStatusHeartbeat(transport)
                runAutoSync()
                readLoop(DataInputStream(s.getInputStream()))
            } catch (e: Exception) {
                isConnectedState = false
                statusMessage = "Connection failed: ${e.localizedMessage}"
                if (!userRequestedDisconnect) {
                    scheduleReconnect()
                }
            }
        }
    }

    // Push the current clipboard and a fresh SMS/contacts snapshot on every
    // successful connection so the Mac is always up to date without manual taps.
    private fun runAutoSync() {
        ClipboardManagerBridge.sendCurrentClipboardToMac(this)
        serviceScope.launch {
            try {
                SmsContactsCallBridge.syncSmsAndContactsToMac(this@LinkConnectionService)
            } catch (_: Exception) {}
        }
    }

    fun sendRawFrame(frameBytes: ByteArray) {
        serviceScope.launch {
            try {
                synchronized(this@LinkConnectionService) {
                    outputStream?.write(frameBytes)
                    outputStream?.flush()
                }
            } catch (_: Exception) {
                closeSocket()
            }
        }
    }

    private fun sendHelloAndStatus(transport: String, token: String) {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        val model = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

        val bytes = WireProtocol.packJson(
            type = "HELLO",
            payloadMap = mapOf(
                "deviceName" to model,
                "battery" to battery.toString(),
                "charging" to charging.toString(),
                "transport" to transport,
                "pairToken" to token,
                "mediaTitle" to LinkNotificationService.currentMediaTitle,
                "mediaArtist" to LinkNotificationService.currentMediaArtist,
                "screenWidth" to resources.displayMetrics.widthPixels.toString(),
                "screenHeight" to resources.displayMetrics.heightPixels.toString()
            )
        )
        sendRawFrame(bytes)
    }

    private fun startStatusHeartbeat(transport: String) {
        serviceScope.launch {
            while (isActive && isConnectedState) {
                delay(8000)
                val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val charging = bm.isCharging
                val bytes = WireProtocol.packJson(
                    type = "STATUS_UPDATE",
                    payloadMap = mapOf(
                        "deviceName" to "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                        "battery" to battery.toString(),
                        "charging" to charging.toString(),
                        "transport" to transport,
                        "mediaTitle" to LinkNotificationService.currentMediaTitle,
                        "mediaArtist" to LinkNotificationService.currentMediaArtist,
                        "screenWidth" to resources.displayMetrics.widthPixels.toString(),
                        "screenHeight" to resources.displayMetrics.heightPixels.toString()
                    )
                )
                sendRawFrame(bytes)
            }
        }
    }

    private fun readLoop(input: DataInputStream) {
        try {
            while (isConnectedState) {
                val length = input.readInt()
                val type = input.readByte()
                if (length <= 0 || length > 30_000_000) continue
                val payload = ByteArray(length)
                input.readFully(payload)

                when (type) {
                    WireProtocol.FRAME_JSON -> handleIncomingJson(String(payload, Charsets.UTF_8))
                    WireProtocol.FRAME_FILE_CHUNK -> handleIncomingFileChunk(payload)
                }
            }
        } catch (_: Exception) {
            closeSocket()
        }
    }

    private fun handleIncomingJson(jsonStr: String) {
        val obj = JSONObject(jsonStr)
        val type = obj.optString("type")
        val payload = obj.optJSONObject("payload") ?: JSONObject()

        when (type) {
            "NOTIFICATION_REPLY" -> {
                val id = payload.optString("id")
                val replyText = payload.optString("replyText")
                LinkNotificationService.performInlineReply(this, id, replyText)
            }
            "NOTIFICATION_REMOVED" -> {
                val id = payload.optString("id")
                LinkNotificationService.dismissNotification(id)
            }
            "SMS_THREADS_REQ" -> {
                SmsContactsCallBridge.syncSmsAndContactsToMac(this)
            }
            "SMS_SEND" -> {
                val addr = payload.optString("address")
                val body = payload.optString("body")
                SmsContactsCallBridge.sendSms(this, addr, body)
            }
            "CALL_ACTION" -> {
                val action = payload.optString("action")
                val number = payload.optString("number")
                val smsReply = payload.optString("smsReply")
                SmsContactsCallBridge.handleCallAction(this, action, number, smsReply)
            }
            "CLIPBOARD_SYNC" -> {
                val content = payload.optString("content")
                ClipboardManagerBridge.applyFromMac(this, content)
            }
            "URL_HANDOFF" -> {
                val url = payload.optString("url")
                if (url.isNotEmpty()) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(browserIntent)
                }
            }
            "FILE_LIST_REQ" -> {
                val path = payload.optString("path", "/sdcard/Download")
                FilePhotosBridge.sendDirectoryListing(path)
            }
            "PHOTOS_LIST_REQ" -> {
                FilePhotosBridge.sendPhotosList(this)
            }
            "FILE_PULL_REQ" -> {
                val path = payload.optString("path")
                val fileName = payload.optString("fileName")
                val reveal = payload.optString("revealInFinder", "true")
                FilePhotosBridge.streamFileToMac(path, fileName, reveal)
            }
            "FILE_TRANSFER_START" -> {
                val tid = payload.optString("transferId")
                val fileName = payload.optString("fileName")
                val destFolder = payload.optString("destFolder", "/sdcard/Download/LinkToMac")
                val dir = File(destFolder)
                if (!dir.exists()) dir.mkdirs()
                val outFile = File(dir, fileName)
                incomingFiles[tid] = FileOutputStream(outFile)
            }
            "FILE_TRANSFER_END" -> {
                val tid = payload.optString("transferId")
                incomingFiles.remove(tid)?.close()
            }
            "MEDIA_ACTION" -> {
                val action = payload.optString("action")
                handleMediaAction(action)
            }
            "STREAM_CONTROL" -> {
                ScreenCameraStreamer.handleStreamControl(this, payload)
            }
        }
    }

    private fun handleIncomingFileChunk(payload: ByteArray) {
        if (payload.size <= 36) return
        val tid = String(payload, 0, 36, Charsets.UTF_8).trim()
        val fos = incomingFiles[tid] ?: return
        fos.write(payload, 36, payload.size - 36)
    }

    private fun handleMediaAction(action: String) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        when (action) {
            "PLAY_PAUSE" -> dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "NEXT"       -> dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            "PREV"       -> dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "VOL_UP"     -> audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "VOL_DOWN"   -> audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "RING_PHONE" -> {
                val current = activeRingtone
                if (current != null && current.isPlaying) {
                    current.stop()
                    activeRingtone = null
                } else {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
                    activeRingtone = ringtone
                    ringtone?.play()
                }
            }
            "STOP_RING", "STOP_RINGING" -> {
                activeRingtone?.stop()
                activeRingtone = null
            }
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
    }

    // MARK: - mDNS Automatic Discovery (_linktomac._tcp)
    private fun startMdnsDiscovery() {
        try {
            nsdManager = getSystemService(NSD_SERVICE) as NsdManager
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceType.contains("_linktomac._tcp")) {
                        nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                val host = resolved.host?.hostAddress ?: return
                                discoveredMacHost = host
                                discoveredMacPort = resolved.port
                                if (!isConnectedState && !userRequestedDisconnect) {
                                    val prefs = getSharedPreferences("linktomac", MODE_PRIVATE)
                                    val token = prefs.getString("lastToken", "") ?: ""
                                    connectToMac(host, resolved.port, token)
                                }
                            }
                        })
                    }
                }
                override fun onServiceLost(service: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            discoveryListener = listener
            nsdManager?.discoverServices("_linktomac._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {}
    }

    private fun closeSocketInternal() {
        isConnectedState = false
        try { outputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        outputStream = null
        socket = null
    }

    private fun closeSocket() {
        closeSocketInternal()
        statusMessage = "Disconnected"
        if (!userRequestedDisconnect) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true || userRequestedDisconnect) return
        reconnectJob = serviceScope.launch {
            var delayMs = 1500L
            while (isActive && !isConnectedState && !userRequestedDisconnect) {
                delay(delayMs)
                if (isConnectedState || userRequestedDisconnect) break

                val prefs = getSharedPreferences("linktomac", MODE_PRIVATE)
                val targetHost = if (discoveredMacHost.isNotEmpty()) discoveredMacHost else prefs.getString("lastHost", null)
                val targetPort = if (discoveredMacHost.isNotEmpty()) discoveredMacPort else prefs.getInt("lastPort", 54921)
                val targetToken = prefs.getString("lastToken", "") ?: ""

                if (!targetHost.isNullOrBlank()) {
                    statusMessage = "Reconnecting to $targetHost:$targetPort..."
                    try {
                        val s = Socket()
                        s.tcpNoDelay = true
                        s.keepAlive = true
                        s.connect(InetSocketAddress(targetHost, targetPort), 4000)
                        socket = s
                        outputStream = s.getOutputStream()
                        isConnectedState = true
                        val transport = if (targetHost == "127.0.0.1") "USB" else "Local Wi-Fi"
                        statusMessage = "Connected ($transport • $targetHost)"
                        startForegroundNotification("Connected to Mac ($transport)")

                        sendHelloAndStatus(transport, targetToken)
                        startStatusHeartbeat(transport)
                        runAutoSync()
                        readLoop(DataInputStream(s.getInputStream()))
                        break
                    } catch (_: Exception) {
                        isConnectedState = false
                        statusMessage = "Waiting for Mac ($targetHost)..."
                        delayMs = (delayMs * 1.5).toLong().coerceAtMost(10000L)
                    }
                } else {
                    delay(3000L)
                }
            }
        }
    }

    private fun startForegroundNotification(text: String) {
        val channelId = "linktomac_channel"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val chan = NotificationChannel(channelId, "LinkToMac Connection", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(chan)

        val notif = Notification.Builder(this, channelId)
            .setContentTitle("LinkToMac Companion")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1001, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1001, notif)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeRingtone?.stop()
        activeRingtone = null
        reconnectJob?.cancel()
        closeSocketInternal()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

// MARK: - Clipboard Bridge Helper
object ClipboardManagerBridge {
    private var lastClipboardText = ""

    fun init(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener {
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim() ?: return@addPrimaryClipChangedListener
            if (text.isNotEmpty() && text != lastClipboardText) {
                lastClipboardText = text
                sendClipboardToMac(text)
            }
        }
    }

    fun sendCurrentClipboardToMac(context: Context) {
        Handler(Looper.getMainLooper()).post {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim() ?: return@post
                if (text.isNotEmpty() && text != lastClipboardText) {
                    lastClipboardText = text
                    sendClipboardToMac(text)
                }
            } catch (_: Exception) {}
        }
    }

    fun syncFromAccessibilityService(context: Context) {
        sendCurrentClipboardToMac(context)
    }

    fun sendClipboardToMac(text: String) {
        val isUrl = text.startsWith("http://") || text.startsWith("https://")
        val bytes = WireProtocol.packJson(
            type = "CLIPBOARD_SYNC",
            payloadMap = mapOf("content" to text, "isUrl" to isUrl.toString())
        )
        LinkConnectionService.instance?.sendRawFrame(bytes)
    }

    fun applyFromMac(context: Context, text: String) {
        if (text == lastClipboardText) return
        lastClipboardText = text
        Handler(Looper.getMainLooper()).post {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("LinkToMac", text))
        }
    }
}
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/LinkNotificationService.kt
```kotlin
package com.linktomac.android

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class LinkNotificationService : NotificationListenerService() {

    private data class ReplyActionHolder(
        val pendingIntent: PendingIntent,
        val remoteInputs: Array<RemoteInput>
    )

    companion object {
        private var instance: LinkNotificationService? = null
        private val replyActions = ConcurrentHashMap<String, ReplyActionHolder>()
        var currentMediaTitle: String = "Nothing Playing"
        var currentMediaArtist: String = "Android Media"

        fun performInlineReply(context: Context, key: String, replyText: String) {
            val holder = replyActions[key] ?: return
            val intent = Intent()
            val bundle = Bundle()
            for (remoteInput in holder.remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(holder.remoteInputs, intent, bundle)
            try {
                holder.pendingIntent.send(context, 0, intent)
            } catch (_: Exception) {}
        }

        fun dismissNotification(key: String) {
            try {
                instance?.cancelNotification(key)
            } catch (_: Exception) {}
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        updateActiveMediaSession()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        guardSbn(sbn) ?: return
        updateActiveMediaSession()

        val notif = sbn!!.notification
        if ((notif.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if (sbn.packageName == packageName || sbn.packageName == "com.android.systemui") return

        val extras = notif.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isEmpty() && body.isEmpty()) return

        // Check for inline RemoteInput reply actions
        var canReply = false
        notif.actions?.forEach { action ->
            val inputs = action.remoteInputs
            if (!inputs.isNullOrEmpty() && action.actionIntent != null) {
                canReply = true
                replyActions[sbn.key] = ReplyActionHolder(action.actionIntent, inputs)
            }
        }

        // Detect 4-8 digit 2FA / OTP codes automatically
        val otpRegex = Regex("\\b(\\d{4,8})\\b")
        val otpMatch = otpRegex.find(body)?.value ?: ""

        val pm = applicationContext.packageManager
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        val itemJson = JSONObject().apply {
            put("id", sbn.key)
            put("packageName", sbn.packageName)
            put("appName", appName)
            put("title", title)
            put("body", body)
            put("timestamp", sbn.postTime)
            put("canReply", canReply)
            put("otpCode", otpMatch)
        }

        val arr = JSONArray().apply { put(itemJson) }
        val bytes = WireProtocol.packJson("NOTIFICATION_POSTED") {
            put("notifications", arr)
        }
        LinkConnectionService.instance?.sendRawFrame(bytes)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        replyActions.remove(key)
        val bytes = WireProtocol.packJson(
            type = "NOTIFICATION_REMOVED",
            payloadMap = mapOf("id" to key)
        )
        LinkConnectionService.instance?.sendRawFrame(bytes)
    }

    private fun updateActiveMediaSession() {
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = msm.getActiveSessions(
                android.content.ComponentName(this, LinkNotificationService::class.java)
            )
            val first = controllers.firstOrNull()
            val meta = first?.metadata
            if (meta != null) {
                currentMediaTitle = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Playing Media"
                currentMediaArtist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            }
        } catch (_: Exception) {}
    }

    private fun guardSbn(sbn: StatusBarNotification?): StatusBarNotification? = sbn
}
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/LinkTheme.kt
```kotlin
package com.linktomac.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// LinkToMac Design Tokens — "Quiet Premium" (mirrors LinkTheme.swift / web CSS).
// Matte surfaces, hairline borders, one blue accent, semantic status colors only.
private val BlueLight  = Color(0xFF0071E3)
private val BlueDark   = Color(0xFF0A84FF)
private val GreenLight = Color(0xFF34C759)
private val GreenDark  = Color(0xFF30D158)
private val AmberLight = Color(0xFFFF9500)
private val AmberDark  = Color(0xFFFF9F0A)
private val RedLight   = Color(0xFFFF3B30)
private val RedDark    = Color(0xFFFF453A)

// Fully-specified schemes — unset M3 roles fall back to stock purple, which is
// exactly how tonal buttons drift away from the brand. Nothing left to defaults.
private val LightScheme = lightColorScheme(
    primary = BlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0x1A0071E3),
    onPrimaryContainer = BlueLight,
    secondary = Color(0xFF6E6E73),
    onSecondary = Color.White,
    secondaryContainer = Color(0x1A0071E3),
    onSecondaryContainer = BlueLight,
    tertiary = Color(0xFF6E6E73),
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF1D1D1F),
    surface = Color.White,
    onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6E6E73),
    surfaceTint = BlueLight,
    inverseSurface = Color(0xFF1D1D1F),
    inverseOnSurface = Color(0xFFF5F5F7),
    outline = Color(0xFFD1D1D6),
    outlineVariant = Color(0x14000000),
    error = RedLight,
    onError = Color.White,
    errorContainer = Color(0x1AFF3B30),
    onErrorContainer = RedLight
)

private val DarkScheme = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0x240A84FF),
    onPrimaryContainer = BlueDark,
    secondary = Color(0xFF98989D),
    onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0x240A84FF),
    onSecondaryContainer = BlueDark,
    tertiary = Color(0xFF98989D),
    background = Color(0xFF1C1C1E),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF2C2C2E),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF38383A),
    onSurfaceVariant = Color(0xFF98989D),
    surfaceTint = BlueDark,
    inverseSurface = Color(0xFFF5F5F7),
    inverseOnSurface = Color(0xFF1C1C1E),
    outline = Color(0xFF48484A),
    outlineVariant = Color(0x1AFFFFFF),
    error = RedDark,
    onError = Color.White,
    errorContainer = Color(0x24FF453A),
    onErrorContainer = RedDark
)

// Semantic helpers resolved at composition time.
data class LinkStatusColors(
    val accent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val dangerQuiet: Color,
    val textTertiary: Color,
    val hairline: Color,
    val surfaceAlt: Color
)

@Composable
fun linkStatusColors(): LinkStatusColors {
    val dark = isSystemInDarkTheme()
    return LinkStatusColors(
        accent = MaterialTheme.colorScheme.primary,
        success = if (dark) GreenDark else GreenLight,
        warning = if (dark) AmberDark else AmberLight,
        danger = if (dark) RedDark else RedLight,
        dangerQuiet = if (dark) Color(0x24FF453A) else Color(0x1AFF3B30),
        textTertiary = if (dark) Color(0xFF636366) else Color(0xFFAEAEB2),
        hairline = if (dark) Color(0x1AFFFFFF) else Color(0x14000000),
        surfaceAlt = MaterialTheme.colorScheme.surfaceVariant
    )
}

// Type ramp (system font) — SF-Pro-equivalent hierarchy on Roboto.
private val LinkTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

// Control / card / overlay radii — applied to M3 components so stock shapes never leak.
private val LinkShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun LinkToMacTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = LinkTypography,
        shapes = LinkShapes,
        content = content
    )
}

// Spacing (4dp grid) and radii — single source of truth for Compose UI.
object LS {
    val xs = 4.dp; val s = 8.dp; val m = 12.dp
    val l = 16.dp; val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp
}

object LR {
    val control = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(12.dp)
    val overlay = RoundedCornerShape(16.dp)
    val badge = RoundedCornerShape(6.dp)
    val pill = RoundedCornerShape(percent = 50)
}

// Type ramp (sp) — weights come from the composables; use these instead of ad-hoc values.
object LT {
    val hero = 20.sp
    val section = 16.sp
    val body = 14.sp
    val caption = 12.sp
}
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/LinkTouchAccessibilityService.kt
```kotlin
package com.linktomac.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.io.ByteArrayOutputStream

class LinkTouchAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "LinkTouchA11y"

        @Volatile
        var instance: LinkTouchAccessibilityService? = null

        fun getRealScreenSize(context: android.content.Context): Pair<Int, Int> {
            val metrics = context.resources.displayMetrics
            return Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            serviceInfo = info
        } catch (_: Exception) {}
        Log.i(TAG, "LinkTouchAccessibilityService connected")
    }

    fun performNormalizedTap(normX: Float, normY: Float): Boolean {
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val px = (normX * width).coerceIn(0f, width.toFloat())
        val py = (normY * height).coerceIn(0f, height.toFloat())

        val path = Path().apply {
            moveTo(px, py)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun performRemoteKey(keyCode: Int): Boolean {
        return when (keyCode) {
            3 -> performGlobalAction(GLOBAL_ACTION_HOME)
            4 -> performGlobalAction(GLOBAL_ACTION_BACK)
            187 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            26 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } else false
            }
            83 -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            else -> false
        }
    }

    fun captureScreenJpeg(callback: (ByteArray?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(null)
            return
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val hwBuffer = result.hardwareBuffer
                    val colorSpace = result.colorSpace
                    try {
                        val hwBmp = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                        if (hwBmp == null) {
                            callback(null)
                            return
                        }
                        val origW = hwBmp.width
                        val origH = hwBmp.height
                        val targetW = 540
                        val targetH = ((origH.toFloat() / origW.toFloat()) * targetW).toInt().coerceAtLeast(1)

                        val scaled = Bitmap.createScaledBitmap(hwBmp, targetW, targetH, true)
                        val baos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                        scaled.recycle()
                        hwBmp.recycle()
                        callback(baos.toByteArray())
                    } catch (e: Exception) {
                        Log.w(TAG, "captureScreenJpeg processing failed: ${e.message}")
                        callback(null)
                    } finally {
                        hwBuffer.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "takeScreenshot failed with code: $errorCode")
                    callback(null)
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "captureScreenJpeg invocation failed: ${e.message}")
            callback(null)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                ClipboardManagerBridge.syncFromAccessibilityService(this)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "LinkTouchAccessibilityService interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "LinkTouchAccessibilityService destroyed")
    }
}
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/MainActivity.kt
```kotlin
package com.linktomac.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start foreground companion service immediately (unchanged)
        startForegroundService(Intent(this, LinkConnectionService::class.java))

        handleIncomingShareIntent(intent)

        enableEdgeToEdge()

        setContent {
            LinkToMacTheme {
                LinkToMacAndroidScreen()
            }
        }
    }

    // ---------------------------------------------------------------- UI

    @Composable
    fun LinkToMacAndroidScreen() {
        val prefs = getSharedPreferences("linktomac", MODE_PRIVATE)
        var tokenInput by remember { mutableStateOf(prefs.getString("lastToken", "") ?: "") }
        var statusText by remember { mutableStateOf(LinkConnectionService.statusMessage) }
        var discoveredHost by remember { mutableStateOf(LinkConnectionService.discoveredMacHost) }
        var permissionTick by remember { mutableStateOf(0) }

        // Existing 1s status poll — now also refreshes permission checklist states
        LaunchedEffect(Unit) {
            while (true) {
                statusText = LinkConnectionService.statusMessage
                discoveredHost = LinkConnectionService.discoveredMacHost
                permissionTick++
                delay(1000)
            }
        }

        // Runtime permissions launcher (unchanged bundle)
        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> permissionTick++ }

        // QR scanner launcher (unchanged parsing)
        val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
            val contents = result.contents ?: return@rememberLauncherForActivityResult
            try {
                val json = JSONObject(contents)
                val h = json.optString("host")
                val p = json.optInt("port", 54921)
                val t = json.optString("token")
                val n = json.optString("macName")
                if (h.isNotEmpty()) {
                    tokenInput = t
                    triggerConnect(h, p, t, n)
                }
            } catch (_: Exception) {}
        }

        val perms = remember(permissionTick) { permissionStates() }
        val connected = LinkConnectionService.isConnectedState

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LS.xxl),
            verticalArrangement = Arrangement.spacedBy(LS.l)
        ) {
            Spacer(Modifier.height(LS.xl))

            BrandHeader()

            StatusCard(
                statusText = statusText,
                discoveredHost = discoveredHost,
                connected = connected,
                onAutoConnect = {
                    triggerConnect(discoveredHost, LinkConnectionService.discoveredMacPort, tokenInput)
                },
                onDisconnect = {
                    startService(Intent(this@MainActivity, LinkConnectionService::class.java).apply {
                        action = "DISCONNECT"
                    })
                }
            )

            if (!connected) {
                PairingCard(
                    tokenInput = tokenInput,
                    onScanQR = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Scan the LinkToMac QR Code on your Mac")
                            setBeepEnabled(false)
                            setOrientationLocked(true)
                        }
                        qrLauncher.launch(options)
                    },
                    onConnectUSB = { triggerConnect("127.0.0.1", 54921, tokenInput) }
                )
            }

            // ---- Permissions checklist (only what is still missing)
            if (!perms.allGranted) {
                PermissionsCard(
                    states = perms,
                    onRequestRuntime = { permLauncher.launch(runtimePermissionBundle()) },
                    onOpenNotificationSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onOpenStorageSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                android.net.Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        }
                    },
                    onOpenBatterySettings = {
                        val pm = getSystemService(POWER_SERVICE) as PowerManager
                        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                android.net.Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        }
                    },
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }

            Spacer(Modifier.height(LS.xxl))
        }
    }

    // ---------------------------------------------------- composable pieces

    @Composable
    private fun BrandHeader() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LS.m)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.primary, LR.control)
                    .padding(5.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_link),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column {
                Text(
                    "LinkToMac",
                    fontSize = LT.hero,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Local & private",
                    fontSize = LT.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun AppCard(content: @Composable ColumnScope.() -> Unit) {
        val status = linkStatusColors()
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = LR.card,
            border = BorderStroke(1.dp, status.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(LS.l),
                verticalArrangement = Arrangement.spacedBy(LS.m),
                content = content
            )
        }
    }

    @Composable
    private fun StatusDot(connected: Boolean) {
        val status = linkStatusColors()
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (connected) status.success else status.warning,
                    CircleShape
                )
        )
    }

    @Composable
    private fun QuietDangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
        val status = linkStatusColors()
        FilledTonalButton(
            onClick = onClick,
            shape = LR.control,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = status.dangerQuiet,
                contentColor = status.danger
            ),
            modifier = modifier
        ) {
            Text(text)
        }
    }

    private fun friendlyStatus(raw: String): String = when {
        raw.startsWith("Connection failed") || raw.contains("ECONNREFUSED") ->
            "Couldn't reach your Mac. Keep LinkToMac open on the Mac, then try USB or QR again."
        raw.startsWith("Connected") ->
            if (raw.contains("USB")) "Connected over USB" else "Connected over Wi-Fi"
        raw.startsWith("Connecting to") -> "Connecting to your Mac…"
        raw.startsWith("Reconnecting to") -> "Reconnecting…"
        raw.startsWith("Waiting for Mac") -> "Waiting for your Mac…"
        raw == "Disconnected" -> "Not connected"
        else -> raw
    }

    @Composable
    private fun StatusCard(
        statusText: String,
        discoveredHost: String,
        connected: Boolean,
        onAutoConnect: () -> Unit,
        onDisconnect: () -> Unit
    ) {
        AppCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LS.s)
            ) {
                StatusDot(connected)
                Text(
                    if (connected) {
                        val macName = LinkConnectionService.connectedMacName
                        if (macName.isNotBlank()) "Connected to $macName" else "Connected to Mac"
                    } else {
                        "Not connected"
                    },
                    fontSize = LT.section,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                friendlyStatus(statusText),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (discoveredHost.isNotEmpty() && !connected) {
                FilledTonalButton(onClick = onAutoConnect, shape = LR.control, modifier = Modifier.fillMaxWidth()) {
                    Text("Connect to discovered Mac")
                }
            }
            if (connected) {
                QuietDangerButton("Disconnect", onClick = onDisconnect, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    @Composable
    private fun PairingCard(
        tokenInput: String,
        onScanQR: () -> Unit,
        onConnectUSB: () -> Unit
    ) {
        AppCard {
            Button(onClick = onScanQR, shape = LR.control, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(LS.s))
                Text("Scan Mac QR code")
            }
            FilledTonalButton(onClick = onConnectUSB, shape = LR.control, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(R.drawable.ic_usb),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(LS.s))
                Text("Connect via USB")
            }
        }
    }

    @Composable
    private fun HairlineDivider() {
        val status = linkStatusColors()
        HorizontalDivider(color = status.hairline)
    }

    @Composable
    private fun PermissionTile(iconRes: Int) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, LR.control)
                .padding(7.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    @Composable
    private fun PermissionsCard(
        states: PermissionStates,
        onRequestRuntime: () -> Unit,
        onOpenNotificationSettings: () -> Unit,
        onOpenStorageSettings: () -> Unit,
        onOpenBatterySettings: () -> Unit,
        onOpenAccessibilitySettings: () -> Unit
    ) {
        data class Row(
            val icon: Int,
            val title: String,
            val subtitle: String,
            val granted: Boolean,
            val onFix: () -> Unit
        )

        val missing = listOf(
            Row(
                icon = R.drawable.ic_chat,
                title = "Messages, calls & camera",
                subtitle = "SMS, contacts, phone state, camera and microphone",
                granted = states.runtime,
                onFix = onRequestRuntime
            ),
            Row(
                icon = R.drawable.ic_bell,
                title = "Notification mirroring",
                subtitle = "Read and reply to phone notifications from your Mac",
                granted = states.notificationListener,
                onFix = onOpenNotificationSettings
            ),
            Row(
                icon = R.drawable.ic_folder,
                title = "File access",
                subtitle = "Browse and transfer files & photos",
                granted = states.storage,
                onFix = onOpenStorageSettings
            ),
            Row(
                icon = R.drawable.ic_battery,
                title = "Unrestricted battery",
                subtitle = "Keep the connection alive in the background",
                granted = states.battery,
                onFix = onOpenBatterySettings
            ),
            Row(
                icon = R.drawable.ic_accessibility,
                title = "Accessibility service",
                subtitle = "Wireless touch, mirroring and background clipboard",
                granted = states.accessibility,
                onFix = onOpenAccessibilitySettings
            )
        ).filter { !it.granted }

        if (missing.isEmpty()) return

        AppCard {
            Text(
                "Permissions needed",
                fontSize = LT.section,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            missing.forEachIndexed { index, row ->
                if (index > 0) HairlineDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LS.m),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PermissionTile(row.icon)
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.title,
                            fontSize = LT.body,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            row.subtitle,
                            fontSize = LT.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(onClick = row.onFix, shape = LR.control) { Text("Fix") }
                }
            }
        }
    }

    // ---------------------------------------------------- permission state

    private data class PermissionStates(
        val runtime: Boolean,
        val notificationListener: Boolean,
        val storage: Boolean,
        val battery: Boolean,
        val accessibility: Boolean
    ) {
        val allGranted: Boolean
            get() = runtime && notificationListener && storage && battery && accessibility
    }

    private fun runtimePermissionBundle(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return perms.toTypedArray()
    }

    private fun permissionStates(): PermissionStates {
        val runtimeGranted = runtimePermissionBundle().all {
            checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        val listenerEnabled = try {
            Settings.Secure.getString(
                contentResolver, "enabled_notification_listeners"
            )?.contains(packageName) == true
        } catch (_: Exception) { false }

        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true

        val batteryGranted = try {
            (getSystemService(POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(packageName)
        } catch (_: Exception) { false }

        return PermissionStates(
            runtime = runtimeGranted,
            notificationListener = listenerEnabled,
            storage = storageGranted,
            battery = batteryGranted,
            accessibility = LinkTouchAccessibilityService.instance != null
        )
    }

    // ---------------------------------------------------- non-UI (unchanged)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    ClipboardManagerBridge.sendClipboardToMac(sharedText)
                    val trimmed = sharedText.trim()
                    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                        val frame = WireProtocol.packJson(
                            type = "URL_HANDOFF",
                            payloadMap = mapOf("url" to trimmed)
                        )
                        LinkConnectionService.instance?.sendRawFrame(frame)
                    }
                }

                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                }

                if (streamUri != null) {
                    shareUriToMac(streamUri)
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                }

                if (!streamUris.isNullOrEmpty()) {
                    for (uri in streamUris) {
                        shareUriToMac(uri)
                    }
                }
            }
        }
    }

    private fun shareUriToMac(uri: android.net.Uri) {
        Thread {
            try {
                var fileName = "shared_${System.currentTimeMillis()}"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        cursor.getString(nameIdx)?.let { fileName = it }
                    }
                }
                val tempFile = java.io.File(cacheDir, fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                FilePhotosBridge.streamFileToMac(tempFile.absolutePath, fileName, "true")
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Failed to share URI to Mac: ${e.message}")
            }
        }.start()
    }

    private fun triggerConnect(host: String, port: Int, token: String, macName: String = "") {
        startService(Intent(this, LinkConnectionService::class.java).apply {
            action = "CONNECT_HOST"
            putExtra("host", host)
            putExtra("port", port)
            putExtra("token", token)
            putExtra("macName", macName)
        })
    }
}
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/ProtocolModels.kt
```kotlin
package com.linktomac.android

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WireProtocol {
    const val FRAME_JSON: Byte       = 0x01
    const val FRAME_FILE_CHUNK: Byte = 0x02
    const val FRAME_VIDEO_JPEG: Byte = 0x03
    const val FRAME_AUDIO_PCM: Byte  = 0x04

    fun packFrame(frameType: Byte, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(5 + payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(payload.size)
        buffer.put(frameType)
        buffer.put(payload)
        return buffer.array()
    }

    fun packJson(type: String, payloadMap: Map<String, String> = emptyMap(), extraBlock: (JSONObject.() -> Unit)? = null): ByteArray {
        val root = JSONObject()
        root.put("type", type)
        root.put("timestamp", System.currentTimeMillis())
        val payloadObj = JSONObject()
        for ((k, v) in payloadMap) {
            payloadObj.put(k, v)
        }
        root.put("payload", payloadObj)
        extraBlock?.invoke(root)
        return packFrame(FRAME_JSON, root.toString().toByteArray(Charsets.UTF_8))
    }
}
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/ScreenCameraStreamer.kt
```kotlin
package com.linktomac.android

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Display
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

object ScreenCameraStreamer {
    private const val TAG = "ScreenCamStreamer"

    private var streamJob: Job? = null
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var virtualDisplay: VirtualDisplay? = null

    @Volatile
    var activeMediaProjection: MediaProjection? = null

    fun handleStreamControl(context: Context, mode: String) {
        handleStreamControl(context, org.json.JSONObject().put("mode", mode))
    }

    fun handleStreamControl(context: Context, payload: org.json.JSONObject) {
        val mode = payload.optString("mode")
        when (mode) {
            "REMOTE_TAP" -> {
                val normX = payload.optString("x").toFloatOrNull() ?: return
                val normY = payload.optString("y").toFloatOrNull() ?: return
                LinkTouchAccessibilityService.instance?.performNormalizedTap(normX, normY)
            }
            "REMOTE_KEY" -> {
                val keyCode = payload.optString("keyCode").toIntOrNull() ?: return
                LinkTouchAccessibilityService.instance?.performRemoteKey(keyCode)
            }
            "WEBCAM_BACK" -> {
                stopAllStreams()
                startCameraStream(context, CameraCharacteristics.LENS_FACING_BACK)
            }
            "WEBCAM_FRONT" -> {
                stopAllStreams()
                startCameraStream(context, CameraCharacteristics.LENS_FACING_FRONT)
            }
            "MIC_STREAM" -> {
                stopAllStreams()
                startMicrophoneStream()
            }
            "MIRROR" -> {
                stopAllStreams()
                startMirrorStatusPreview(context)
            }
            "NONE" -> {
                stopAllStreams()
            }
            else -> {
                // Do NOT call stopAllStreams() for unknown or action commands!
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startCameraStream(context: Context, lensFacing: Int) {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == lensFacing
        } ?: cm.cameraIdList.firstOrNull() ?: return

        val ht = HandlerThread("LinkToMacCam").apply { start() }
        workerThread = ht
        val handler = Handler(ht.looper)

        val reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2)
        imageReader = reader
        var lastSent = 0L

        reader.setOnImageAvailableListener({ ir ->
            val img = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
            val now = System.currentTimeMillis()
            if (now - lastSent >= 65) { // ~15 fps over universal socket
                lastSent = now
                val jpeg = yuv420ToJpeg(img)
                if (jpeg != null) {
                    val frame = WireProtocol.packFrame(WireProtocol.FRAME_VIDEO_JPEG, jpeg)
                    LinkConnectionService.instance?.sendRawFrame(frame)
                }
            }
            img.close()
        }, handler)

        try {
            cm.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(reader.surface)
                    }
                    camera.createCaptureSession(
                        listOf(reader.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                session.setRepeatingRequest(req.build(), null, handler)
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {}
                        },
                        handler
                    )
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, handler)
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun startMicrophoneStream() {
        streamJob = CoroutineScope(Dispatchers.IO).launch {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
            val buf = ByteArray(2048)
            recorder.startRecording()
            try {
                while (isActive) {
                    val read = recorder.read(buf, 0, buf.size)
                    if (read > 0) {
                        val chunk = buf.copyOf(read)
                        val frame = WireProtocol.packFrame(WireProtocol.FRAME_AUDIO_PCM, chunk)
                        LinkConnectionService.instance?.sendRawFrame(frame)
                    }
                }
            } finally {
                recorder.stop()
                recorder.release()
            }
        }
    }

    fun startMirrorStatusPreview(context: Context) {
        val proj = activeMediaProjection
        if (proj != null) {
            startMediaProjectionMirror(context, proj)
            return
        }

        val width = context.resources.displayMetrics.widthPixels
        val height = context.resources.displayMetrics.heightPixels
        val targetW = 540
        val targetH = ((height.toFloat() / width.toFloat()) * targetW).toInt().coerceAtLeast(960)

        var inFlight = false
        streamJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && LinkTouchAccessibilityService.instance != null) {
                    if (!inFlight) {
                        inFlight = true
                        LinkTouchAccessibilityService.instance?.captureScreenJpeg { jpegBytes ->
                            try {
                                if (jpegBytes != null) {
                                    val frame = WireProtocol.packFrame(WireProtocol.FRAME_VIDEO_JPEG, jpegBytes)
                                    LinkConnectionService.instance?.sendRawFrame(frame)
                                }
                            } finally {
                                inFlight = false
                            }
                        }
                    }
                    delay(55) // ~18 fps
                } else {
                    // Fall back to drawing status canvas if LinkTouchAccessibilityService.instance == null
                    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.rgb(18, 22, 28))
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(74, 222, 128)
                        textSize = 28f
                    }
                    canvas.drawText("LinkToMac Screen Mirroring", 50f, (targetH / 2 - 40).toFloat(), paint)
                    paint.color = Color.WHITE
                    paint.textSize = 20f
                    canvas.drawText("Enable LinkToMac Accessibility Service", 50f, (targetH / 2 + 10).toFloat(), paint)
                    canvas.drawText("on phone for Live Wireless Mirror", 50f, (targetH / 2 + 45).toFloat(), paint)

                    val baos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                    bmp.recycle()
                    val frame = WireProtocol.packFrame(WireProtocol.FRAME_VIDEO_JPEG, baos.toByteArray())
                    LinkConnectionService.instance?.sendRawFrame(frame)
                    delay(600)
                }
            }
        }
    }

    private fun startMediaProjectionMirror(context: Context, projection: MediaProjection) {
        val realW = context.resources.displayMetrics.widthPixels
        val realH = context.resources.displayMetrics.heightPixels
        val targetW = 540
        val targetH = ((realH.toFloat() / realW.toFloat()) * targetW).toInt().coerceAtLeast(960)

        val ht = HandlerThread("LinkToMacProj").apply { start() }
        workerThread = ht
        val handler = Handler(ht.looper)

        val reader = ImageReader.newInstance(targetW, targetH, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        val metrics = context.resources.displayMetrics
        val vd = projection.createVirtualDisplay(
            "LinkToMacMirror",
            targetW, targetH, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler
        )
        virtualDisplay = vd

        var lastSent = 0L
        reader.setOnImageAvailableListener({ ir ->
            val img = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
            val now = System.currentTimeMillis()
            if (now - lastSent >= 50) { // ~20 fps
                lastSent = now
                val planes = img.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * targetW

                val bmp = Bitmap.createBitmap(targetW + rowPadding / pixelStride, targetH, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(buffer)
                val finalBmp = if (rowPadding > 0) Bitmap.createBitmap(bmp, 0, 0, targetW, targetH) else bmp

                val baos = ByteArrayOutputStream()
                finalBmp.compress(Bitmap.CompressFormat.JPEG, 65, baos)
                if (rowPadding > 0) finalBmp.recycle()
                bmp.recycle()

                val frame = WireProtocol.packFrame(WireProtocol.FRAME_VIDEO_JPEG, baos.toByteArray())
                LinkConnectionService.instance?.sendRawFrame(frame)
            }
            img.close()
        }, handler)
    }

    fun stopAllStreams() {
        streamJob?.cancel()
        streamJob = null

        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null

        try { cameraDevice?.close() } catch (_: Exception) {}
        cameraDevice = null

        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null

        try { workerThread?.quitSafely() } catch (_: Exception) {}
        workerThread = null
    }

    private fun yuv420ToJpeg(image: android.media.Image): ByteArray? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 65, out)
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/SmsContactsCallBridge.kt
```kotlin
package com.linktomac.android

import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject

object SmsContactsCallBridge {

    @SuppressLint("Range")
    fun syncSmsAndContactsToMac(context: Context) {
        val contactsMap = mutableMapOf<String, String>()
        val contactsArray = JSONArray()

        // 1. Load Contacts
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count < 200) {
                    val id = cursor.getString(0) ?: ""
                    val name = cursor.getString(1) ?: ""
                    val number = cursor.getString(2)?.replace(" ", "") ?: ""
                    if (number.isNotEmpty()) {
                        contactsMap[number] = name
                        contactsArray.put(JSONObject().apply {
                            put("id", "$id-$number")
                            put("name", name)
                            put("phoneNumber", number)
                        })
                        count++
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Load Recent SMS Messages & Group by Address
        val threadsMap = linkedMapOf<String, MutableList<JSONObject>>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                ),
                null, null,
                Telephony.Sms.DATE + " DESC LIMIT 120"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: ""
                    val addr = cursor.getString(1) ?: "Unknown"
                    val body = cursor.getString(2) ?: ""
                    val date = cursor.getLong(3)
                    val type = cursor.getInt(4)
                    val isIncoming = (type == Telephony.Sms.MESSAGE_TYPE_INBOX)

                    val msgJson = JSONObject().apply {
                        put("id", id)
                        put("address", addr)
                        put("body", body)
                        put("date", date)
                        put("isIncoming", isIncoming)
                    }
                    threadsMap.getOrPut(addr) { mutableListOf() }.add(0, msgJson)
                }
            }
        } catch (_: Exception) {}

        val threadsArray = JSONArray()
        for ((addr, messages) in threadsMap) {
            val lastMsg = messages.lastOrNull()
            val contactName = contactsMap[addr] ?: addr
            threadsArray.put(JSONObject().apply {
                put("id", addr)
                put("contactName", contactName)
                put("address", addr)
                put("lastMessage", lastMsg?.optString("body") ?: "")
                put("timestamp", lastMsg?.optLong("date") ?: System.currentTimeMillis())
                put("messages", JSONArray(messages))
            })
        }

        val frame = WireProtocol.packJson("SMS_THREADS_SYNC") {
            put("smsThreads", threadsArray)
            put("contacts", contactsArray)
        }
        LinkConnectionService.instance?.sendRawFrame(frame)
    }

    fun sendSms(context: Context, address: String, body: String) {
        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(address, null, body, null, null)
        } catch (_: Exception) {}
    }

    // MARK: - Call Controls (Cellular Audio Remains on Phone)
    @SuppressLint("MissingPermission")
    fun handleCallAction(context: Context, action: String, number: String, smsReply: String) {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        try {
            when (action) {
                "ANSWER" -> tm.acceptRingingCall()
                "DECLINE", "END" -> tm.endCall()
                "DECLINE_WITH_SMS" -> {
                    tm.endCall()
                    if (number.isNotEmpty() && smsReply.isNotEmpty()) {
                        sendSms(context, number, smsReply)
                    }
                }
                "DIAL" -> {
                    if (number.isNotEmpty()) {
                        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(callIntent)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun registerCallStateReceiver(context: Context) {
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val stateStr = intent?.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Cellular Call"
                val mappedState = when (stateStr) {
                    TelephonyManager.EXTRA_STATE_RINGING -> "RINGING"
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> "OFFHOOK"
                    else -> "IDLE"
                }
                val frame = WireProtocol.packJson(
                    type = "CALL_STATE",
                    payloadMap = mapOf(
                        "state" to mappedState,
                        "number" to incomingNumber,
                        "contactName" to incomingNumber
                    )
                )
                LinkConnectionService.instance?.sendRawFrame(frame)
            }
        }, filter)
    }
}
```

---

### FILE: allcode/LinkToMac-Android/gradlew
```bash
#!/bin/sh

#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
#
#   Gradle start up script for POSIX generated by Gradle.
#
#   Important for running:
#
#   (1) You need a POSIX-compliant shell to run this script. If your /bin/sh is
#       noncompliant, but you have some other compliant shell such as ksh or
#       bash, then to run this script, type that shell name before the whole
#       command line, like:
#
#           ksh Gradle
#
#       Busybox and similar reduced shells will NOT work, because this script
#       requires all of these POSIX shell features:
#         * functions;
#         * expansions «$var», «${var}», «${var:-default}», «${var+SET}»,
#           «${var#prefix}», «${var%suffix}», and «$( cmd )»;
#         * compound commands having a testable exit status, especially «case»;
#         * various built-in commands including «command», «set», and «ulimit».
#
#   Important for patching:
#
#   (2) This script targets any POSIX shell, so it avoids extensions provided
#       by Bash, Ksh, etc; in particular arrays are avoided.
#
#       The "traditional" practice of packing multiple parameters into a
#       space-separated string is a well documented source of bugs and security
#       problems, so this is (mostly) avoided, by progressively accumulating
#       options in "$@", and eventually passing that to Java.
#
#       Where the inherited environment variables (DEFAULT_JVM_OPTS, JAVA_OPTS,
#       and GRADLE_OPTS) rely on word-splitting, this is performed explicitly;
#       see the in-line comments for details.
#
#       There are tweaks for specific operating systems such as AIX, CygWin,
#       Darwin, MinGW, and NonStop.
#
#   (3) This script is generated from the Groovy template
#       https://github.com/gradle/gradle/blob/HEAD/subprojects/plugins/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt
#       within the Gradle project.
#
#       You can find Gradle at https://github.com/gradle/gradle/.
#
##############################################################################

# Attempt to set APP_HOME

# Resolve links: $0 may be a link
app_path=$0

# Need this for daisy-chained symlinks.
while
    APP_HOME=${app_path%"${app_path##*/}"}  # leaves a trailing /; empty if no leading path
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

# This is normally unused
# shellcheck disable=SC2034
APP_BASE_NAME=${0##*/}
# Discard cd standard output in case $CDPATH is set (https://github.com/gradle/gradle/issues/25036)
APP_HOME=$( cd "${APP_HOME:-./}" > /dev/null && pwd -P ) || exit

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD=maximum

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "$( uname )" in                #(
  CYGWIN* )         cygwin=true  ;; #(
  Darwin* )         darwin=true  ;; #(
  MSYS* | MINGW* )  msys=true    ;; #(
  NONSTOP* )        nonstop=true ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar


# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1
    then
        die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
fi

# Increase the maximum file descriptors if we can.
if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    case $MAX_FD in #(
      max*)
        # In POSIX sh, ulimit -H is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        MAX_FD=$( ulimit -H -n ) ||
            warn "Could not query maximum file descriptor limit"
    esac
    case $MAX_FD in  #(
      '' | soft) :;; #(
      *)
        # In POSIX sh, ulimit -n is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        ulimit -n "$MAX_FD" ||
            warn "Could not set maximum file descriptor limit to $MAX_FD"
    esac
fi

# Collect all arguments for the java command, stacking in reverse order:
#   * args from the command line
#   * the main class name
#   * -classpath
#   * -D...appname settings
#   * --module-path (only if needed)
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and GRADLE_OPTS environment variables.

# For Cygwin or MSYS, switch paths to Windows format before running java
if "$cygwin" || "$msys" ; then
    APP_HOME=$( cygpath --path --mixed "$APP_HOME" )
    CLASSPATH=$( cygpath --path --mixed "$CLASSPATH" )

    JAVACMD=$( cygpath --unix "$JAVACMD" )

    # Now convert the arguments - kludge to limit ourselves to /bin/sh
    for arg do
        if
            case $arg in                                #(
              -*)   false ;;                            # don't mess with options #(
              /?*)  t=${arg#/} t=/${t%%/*}              # looks like a POSIX filepath
                    [ -e "$t" ] ;;                      #(
              *)    false ;;
            esac
        then
            arg=$( cygpath --path --ignore --mixed "$arg" )
        fi
        # Roll the args list around exactly as many times as the number of
        # args, so each arg winds up back in the position where it started, but
        # possibly modified.
        #
        # NB: a `for` loop captures its iteration list before it begins, so
        # changing the positional parameters here affects neither the number of
        # iterations, nor the values presented in `arg`.
        shift                   # remove old arg
        set -- "$@" "$arg"      # push replacement arg
    done
fi


# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Collect all arguments for the java command:
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, JAVA_OPTS, and optsEnvironmentVar are not allowed to contain shell fragments,
#     and any embedded shellness will be escaped.
#   * For example: A user cannot expect ${Hostname} to be expanded, as it is an environment variable and will be
#     treated as '${Hostname}' itself on the command line.

set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -classpath "$CLASSPATH" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"

# Stop when "xargs" is not available.
if ! command -v xargs >/dev/null 2>&1
then
    die "xargs is not available"
fi

# Use "xargs" to parse quoted args.
#
# With -n1 it outputs one arg per line, with the quotes and backslashes removed.
#
# In Bash we could simply go:
#
#   readarray ARGS < <( xargs -n1 <<<"$var" ) &&
#   set -- "${ARGS[@]}" "$@"
#
# but POSIX shell has neither arrays nor command substitution, so instead we
# post-process each arg (as a line of input to sed) to backslash-escape any
# character that might be a shell metacharacter, then use eval to reverse
# that process (while maintaining the separation between arguments), and wrap
# the whole thing up as a single "set" statement.
#
# This will of course break if any of these variables contains a newline or
# an unmatched quote.
#

eval "set -- $(
        printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
        xargs -n1 |
        sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
        tr '\n' ' '
    )" '"$@"'

exec "$JAVACMD" "$@"
```

---
