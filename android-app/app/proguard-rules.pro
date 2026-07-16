# ProGuard rules for LinktoMac Companion

# Keep the MainActivity entry point
-keep class com.linktomac.companion.MainActivity {
    public protected *;
}

# Keep MirrorService
-keep class com.linktomac.companion.MirrorService {
    public protected *;
}

# Keep ScreenEncoder
-keep class com.linktomac.companion.ScreenEncoder {
    public protected *;
}

# Keep NSD related classes
-keep class com.linktomac.companion.** { *; }

# Keep Android support library
-keep class androidx.** { *; }
-keep class com.google.android.material.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep parcelable creators
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# Keep setters/getters
-keepclassmembers class * {
    public void set*(***);
    public *** get*();
}