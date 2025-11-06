# ProGuard rules for OSCSensorController

# Disable all optimizations to avoid removing AWT class references that would cause ClassNotFoundError
-dontoptimize

# Keep all OSC library classes
-keep class com.illposed.osc.** { *; }
-keep interface com.illposed.osc.** { *; }

# Keep our application classes
-keep class com.example.oscsensorcontroller.** { *; }
-keepclassmembers class com.example.oscsensorcontroller.** { *; }

# Don't warn about missing AWT classes
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn org.apache.log4j.**
