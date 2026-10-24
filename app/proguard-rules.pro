# MediaPipe uses JNI and reflection on its proto classes.
-keep class com.google.mediapipe.** { *; }
# Protobuf-lite reads generated message fields by name.
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
# Flogger (MediaPipe's logger) finds its caller by scanning the stack for its own class
# names. Renamed, it throws "no caller found on the stack" and the model never loads.
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn javax.lang.model.**
-dontwarn com.google.auto.value.**

# MediaPipe uploads usage stats through Google's data-transport library, which schedules
# background jobs. Strip every stats call so nothing is queued. The library itself has to
# stay, because MediaPipe initialises it directly.
-assumenosideeffects interface com.google.mediapipe.tasks.core.logging.TasksStatsLogger {
    void log*(...);
    void record*(...);
}
