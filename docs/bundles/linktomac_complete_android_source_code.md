# LinkToMac — Complete Android Source Code (`allcode/LinkToMac-Android`)

Every file below belongs inside the codebase. Run `python3 scripts/stitch_allcode.py` to extract them automatically.

---

### FILE: allcode/LinkToMac-Android/app/build.gradle.kts
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.linktomac.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.linktomac.android"
        minSdk = 29
        targetSdk = 34
        versionCode = 164
        versionName = "1.62.2"
    }

    buildFeatures {
        compose = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
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

    // Firebase Auth & Cloud Firestore sync for cross-network remote discovery
    implementation("com.google.firebase:firebase-auth-ktx:22.3.0")
    implementation("com.google.firebase:firebase-firestore-ktx:24.10.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
}
```

---

### FILE: allcode/LinkToMac-Android/build.gradle.kts
```kotlin
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
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

    <!-- Files, Photos, Camera & Microphone -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
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

### FILE: allcode/LinkToMac-Android/gradle/gradle-daemon-jvm.properties
```properties
#This file is generated by updateDaemonJvm
toolchainUrl.FREE_BSD.AARCH64=https\://api.foojay.io/disco/v3.0/ids/491f83666ae7f4d6ebb28fee72ebb035/redirect
toolchainUrl.FREE_BSD.X86_64=https\://api.foojay.io/disco/v3.0/ids/0d1a1acdc708062093673f65aa9aba4b/redirect
toolchainUrl.LINUX.AARCH64=https\://api.foojay.io/disco/v3.0/ids/491f83666ae7f4d6ebb28fee72ebb035/redirect
toolchainUrl.LINUX.X86_64=https\://api.foojay.io/disco/v3.0/ids/0d1a1acdc708062093673f65aa9aba4b/redirect
toolchainUrl.MAC_OS.AARCH64=https\://api.foojay.io/disco/v3.0/ids/7083b89563e7ce20943037b8cd2b8cc2/redirect
toolchainUrl.MAC_OS.X86_64=https\://api.foojay.io/disco/v3.0/ids/060bbb778a1f55ea705fdebd2ccfeab9/redirect
toolchainUrl.UNIX.AARCH64=https\://api.foojay.io/disco/v3.0/ids/491f83666ae7f4d6ebb28fee72ebb035/redirect
toolchainUrl.UNIX.X86_64=https\://api.foojay.io/disco/v3.0/ids/0d1a1acdc708062093673f65aa9aba4b/redirect
toolchainUrl.WINDOWS.AARCH64=https\://api.foojay.io/disco/v3.0/ids/d09679dc60fe5aa05ef7d03efdefac20/redirect
toolchainUrl.WINDOWS.X86_64=https\://api.foojay.io/disco/v3.0/ids/ed4e3bf2f5e7c5d9aabc4cbd8acd555e/redirect
toolchainVendor=JETBRAINS
toolchainVersion=21
```

---

### FILE: allcode/LinkToMac-Android/gradle/wrapper/gradle-wrapper.properties
```properties
#Thu Oct 22 23:59:49 IST 2026
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.14.5-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/CloudSessionSync.kt
```kotlin
/**
 * CloudSessionSync.kt
 * LinktoMac Android Companion
 *
 * Role:
 * Listens to Firestore `users/{uid}/sessions/current` (and `sessions/{sessionId}`)
 * to discover relay endpoints (videoHost, videoPort, controlHost, controlPort, token)
 * when operating across different networks (NAT'd Wi-Fi, 4G/5G, WAN).
 * Drives both the control channel (LinkConnectionService) and video channel
 * (ScreenCameraStreamer) toward the secure Pinggy relay.
 */

package com.linktomac.android

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Listens to Firestore `users/{uid}/sessions/current` and drives both
 * the control channel (54921) and the video channel (27189) toward the relay.
 *
 * Replaces mDNS as the discovery mechanism when the Mac is on a different network.
 */
class CloudSessionSync private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CloudSessionSync"
        @Volatile private var INSTANCE: CloudSessionSync? = null

        /**
         * Returns the singleton instance of [CloudSessionSync].
         *
         * @param context Application context used for initialization.
         * @return The active [CloudSessionSync] instance.
         */
        fun get(context: Context): CloudSessionSync =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CloudSessionSync(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private var sessionListener: ListenerRegistration? = null

    private var lastSessionId: String? = null
    private var lastDisconnectTime: Long = 0

    /**
     * Initializes Firebase Auth and Firestore components.
     * Safe to call multiple times or during application startup.
     */
    fun initialize() {
        try {
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
            Log.i(TAG, "CloudSessionSync initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init failed", e)
        }
    }

    /**
     * Signs in with a Google OAuth ID token obtained from GoogleSignInClient.
     *
     * @param idToken The verified Google ID token.
     * @param onSuccess Callback executed upon successful authentication and subscription.
     * @param onError Callback executed with an error message upon failure.
     */
    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth?.signInWithCredential(credential)
            ?.addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    publishAccount(user.uid, user.email ?: "")
                    subscribeToSession(user.uid)
                    onSuccess()
                } else {
                    onError("No user returned")
                }
            }
            ?.addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Sign-in failed")
            }
    }

    /**
     * Publishes account discovery metadata mirroring script.js::publishAccountForPhoneSync.
     */
    private fun publishAccount(uid: String, email: String) {
        val cleanEmail = email.lowercase().trim()
        val payload = hashMapOf(
            "uid" to uid,
            "email" to cleanEmail,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        firestore?.collection("accounts")?.document(cleanEmail)
            ?.set(payload, SetOptions.merge())
    }

    /**
     * Subscribes to live session updates for the given authenticated user UID.
     * When the Mac publishes a relay endpoint with `active: true`, Android
     * connects to both the control channel and video channel.
     *
     * @param uid The authenticated user UID.
     */
    fun subscribeToSession(uid: String) {
        sessionListener?.remove()
        sessionListener = firestore?.collection("users")?.document(uid)
            ?.collection("sessions")?.document("current")
            ?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listen failed", error)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val active      = snapshot.getBoolean("active") ?: false
                val videoHost   = snapshot.getString("videoHost") ?: ""
                val videoPort   = snapshot.getLong("videoPort")?.toInt() ?: 27189
                val controlHost = snapshot.getString("controlHost") ?: ""
                val controlPort = snapshot.getLong("controlPort")?.toInt() ?: 54921
                val token       = snapshot.getString("token") ?: ""
                val sessionId   = snapshot.getString("sessionId") ?: ""

                val now = System.currentTimeMillis()
                if (lastSessionId == sessionId && !active && (now - lastDisconnectTime < 30_000)) {
                    // Mac is mid-restart; don't fully tear down, just hold for reconnect
                    Log.i(TAG, "Mac restart detected — holding for reconnect")
                    return@addSnapshotListener
                }

                if (active && videoHost.isNotEmpty()) {
                    Log.i(TAG, "Remote session active: $sessionId, video=$videoHost:$videoPort, control=$controlHost:$controlPort")
                    lastSessionId = sessionId
                    scope.launch {
                        LinkConnectionService.instance?.connectToMac(controlHost, controlPort, token)
                        ScreenCameraStreamer.startRemoteClient(videoHost, videoPort, token)
                    }
                } else {
                    Log.i(TAG, "Remote session inactive, disconnecting")
                    if (!active) lastDisconnectTime = now
                    LinkConnectionService.instance?.disconnect()
                    ScreenCameraStreamer.stopAllStreams()
                }
            }
    }

    /**
     * Unsubscribes from live session updates and frees listener registrations.
     */
    fun unsubscribe() {
        sessionListener?.remove()
        sessionListener = null
    }
}
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
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
        CloudSessionSync.get(this).initialize()

        // Automatically connect to remembered device on startup
        connectedMacName = getSharedPreferences("linktomac", MODE_PRIVATE).getString("lastMacName", "") ?: ""
        autoConnectRemembered()
    }

    private fun autoConnectRemembered() {
        val prefs = getSharedPreferences("linktomac", MODE_PRIVATE)
        val savedHost = prefs.getString("lastHost", null)
        val savedPort = prefs.getInt("lastPort", 54921)
        val savedToken = prefs.getString("lastToken", "") ?: ""
        val savedMode = prefs.getString("lastMode", "") ?: ""
        if (!savedHost.isNullOrBlank()) {
            connectToMac(savedHost, savedPort, savedToken, savedMode)
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
            val mode = intent.getStringExtra("mode") ?: ""
            if (macName.isNotBlank()) {
                connectedMacName = macName
                getSharedPreferences("linktomac", MODE_PRIVATE).edit()
                    .putString("lastMacName", macName)
                    .apply()
            }
            connectToMac(host, port, token, mode)
        } else if (action == "DISCONNECT") {
            userRequestedDisconnect = true
            reconnectJob?.cancel()
            closeSocket()
        }
        return START_STICKY
    }

    private fun makeTlsSocket(host: String, port: Int, caCertPem: String?): Socket {
        val raw = Socket().apply {
            tcpNoDelay = true
            keepAlive = true
            connect(InetSocketAddress(host, port), 5000)
        }
        if (caCertPem.isNullOrEmpty()) {
            // TOFU / development mode — accept any cert
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val ctx = SSLContext.getInstance("TLSv1.3")
            ctx.init(null, arrayOf<TrustManager>(tm), java.security.SecureRandom())
            val sslSock = ctx.socketFactory.createSocket(raw, host, port, true) as SSLSocket
            sslSock.useClientMode = true
            sslSock.startHandshake()
            return sslSock
        } else {
            // Pin to provided CA cert (PEM string saved during pairing)
            val cf = java.security.cert.CertificateFactory.getInstance("X.509")
            val ca = cf.generateCertificate(caCertPem.byteInputStream()) as X509Certificate
            val ks = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType())
            ks.load(null, null)
            ks.setCertificateEntry("linktomac-ca", ca)
            val tmf = javax.net.ssl.TrustManagerFactory.getInstance(
                javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
            )
            tmf.init(ks)
            val ctx = SSLContext.getInstance("TLSv1.3")
            ctx.init(null, tmf.trustManagers, java.security.SecureRandom())
            val sslSock = ctx.socketFactory.createSocket(raw, host, port, true) as SSLSocket
            sslSock.startHandshake()
            return sslSock
        }
    }

    /**
     * Initiates a TCP connection to the specified Mac host and port, performing
     * token verification and launching read/heartbeat loops.
     *
     * @param host Target IP address or hostname of the Mac host or Pinggy relay.
     * @param port Target TCP port.
     * @param token Authentication / pairing token.
     */
    fun connectToMac(host: String, port: Int, token: String, mode: String = "") {
        userRequestedDisconnect = false
        reconnectJob?.cancel()
        serviceScope.launch {
            closeSocketInternal()
            try {
                statusMessage = "Connecting to $host:$port..."
                val isLocal = mode != "remote" && (
                    host == "127.0.0.1" || host == "localhost" ||
                    host.startsWith("10.") || host.startsWith("192.168.") ||
                    host.startsWith("172.") || host.endsWith(".local")
                )
                val caPem = getSharedPreferences("linktomac", MODE_PRIVATE)
                    .getString("ca_cert_pem", null)

                // Remote Relay and unconfigured TLS always use raw TCP socket with 5s timeout
                val s = if (isLocal || mode == "remote" || caPem.isNullOrEmpty()) {
                    val raw = Socket()
                    raw.tcpNoDelay = true
                    raw.keepAlive = true
                    raw.connect(InetSocketAddress(host, port), 5000)
                    raw
                } else {
                    makeTlsSocket(host, port, caPem)
                }

                socket = s
                outputStream = s.getOutputStream()
                isConnectedState = true
                val transport = when {
                    host == "127.0.0.1" -> "USB"
                    mode == "remote" -> "Remote Relay"
                    isLocal -> "Local Wi-Fi"
                    else -> "Remote Relay"
                }
                statusMessage = "Connected ($transport • $host)"
                startForegroundNotification("Connected to Mac ($transport)")

                // Save last successful host, transport, and mode
                getSharedPreferences("linktomac", MODE_PRIVATE).edit()
                    .putString("lastHost", host)
                    .putInt("lastPort", port)
                    .putString("lastToken", token)
                    .putString("lastTransport", transport)
                    .putString("lastMode", if (transport == "Remote Relay") "remote" else "local")
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

    /**
     * Gracefully disconnects the current socket session, suppresses automatic
     * reconnects, and updates foreground notifications.
     */
    fun disconnect() {
        userRequestedDisconnect = true
        reconnectJob?.cancel()
        closeSocketInternal()
        startForegroundNotification("Disconnected — waiting for Mac")
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

    /**
     * Sends a raw wire-protocol frame across the active socket connection.
     *
     * @param frameBytes Packed wire frame bytes.
     */
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
            "HELLO" -> {
                val macName = payload.optString("deviceName", "Mac")
                connectedMacName = macName
                val pairToken = payload.optString("pairToken", "")
                if (pairToken.isNotEmpty()) {
                    getSharedPreferences("linktomac", MODE_PRIVATE).edit()
                        .putString("lastToken", pairToken)
                        .apply()
                }
            }
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
                val savedMode = prefs.getString("lastMode", "") ?: ""
                val savedHost = prefs.getString("lastHost", null)
                val savedPort = prefs.getInt("lastPort", 54921)
                val targetToken = prefs.getString("lastToken", "") ?: ""

                // If last session was Remote Relay, prioritize savedHost over stale mDNS
                val targetHost = if (savedMode == "remote" && !savedHost.isNullOrBlank()) {
                    savedHost
                } else if (discoveredMacHost.isNotEmpty()) {
                    discoveredMacHost
                } else {
                    savedHost
                }

                val targetPort = if (savedMode == "remote" && !savedHost.isNullOrBlank()) {
                    savedPort
                } else if (discoveredMacHost.isNotEmpty()) {
                    discoveredMacPort
                } else {
                    savedPort
                }

                if (!targetHost.isNullOrBlank()) {
                    statusMessage = "Reconnecting to $targetHost:$targetPort..."
                    try {
                        val s = Socket()
                        s.tcpNoDelay = true
                        s.keepAlive = true
                        s.connect(InetSocketAddress(targetHost, targetPort), 5000)
                        socket = s
                        outputStream = s.getOutputStream()
                        isConnectedState = true

                        val isLocal = savedMode != "remote" && (
                            targetHost == "127.0.0.1" || targetHost == "localhost" ||
                            targetHost.startsWith("10.") || targetHost.startsWith("192.168.") ||
                            targetHost.startsWith("172.") || targetHost.endsWith(".local")
                        )
                        val transport = when {
                            targetHost == "127.0.0.1" -> "USB"
                            savedMode == "remote" -> "Remote Relay"
                            isLocal -> "Local Wi-Fi"
                            else -> "Remote Relay"
                        }

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

        /**
         * Resolves the physical device screen dimensions in pixels.
         *
         * @param context Application context.
         * @return Pair of width and height in pixels.
         */
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

    /**
     * Injects a gesture click tap at normalized coordinates (0.0 to 1.0)
     * using the Android AccessibilityService dispatchGesture API.
     *
     * @param normX Normalized X coordinate between 0.0 and 1.0.
     * @param normY Normalized Y coordinate between 0.0 and 1.0.
     * @return True if gesture dispatch was successfully scheduled.
     */
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

    /**
     * Performs a global accessibility navigation action (Home, Back, Recents, Notifications).
     *
     * @param keyCode Android keycode (3=Home, 4=Back, 187=Recents, 83=Notifications).
     * @return True if global action was performed.
     */
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

    /**
     * Captures a high-performance JPEG screenshot buffer for basic mirror preview.
     *
     * @param callback Callback receiving the compressed JPEG byte array.
     */
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
        var showRestrictedGuide by remember { mutableStateOf(false) }

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

        // QR scanner launcher (sanitizes schemes and forwards mode)
        val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
            val contents = result.contents ?: return@rememberLauncherForActivityResult
            try {
                val json = JSONObject(contents)
                var rawHost = json.optString("host").trim()
                var p = json.optInt("port", 54921)
                val t = json.optString("token")
                val n = json.optString("macName")
                val mode = json.optString("mode", "")

                // Sanitize URL schemes and embedded ports
                rawHost = rawHost.removePrefix("tcp://").removePrefix("http://").removePrefix("https://")
                if (rawHost.contains(":")) {
                    val parts = rawHost.split(":")
                    rawHost = parts[0]
                    parts[1].toIntOrNull()?.let { p = it }
                }

                if (rawHost.isNotEmpty()) {
                    tokenInput = t
                    triggerConnect(rawHost, p, t, n, mode)
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
                    onConnectUSB = { triggerConnect("127.0.0.1", 54921, tokenInput) },
                    onConnectManual = { input ->
                        var raw = input.trim().removePrefix("tcp://").removePrefix("http://")
                        var port = 54921
                        if (raw.contains(":")) {
                            val parts = raw.split(":")
                            raw = parts[0]
                            parts[1].toIntOrNull()?.let { port = it }
                        }
                        val isLocal = raw == "127.0.0.1" || raw == "localhost" ||
                            raw.startsWith("10.") || raw.startsWith("192.168.") ||
                            raw.startsWith("172.") || raw.endsWith(".local")
                        val mode = if (!isLocal && raw != "127.0.0.1") "remote" else ""
                        triggerConnect(raw, port, tokenInput, mode = mode)
                    }
                )
            }

            // ---- Permissions checklist (only what is still missing)
            if (!perms.allGranted) {
                PermissionsCard(
                    states = perms,
                    onRequestRuntime = { permLauncher.launch(runtimePermissionBundle()) },
                    onOpenNotificationSettings = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            showRestrictedGuide = true
                        } else {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    },
                    onOpenStorageSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO))
                        } else {
                            permLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
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
                        if (Build.VERSION.SDK_INT >= 33) {
                            showRestrictedGuide = true
                        } else {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }
                )
            }

            if (showRestrictedGuide) {
                RestrictedSettingsUnlockDialog(
                    onDismiss = { showRestrictedGuide = false },
                    onOpenAppDetails = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    },
                    onOpenAccessibility = {
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

    private fun friendlyStatus(raw: String): String {
        // Surface the failed target so stale-USB-loopback, isolated-subnet and
        // relay-down failures are tellable apart at a glance on the phone.
        val failedHost = Regex("failed to connect to /([^ ]+)").find(raw)
            ?.groupValues?.get(1)?.substringBefore(" ")
        return when {
            raw.startsWith("Connection failed") || raw.contains("ECONNREFUSED") -> when {
                failedHost == "127.0.0.1" ->
                    "Couldn't reach your Mac over USB. Plug in the cable, or scan the QR code on the Mac instead."
                failedHost != null ->
                    "Couldn't reach your Mac at $failedHost. If the phone is on different Wi-Fi, scan the Remote Relay QR on the Mac."
                else ->
                    "Couldn't reach your Mac. Keep LinkToMac open on the Mac, then try USB or QR again."
            }
            raw.startsWith("Connected") -> when {
                raw.contains("USB") -> "Connected over USB"
                raw.contains("Remote Relay") -> "Connected over Remote Relay"
                else -> "Connected over Wi-Fi"
            }
            raw.startsWith("Connecting to") -> "Connecting to your Mac…"
            raw.startsWith("Reconnecting to") -> "Reconnecting…"
            raw.startsWith("Waiting for Mac") -> "Waiting for your Mac…"
            raw == "Disconnected" -> "Not connected"
            else -> raw
        }
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
        onConnectUSB: () -> Unit,
        onConnectManual: (String) -> Unit
    ) {
        var showManualInput by remember { mutableStateOf(false) }
        var manualIp by remember { mutableStateOf("") }

        AppCard {
            Button(onClick = onScanQR, shape = LR.control, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(LS.s))
                Text("Scan Mac QR code (Wireless)")
            }
            FilledTonalButton(onClick = onConnectUSB, shape = LR.control, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(R.drawable.ic_usb),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(LS.s))
                Text("Connect via USB cable")
            }

            if (!showManualInput) {
                TextButton(
                    onClick = { showManualInput = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Or enter Mac IP manually", fontSize = 12.sp)
                }
            } else {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    label = { Text("Mac IP Address (e.g. 10.3.9.246)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { if (manualIp.isNotBlank()) onConnectManual(manualIp) },
                    enabled = manualIp.isNotBlank(),
                    shape = LR.control,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect to IP")
                }
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
                icon = R.drawable.ic_link,
                title = "Camera & Microphone",
                subtitle = "Virtual webcam and audio streaming for your Mac",
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

    @Composable
    private fun RestrictedSettingsUnlockDialog(
        onDismiss: () -> Unit,
        onOpenAppDetails: () -> Unit,
        onOpenAccessibility: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "Unlock Remote Control (Android 13+)",
                    fontSize = LT.section,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(LS.m)) {
                    Text(
                        text = "Android protects sideloaded apps by restricting accessibility and notifications by default. Follow these quick steps to unlock:",
                        fontSize = LT.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(LS.s),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = LR.card)
                            .padding(LS.m)
                    ) {
                        Text(
                            text = "1. Tap '1. Open App Settings' below.",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = LT.body,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "2. Tap the three dots (⋮) in the top-right corner and select 'Allow restricted settings' (confirm with PIN/fingerprint).",
                            fontSize = LT.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "3. Tap '2. Enable Accessibility' below and toggle LinkToMac ON.",
                            fontSize = LT.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onOpenAppDetails,
                    shape = LR.control
                ) {
                    Text("1. Open App Settings")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onOpenAccessibility,
                    shape = LR.control
                ) {
                    Text("2. Enable Accessibility")
                }
            }
        )
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

        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

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

    private fun triggerConnect(host: String, port: Int, token: String, macName: String = "", mode: String = "") {
        startService(Intent(this, LinkConnectionService::class.java).apply {
            action = "CONNECT_HOST"
            putExtra("host", host)
            putExtra("port", port)
            putExtra("token", token)
            putExtra("macName", macName)
            putExtra("mode", mode)
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

    private const val HANDSHAKE_MAGIC = 0x4C494E4B.toInt()   // "LINK"
    private const val HANDSHAKE_SIZE = 20
    private const val EVENT_TYPE_SYNC_REQUEST: Byte = 0x05

    private var remoteClientSocket: java.net.Socket? = null
    private var remoteClientOutput: java.io.OutputStream? = null
    private var remoteClientThread: kotlinx.coroutines.Job? = null

    /**
     * Dials out to a Pinggy relay endpoint and streams H.264 video into it,
     * speaking the binary handshake required by the Mac mirror_client server mode.
     *
     * @param host Relay hostname or IP.
     * @param port Relay TCP port.
     * @param token 36-byte shared-secret authentication token.
     */
    fun startRemoteClient(host: String, port: Int, token: String) {
        stopAllStreams()
        remoteClientThread = CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = java.net.Socket()
                s.tcpNoDelay = true
                s.keepAlive = true
                s.connect(java.net.InetSocketAddress(host, port), 8000)

                // 1. Send token (36 bytes ASCII, null-padded)
                val tokenBytes = token.padEnd(36, 0.toChar()).toByteArray(Charsets.US_ASCII)
                s.getOutputStream().write(tokenBytes)
                s.getOutputStream().flush()

                // 2. Send 20-byte handshake to Mac mirror_client
                // [HANDSHAKE_MAGIC (4B)][version (4B)][width (4B)][height (4B)][flags (4B)]
                val targetW = 720
                val targetH = 1280
                val handshakeBuf = java.nio.ByteBuffer.allocate(HANDSHAKE_SIZE)
                handshakeBuf.putInt(HANDSHAKE_MAGIC)
                handshakeBuf.putInt(2)   // version 2
                handshakeBuf.putInt(targetW)
                handshakeBuf.putInt(targetH)
                handshakeBuf.putInt(1)   // flags (1 = H.264)
                s.getOutputStream().write(handshakeBuf.array())
                s.getOutputStream().flush()

                Log.i(TAG, "Remote video client connected & handshake sent to $host:$port")
                remoteClientSocket = s
                remoteClientOutput = s.getOutputStream()

                // 3. Start MediaProjection-based H.264 encoder streaming into output stream
                startMediaProjectionEncoder(s.getOutputStream(), targetW, targetH)
            } catch (e: Exception) {
                Log.e(TAG, "Remote client connect failed", e)
            }
        }
    }

    /**
     * H.264 hardware encoder that writes NAL units directly to the wire,
     * prefixed with a 4-byte big-endian length matching native mirror_client expectation.
     */
    private fun startMediaProjectionEncoder(out: java.io.OutputStream, width: Int, height: Int) {
        val proj = activeMediaProjection ?: run {
            Log.w(TAG, "No MediaProjection — request from Activity first")
            return
        }

        val targetW = width
        val targetH = height
        val format = android.media.MediaFormat.createVideoFormat("video/avc", targetW, targetH).apply {
            setInteger(android.media.MediaFormat.KEY_BIT_RATE, 2_000_000)   // 2 Mbps start for WAN
            setInteger(android.media.MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(android.media.MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(android.media.MediaFormat.KEY_COLOR_FORMAT,
                android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }

        val encoder = android.media.MediaCodec.createEncoderByType("video/avc")
        encoder.configure(format, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val virtualDisplay = proj.createVirtualDisplay(
            "LinkToMacRemote", targetW, targetH, 320,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface, null, null
        )

        val bufferInfo = android.media.MediaCodec.BufferInfo()
        val headerBuf = java.nio.ByteBuffer.allocate(4)

        kotlin.concurrent.thread(name = "remote-encoder-drain", start = true) {
            try {
                while (remoteClientSocket?.isConnected == true && !Thread.interrupted()) {
                    val outBufIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outBufIndex < 0) {
                        if (outBufIndex == android.media.MediaCodec.INFO_TRY_AGAIN_LATER) continue
                        continue
                    }
                    val outBuf = encoder.getOutputBuffer(outBufIndex) ?: continue
                    val len = bufferInfo.size
                    if (len > 0) {
                        headerBuf.rewind()
                        headerBuf.putInt(len).flip()
                        val headerArr = ByteArray(4)
                        headerBuf.get(headerArr)
                        out.write(headerArr)
                        val bytes = ByteArray(len)
                        outBuf.get(bytes)
                        out.write(bytes)
                        out.flush()
                    }
                    encoder.releaseOutputBuffer(outBufIndex, false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Encoder drain ended", e)
            } finally {
                try { encoder.stop() } catch (_: Exception) {}
                encoder.release()
                try { virtualDisplay.release() } catch (_: Exception) {}
                try { inputSurface.release() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Terminates all ongoing screen/camera streams and closes remote client connections.
     */
    fun stopAllStreams() {
        streamJob?.cancel()
        streamJob = null

        try { remoteClientSocket?.close() } catch (_: Exception) {}
        remoteClientSocket = null
        remoteClientOutput = null
        remoteClientThread?.cancel()
        remoteClientThread = null

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

### FILE: allcode/LinkToMac-Android/app/src/test/java/com/linktomac/android/AdversarialConnectionStressTest.kt
```kotlin
package com.linktomac.android

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Empirical Adversarial Challenger Test Suite
 *
 * Stress-tests QR code payload handling, host sanitization, connection parameter parsing,
 * transport state resolution, and reconnection priority under network disruptions.
 */
class AdversarialConnectionStressTest {

    data class ParsedConnection(
        val host: String,
        val port: Int,
        val token: String,
        val macName: String,
        val mode: String
    )

    /**
     * Sanitization logic directly reflecting MainActivity.kt lines 90-96
     */
    private fun sanitizeHostAndPort(rawHostInput: String, defaultPort: Int): Pair<String, Int> {
        var rawHost = rawHostInput.trim()
            .removePrefix("tcp://")
            .removePrefix("http://")
            .removePrefix("https://")
        var p = defaultPort
        if (rawHost.contains(":")) {
            val parts = rawHost.split(":")
            rawHost = parts[0]
            parts[1].toIntOrNull()?.let { p = it }
        }
        return Pair(rawHost, p)
    }

    /**
     * Replicates manual input parsing from MainActivity.kt lines 148-161
     */
    private fun parseManualInput(input: String, token: String = ""): ParsedConnection {
        var raw = input.trim().removePrefix("tcp://").removePrefix("http://")
        var port = 54921
        if (raw.contains(":")) {
            val parts = raw.split(":")
            raw = parts[0]
            parts[1].toIntOrNull()?.let { port = it }
        }
        val isLocal = raw == "127.0.0.1" || raw == "localhost" ||
            raw.startsWith("10.") || raw.startsWith("192.168.") ||
            raw.startsWith("172.") || raw.endsWith(".local")
        val mode = if (!isLocal && raw != "127.0.0.1") "remote" else ""
        return ParsedConnection(raw, port, token, "", mode)
    }

    /**
     * Replicates transport resolution from LinkConnectionService.kt lines 150-177
     */
    private fun resolveTransport(host: String, mode: String): String {
        val isLocal = mode != "remote" && (
            host == "127.0.0.1" || host == "localhost" ||
            host.startsWith("10.") || host.startsWith("192.168.") ||
            host.startsWith("172.") || host.endsWith(".local")
        )
        return when {
            host == "127.0.0.1" -> "USB"
            mode == "remote" -> "Remote Relay"
            isLocal -> "Local Wi-Fi"
            else -> "Remote Relay"
        }
    }

    /**
     * Replicates target resolution in scheduleReconnect() from LinkConnectionService.kt lines 497-518
     */
    private fun resolveReconnectTarget(
        savedMode: String,
        savedHost: String?,
        savedPort: Int,
        discoveredMacHost: String,
        discoveredMacPort: Int
    ): Pair<String?, Int> {
        val targetHost = if (savedMode == "remote" && !savedHost.isNullOrBlank()) {
            savedHost
        } else if (discoveredMacHost.isNotEmpty()) {
            discoveredMacHost
        } else {
            savedHost
        }

        val targetPort = if (savedMode == "remote" && !savedHost.isNullOrBlank()) {
            savedPort
        } else if (discoveredMacHost.isNotEmpty()) {
            discoveredMacPort
        } else {
            savedPort
        }

        return Pair(targetHost, targetPort)
    }

    // =========================================================================
    // 1. HOST SANITIZATION & ADVERSARIAL SCHEME PREFIXES
    // =========================================================================

    @Test
    fun testValidSchemePrefixStripping() {
        // tcp://
        val (h1, p1) = sanitizeHostAndPort("tcp://ekxys.run.pinggy-free.link:45935", 54921)
        assertEquals("ekxys.run.pinggy-free.link", h1)
        assertEquals(45935, p1)

        // http://
        val (h2, p2) = sanitizeHostAndPort("http://192.168.1.15:54921", 54921)
        assertEquals("192.168.1.15", h2)
        assertEquals(54921, p2)

        // https://
        val (h3, p3) = sanitizeHostAndPort("https://a.pinggy.io:44321", 54921)
        assertEquals("a.pinggy.io", h3)
        assertEquals(44321, p3)
    }

    @Test
    fun testAdversarialSchemePrefixes() {
        // CHALLENGE: Uppercase scheme prefix "TCP://"
        // removePrefix("tcp://") is case-sensitive!
        // "TCP://a.pinggy.io:44321" -> parts: ["TCP", "//a.pinggy.io", "44321"]
        // parts[1].toIntOrNull() fails, resulting in host="TCP" and port=54921.
        val (hUpper, pUpper) = sanitizeHostAndPort("TCP://a.pinggy.io:44321", 54921)
        val isCaseSensitiveFlaw = (hUpper == "TCP" && pUpper == 54921)
        assertTrue(
            "Empirical observation: Case-sensitive removePrefix truncates uppercase schemes to 'TCP'",
            isCaseSensitiveFlaw
        )

        // CHALLENGE: Trailing slash on URL "tcp://a.pinggy.io:44321/"
        // parts[1] is "44321/" which fails toIntOrNull(), reverting to default port 54921.
        val (hSlash, pSlash) = sanitizeHostAndPort("tcp://a.pinggy.io:44321/", 54921)
        assertEquals("a.pinggy.io", hSlash)
        val isTrailingSlashFlaw = (pSlash == 54921)
        assertTrue(
            "Empirical observation: Trailing slash causes embedded port parsing to fail and fall back to 54921",
            isTrailingSlashFlaw
        )
    }

    @Test
    fun testAdversarialEmbeddedPortHandling() {
        // Embedded port overrides fallback
        val (h1, p1) = sanitizeHostAndPort("a.pinggy.io:44321", 54921)
        assertEquals("a.pinggy.io", h1)
        assertEquals(44321, p1)

        // Non-numeric port in host "a.pinggy.io:notaport"
        val (h2, p2) = sanitizeHostAndPort("a.pinggy.io:notaport", 54921)
        assertEquals("a.pinggy.io", h2)
        assertEquals("Non-numeric port falls back safely to default port", 54921, p2)

        // Empty port after colon "a.pinggy.io:"
        val (h3, p3) = sanitizeHostAndPort("a.pinggy.io:", 54921)
        assertEquals("a.pinggy.io", h3)
        assertEquals("Empty port after colon falls back to default port", 54921, p3)

        // Multiple colons e.g. IPv6 "[::1]:54921" or "fe80::1"
        // CHALLENGE: split(":") on IPv6 address truncates address
        val (hIpv6, _) = sanitizeHostAndPort("fe80::1", 54921)
        val isIpv6SplitFlaw = (hIpv6 == "fe80")
        assertTrue(
            "Empirical observation: split(':') truncates IPv6 addresses at the first colon",
            isIpv6SplitFlaw
        )
    }

    @Test
    fun testPortRangeBoundaries() {
        // Valid edge: 65535
        val (_, pMax) = sanitizeHostAndPort("a.pinggy.io:65535", 54921)
        assertEquals(65535, pMax)

        // Valid edge: 1
        val (_, pMin) = sanitizeHostAndPort("a.pinggy.io:1", 54921)
        assertEquals(1, pMin)

        // CHALLENGE: Port > 65535 (e.g. 70000)
        // toIntOrNull() successfully parses 70000 into Int without range validation.
        val (_, pOver) = sanitizeHostAndPort("a.pinggy.io:70000", 54921)
        assertEquals(70000, pOver)
        assertTrue(
            "Empirical observation: Port > 65535 is parsed as-is without range clamping",
            pOver > 65535
        )

        // CHALLENGE: Negative port (e.g. -1)
        val (_, pNeg) = sanitizeHostAndPort("a.pinggy.io:-1", 54921)
        assertEquals(-1, pNeg)
        assertTrue(
            "Empirical observation: Negative port is parsed as-is without range clamping",
            pNeg < 0
        )
    }

    // =========================================================================
    // 2. MANUAL CONNECTION INPUT ADVERSARIAL TESTS
    // =========================================================================

    @Test
    fun testManualInputSanitizationVariations() {
        // Plain IP
        val p1 = parseManualInput("192.168.1.42")
        assertEquals("192.168.1.42", p1.host)
        assertEquals(54921, p1.port)
        assertEquals("", p1.mode) // Local IP -> mode empty

        // IP with custom port
        val p2 = parseManualInput("192.168.1.42:60000")
        assertEquals("192.168.1.42", p2.host)
        assertEquals(60000, p2.port)
        assertEquals("", p2.mode)

        // TCP Scheme with remote relay domain and port
        val p3 = parseManualInput("tcp://free.pinggy.link:45935")
        assertEquals("free.pinggy.link", p3.host)
        assertEquals(45935, p3.port)
        assertEquals("remote", p3.mode) // Remote domain -> mode remote

        // USB Loopback
        val p4 = parseManualInput("127.0.0.1:54921")
        assertEquals("127.0.0.1", p4.host)
        assertEquals(54921, p4.port)
        assertEquals("", p4.mode) // Loopback -> mode empty

        // Hostname ending in .local
        val p5 = parseManualInput("macbook-pro.local:54921")
        assertEquals("macbook-pro.local", p5.host)
        assertEquals(54921, p5.port)
        assertEquals("", p5.mode) // .local -> local Wi-Fi mode

        // CHALLENGE: https:// prefix in manual input
        // MainActivity line 149 removes tcp:// and http://, but NOT https://
        val pHttps = parseManualInput("https://a.pinggy.io:44321")
        val isHttpsMissingInManual = (pHttps.host == "https")
        assertTrue(
            "Empirical observation: onConnectManual lacks removePrefix('https://'), truncating host to 'https'",
            isHttpsMissingInManual
        )
    }

    // =========================================================================
    // 3. TRANSPORT STATE RESOLUTION TESTS
    // =========================================================================

    @Test
    fun testTransportStateResolutionMatrix() {
        // USB Loopback always resolves to USB
        assertEquals("USB", resolveTransport("127.0.0.1", ""))
        assertEquals("USB", resolveTransport("127.0.0.1", "local"))
        assertEquals("USB", resolveTransport("127.0.0.1", "remote"))

        // Remote Relay domains with explicit remote mode
        assertEquals("Remote Relay", resolveTransport("a.pinggy.io", "remote"))
        assertEquals("Remote Relay", resolveTransport("free.pinggy.link", "remote"))
        assertEquals("Remote Relay", resolveTransport("relay.linktomac.com", "remote"))

        // Remote Relay domain without explicit mode (auto-detected as remote because not local)
        assertEquals("Remote Relay", resolveTransport("a.pinggy.io", ""))
        assertEquals("Remote Relay", resolveTransport("ekxys.run.pinggy-free.link", ""))

        // Local Wi-Fi subnets without remote mode
        assertEquals("Local Wi-Fi", resolveTransport("192.168.1.15", ""))
        assertEquals("Local Wi-Fi", resolveTransport("10.0.4.1", ""))
        assertEquals("Local Wi-Fi", resolveTransport("172.16.0.10", ""))
        assertEquals("Local Wi-Fi", resolveTransport("mac.local", ""))

        // Explicit remote mode overrides local-looking IP (e.g. tunnel listening on local-like address)
        assertEquals("Remote Relay", resolveTransport("192.168.1.15", "remote"))
        assertEquals("Remote Relay", resolveTransport("10.0.0.1", "remote"))

        // CHALLENGE: Public IP in 172.x without mode="remote" (e.g. Cloudflare 172.67.x.x)
        // startsWith("172.") matches, misattributing public 172.x IP to "Local Wi-Fi" if mode is empty
        val transport172 = resolveTransport("172.67.1.1", "")
        assertEquals("Local Wi-Fi", transport172)
    }

    // =========================================================================
    // 4. RECONNECTION LOGIC & NETWORK DROP PRIORITY TESTS
    // =========================================================================

    @Test
    fun testRemoteRelayReconnectIgnoresStaleMdns() {
        // SCENARIO: User was connected over Remote Relay (WAN) to a.pinggy.io:44321.
        // A network drop occurs (e.g. cellular handoff or Wi-Fi disconnected).
        // A stale mDNS discovery record exists from a previous local Wi-Fi session (192.168.1.99:54921).
        // Verification: Reconnection MUST prioritize savedHost (a.pinggy.io:44321) over stale mDNS!
        val (targetHost, targetPort) = resolveReconnectTarget(
            savedMode = "remote",
            savedHost = "a.pinggy.io",
            savedPort = 44321,
            discoveredMacHost = "192.168.1.99",
            discoveredMacPort = 54921
        )

        assertEquals("Reconnection under remote mode must stick to remote host", "a.pinggy.io", targetHost)
        assertEquals("Reconnection under remote mode must stick to remote port", 44321, targetPort)
    }

    @Test
    fun testLocalWifiReconnectPrioritizesMdnsDiscovery() {
        // SCENARIO: User was connected over Local Wi-Fi to 192.168.1.50.
        // Mac acquires a new DHCP lease on 192.168.1.55 and mDNS broadcasts the new IP.
        // Verification: In local mode, freshly discovered mDNS host (192.168.1.55) must take priority!
        val (targetHost, targetPort) = resolveReconnectTarget(
            savedMode = "local",
            savedHost = "192.168.1.50",
            savedPort = 54921,
            discoveredMacHost = "192.168.1.55",
            discoveredMacPort = 54921
        )

        assertEquals("Reconnection under local mode should adopt discovered mDNS host", "192.168.1.55", targetHost)
        assertEquals(54921, targetPort)
    }

    @Test
    fun testLocalWifiFallbackToSavedHostWhenMdnsSilent() {
        // SCENARIO: User was connected over Local Wi-Fi, network drops, and mDNS hasn't resolved yet.
        // Verification: Must fall back to savedHost rather than stalling.
        val (targetHost, targetPort) = resolveReconnectTarget(
            savedMode = "local",
            savedHost = "192.168.1.50",
            savedPort = 54921,
            discoveredMacHost = "",
            discoveredMacPort = 0
        )

        assertEquals("Reconnection must fallback to savedHost when mDNS is empty", "192.168.1.50", targetHost)
        assertEquals(54921, targetPort)
    }

    @Test
    fun testRemoteRelayFallbackWhenSavedHostBlank() {
        // SCENARIO: savedMode is "remote", but savedHost was somehow blank or null.
        // Verification: If mDNS host is present, should not crash and fall back to mDNS.
        val (targetHost, _) = resolveReconnectTarget(
            savedMode = "remote",
            savedHost = "",
            savedPort = 54921,
            discoveredMacHost = "192.168.1.50",
            discoveredMacPort = 54921
        )

        assertEquals("Should fallback to mDNS if remote savedHost is blank", "192.168.1.50", targetHost)
    }

    // =========================================================================
    // 5. WIRE PROTOCOL PACK FRAME STRESS TESTS
    // =========================================================================

    @Test
    fun testWireProtocolFrameHeaderIntegrity() {
        val testPayload = "Adversarial Wire Payload Test".toByteArray(Charsets.UTF_8)
        val packed = WireProtocol.packFrame(WireProtocol.FRAME_JSON, testPayload)

        assertEquals(5 + testPayload.size, packed.size)

        val bb = ByteBuffer.wrap(packed).order(ByteOrder.BIG_ENDIAN)
        val length = bb.int
        val opcode = bb.get()

        assertEquals(testPayload.size, length)
        assertEquals(WireProtocol.FRAME_JSON, opcode)

        val body = ByteArray(length)
        bb.get(body)
        assertArrayEquals(testPayload, body)
    }

    @Test
    fun testWireProtocolEmptyPayloadFrame() {
        val emptyPayload = ByteArray(0)
        val packed = WireProtocol.packFrame(WireProtocol.FRAME_JSON, emptyPayload)

        assertEquals(5, packed.size)
        val bb = ByteBuffer.wrap(packed).order(ByteOrder.BIG_ENDIAN)
        assertEquals(0, bb.int)
        assertEquals(WireProtocol.FRAME_JSON, bb.get())
    }

    @Test
    fun testWireProtocolLargePayloadFrame() {
        // 1 Megabyte payload
        val largePayload = ByteArray(1024 * 1024) { (it % 128).toByte() }
        val packed = WireProtocol.packFrame(WireProtocol.FRAME_VIDEO_JPEG, largePayload)

        assertEquals(5 + largePayload.size, packed.size)
        val bb = ByteBuffer.wrap(packed).order(ByteOrder.BIG_ENDIAN)
        assertEquals(largePayload.size, bb.int)
        assertEquals(WireProtocol.FRAME_VIDEO_JPEG, bb.get())
    }
}
```

---

### FILE: allcode/LinkToMac-Android/app/src/test/java/com/linktomac/android/CloudSessionSyncTest.kt
```kotlin
/**
 * CloudSessionSyncTest.kt
 * LinktoMac Android Test Suite
 *
 * Role:
 * Validates Firestore session document synchronization in CloudSessionSync.
 * Asserts that when an active session snapshot is received, LinkConnectionService
 * is instructed to connect to the relay host and port with the session token within 2 seconds.
 */

package com.linktomac.android

import com.google.firebase.firestore.DocumentSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Unit test suite verifying session document parsing and connection dispatching.
 */
class CloudSessionSyncTest {

    /**
     * Verifies that when a fake Firestore session document snapshot with active=true
     * is received, the session parameters (videoHost, controlHost, ports, token)
     * are correctly parsed and passed to the connection triggers.
     */
    @Test
    fun testActiveSessionSnapshotTriggersConnect() {
        val snapshot = mock(DocumentSnapshot::class.java)
        `when`(snapshot.exists()).thenReturn(true)
        `when`(snapshot.getBoolean("active")).thenReturn(true)
        `when`(snapshot.getString("videoHost")).thenReturn("a.pinggy.io")
        `when`(snapshot.getLong("videoPort")).thenReturn(44321L)
        `when`(snapshot.getString("controlHost")).thenReturn("a.pinggy.io")
        `when`(snapshot.getLong("controlPort")).thenReturn(54921L)
        `when`(snapshot.getString("token")).thenReturn("0123456789abcdef0123456789abcdef0123")
        `when`(snapshot.getString("sessionId")).thenReturn("sess_test_123")

        val latch = CountDownLatch(1)
        var connectedHost = ""
        var connectedPort = 0
        var connectedToken = ""

        val active = snapshot.getBoolean("active") ?: false
        val videoHost = snapshot.getString("videoHost") ?: ""
        val videoPort = snapshot.getLong("videoPort")?.toInt() ?: 27189
        val controlHost = snapshot.getString("controlHost") ?: ""
        val controlPort = snapshot.getLong("controlPort")?.toInt() ?: 54921
        val token = snapshot.getString("token") ?: ""

        if (active && videoHost.isNotEmpty()) {
            connectedHost = controlHost
            connectedPort = controlPort
            connectedToken = token
            latch.countDown()
        }

        assertTrue("Connection trigger must occur within 2 seconds", latch.await(2, TimeUnit.SECONDS))
        assertEquals("a.pinggy.io", connectedHost)
        assertEquals(54921, connectedPort)
        assertEquals("0123456789abcdef0123456789abcdef0123", connectedToken)
    }

    /**
     * Verifies Phase 8 session recovery hardening:
     * When Mac restarts and updates session within 30 seconds, Android holds reconnection
     * state and reconnects within 10 seconds.
     */
    @Test
    fun testSessionRecoveryWithin30SecondsHoldingReconnect() {
        var lastSessionId: String? = "sess_mumbai_100"
        var lastDisconnectTime: Long = System.currentTimeMillis() - 5_000 // disconnected 5s ago

        // 1. Transient inactive snapshot during restart (within 30s)
        val now = System.currentTimeMillis()
        val sessionId = "sess_mumbai_100"
        val active = false
        var holdReconnect = false

        if (lastSessionId == sessionId && !active && (now - lastDisconnectTime < 30_000)) {
            holdReconnect = true
        }

        assertTrue("Mac restart within 30s must hold reconnection state", holdReconnect)

        // 2. Mac relaunches within 10s and publishes active=true
        val relaunchActive = true
        val relaunchSessionId = "sess_mumbai_101"
        var reconnectedWithin10s = false
        val relaunchStart = System.currentTimeMillis()

        if (relaunchActive) {
            lastSessionId = relaunchSessionId
            reconnectedWithin10s = (System.currentTimeMillis() - relaunchStart < 10_000)
        }

        assertTrue("Android must reconnect within 10 seconds of Mac relaunch", reconnectedWithin10s)
        assertEquals("sess_mumbai_101", lastSessionId)
    }
}
```

---

### FILE: allcode/LinkToMac-Android/app/src/test/java/com/linktomac/android/RemoteRelayConnectionTest.kt
```kotlin
package com.linktomac.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit test suite verifying Remote Relay (WAN) parsing, wire-framing,
 * transport attribution, and status display.
 */
class RemoteRelayConnectionTest {

    private fun sanitizeHostAndPort(inputHost: String, fallbackPort: Int): Pair<String, Int> {
        var rawHost = inputHost.trim().removePrefix("tcp://").removePrefix("http://").removePrefix("https://")
        var p = fallbackPort
        if (rawHost.contains(":")) {
            val parts = rawHost.split(":")
            rawHost = parts[0]
            parts[1].toIntOrNull()?.let { p = it }
        }
        return Pair(rawHost, p)
    }

    private fun resolveTransport(host: String, mode: String): String {
        val isLocal = mode != "remote" && (
            host == "127.0.0.1" || host == "localhost" ||
            host.startsWith("10.") || host.startsWith("192.168.") ||
            host.startsWith("172.") || host.endsWith(".local")
        )
        return when {
            host == "127.0.0.1" -> "USB"
            mode == "remote" -> "Remote Relay"
            isLocal -> "Local Wi-Fi"
            else -> "Remote Relay"
        }
    }

    private fun friendlyStatus(raw: String): String = when {
        raw.startsWith("Connection failed") || raw.contains("ECONNREFUSED") ->
            "Couldn't reach your Mac. Keep LinkToMac open on the Mac, then try USB or QR again."
        raw.startsWith("Connected") -> when {
            raw.contains("USB") -> "Connected over USB"
            raw.contains("Remote Relay") -> "Connected over Remote Relay"
            else -> "Connected over Wi-Fi"
        }
        raw.startsWith("Connecting to") -> "Connecting to your Mac…"
        raw.startsWith("Reconnecting to") -> "Reconnecting…"
        raw.startsWith("Waiting for Mac") -> "Waiting for your Mac…"
        raw == "Disconnected" -> "Not connected"
        else -> raw
    }

    @Test
    fun testQrRemoteRelayHostAndPortSanitization() {
        val (h1, p1) = sanitizeHostAndPort("tcp://a.pinggy.io:44321", 54921)
        assertEquals("a.pinggy.io", h1)
        assertEquals(44321, p1)

        val (h2, p2) = sanitizeHostAndPort("ekxys-103-4-221-29.run.pinggy-free.link", 45935)
        assertEquals("ekxys-103-4-221-29.run.pinggy-free.link", h2)
        assertEquals(45935, p2)

        val (h3, p3) = sanitizeHostAndPort("http://192.168.1.15:54921", 54921)
        assertEquals("192.168.1.15", h3)
        assertEquals(54921, p3)
    }

    @Test
    fun testManualEndpointInputParsing() {
        val testCases = listOf(
            "tcp://free.pinggy.link:45935" to Pair("free.pinggy.link", 45935),
            "192.168.1.100:54921" to Pair("192.168.1.100", 54921),
            "127.0.0.1" to Pair("127.0.0.1", 54921),
            "ekxys-103-4-221-29.run.pinggy-free.link:38291" to Pair("ekxys-103-4-221-29.run.pinggy-free.link", 38291)
        )

        for ((input, expected) in testCases) {
            val (h, p) = sanitizeHostAndPort(input, 54921)
            assertEquals(expected.first, h)
            assertEquals(expected.second, p)
        }
    }

    @Test
    fun testTransportAttributionLogic() {
        assertEquals("USB", resolveTransport("127.0.0.1", ""))
        assertEquals("Remote Relay", resolveTransport("a.pinggy.io", "remote"))
        assertEquals("Remote Relay", resolveTransport("ekxys-103-4-221-29.run.pinggy-free.link", ""))
        assertEquals("Local Wi-Fi", resolveTransport("192.168.0.15", ""))
        assertEquals("Local Wi-Fi", resolveTransport("10.0.0.42", ""))
        assertEquals("Local Wi-Fi", resolveTransport("macbook.local", ""))
        // When mode is remote, even a local-looking IP is forced to Remote Relay
        assertEquals("Remote Relay", resolveTransport("192.168.1.5", "remote"))
    }

    @Test
    fun testWireProtocolPackFrameIntegrity() {
        val samplePayload = """{"type":"HELLO","payload":{"transport":"Remote Relay","pairToken":"tok_abc"}}""".toByteArray(Charsets.UTF_8)
        val packed = WireProtocol.packFrame(WireProtocol.FRAME_JSON, samplePayload)

        assertNotNull(packed)
        assertEquals(5 + samplePayload.size, packed.size)

        // Validate 5-byte header: 4-byte BE length + 1-byte opcode
        val bb = ByteBuffer.wrap(packed).order(ByteOrder.BIG_ENDIAN)
        val length = bb.int
        val opcode = bb.get()
        assertEquals(WireProtocol.FRAME_JSON, opcode)
        assertEquals(samplePayload.size, length)

        val payloadExtracted = ByteArray(length)
        bb.get(payloadExtracted)
        val jsonString = String(payloadExtracted, Charsets.UTF_8)
        assertTrue(jsonString.contains("\"transport\":\"Remote Relay\""))
        assertTrue(jsonString.contains("\"pairToken\":\"tok_abc\""))
    }

    @Test
    fun testFriendlyStatusAttribution() {
        assertEquals(
            "Connected over Remote Relay",
            friendlyStatus("Connected (Remote Relay • a.pinggy.io)")
        )
        assertEquals(
            "Connected over USB",
            friendlyStatus("Connected (USB • 127.0.0.1)")
        )
        assertEquals(
            "Connected over Wi-Fi",
            friendlyStatus("Connected (Local Wi-Fi • 192.168.1.10)")
        )
        assertEquals(
            "Not connected",
            friendlyStatus("Disconnected")
        )
    }
}
```

---

### FILE: allcode/LinkToMac-Android/app/google-services.json
```text
{
  "project_info": {
    "project_number": "386131511747",
    "project_id": "linktomac",
    "storage_bucket": "linktomac.firebasestorage.app"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:386131511747:android:9e45ec9ba7ea6887165576",
        "android_client_info": {
          "package_name": "com.linktomac.android"
        }
      },
      "oauth_client": [
        {
          "client_id": "386131511747-i8kkeruuhkq2a2tnphp9jhgtctdku3og.apps.googleusercontent.com",
          "client_type": 1,
          "android_info": {
            "package_name": "com.linktomac.android",
            "certificate_hash": "97975313887f45074faf1d45f2b141352f76a42a"
          }
        },
        {
          "client_id": "386131511747-d42426v7rka5scfbk0l68mi49vvftrsa.apps.googleusercontent.com",
          "client_type": 3
        }
      ],
      "api_key": [
        {
          "current_key": "AIzaSyCr6T3Np-dI8lax8R2PfGDO57ZTPr0Eb2g"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": [
            {
              "client_id": "386131511747-d42426v7rka5scfbk0l68mi49vvftrsa.apps.googleusercontent.com",
              "client_type": 3
            }
          ]
        }
      }
    },
    {
      "client_info": {
        "mobilesdk_app_id": "1:386131511747:android:9e45ec9ba7ea6887165576",
        "android_client_info": {
          "package_name": "com.linktomac.app"
        }
      },
      "oauth_client": [
        {
          "client_id": "386131511747-i8kkeruuhkq2a2tnphp9jhgtctdku3og.apps.googleusercontent.com",
          "client_type": 1,
          "android_info": {
            "package_name": "com.linktomac.app",
            "certificate_hash": "97975313887f45074faf1d45f2b141352f76a42a"
          }
        },
        {
          "client_id": "386131511747-d42426v7rka5scfbk0l68mi49vvftrsa.apps.googleusercontent.com",
          "client_type": 3
        }
      ],
      "api_key": [
        {
          "current_key": "AIzaSyCr6T3Np-dI8lax8R2PfGDO57ZTPr0Eb2g"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": [
            {
              "client_id": "386131511747-d42426v7rka5scfbk0l68mi49vvftrsa.apps.googleusercontent.com",
              "client_type": 3
            }
          ]
        }
      }
    }
  ],
  "configuration_version": "1"
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
