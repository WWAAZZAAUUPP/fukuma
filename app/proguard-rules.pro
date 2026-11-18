-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

-repackageclasses 'com.android.core.internal'
-allowaccessmodification

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.google.android.gms.** { *; }
-keep class androidx.** { *; }
-keep class org.json.** { *; }

-keepclassmembers class com.example.telemetry.data.** {
    *;
}
