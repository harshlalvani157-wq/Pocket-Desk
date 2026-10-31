# PocketDesk ProGuard Rules
-keep class com.pocketdesk.** { *; }
-keep class com.pocketdesk.service.LinuxBridgeService { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
