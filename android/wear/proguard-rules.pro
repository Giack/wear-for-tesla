# Protobuf-lite reflects over generated message fields by name.
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }

# diag.log names exceptions by javaClass.simpleName; keep them readable in builds R8 renames (compact).
-keepnames class * extends java.lang.Throwable
