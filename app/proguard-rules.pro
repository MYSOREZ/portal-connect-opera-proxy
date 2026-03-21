# ProGuard rules for OperaProxy

# Сохраняем классы приложения, чтобы избежать проблем с Reflection/JNI
-keep class com.example.operaproxy.** { *; }

# Сохраняем компоненты Android (Activity, Service и т.д.), хотя AAPT2 обычно делает это сам
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.service.quicksettings.TileService
-keep public class * extends android.net.VpnService

# Оставляем нативные методы, если они есть
-keepclasseswithmembernames class * {
    native <methods>;
}

# callback из native-lib: ProxyVpnService.onTun2ProxyLog(String)
-keepclassmembers class com.example.operaproxy.ProxyVpnService {
    public void onTun2ProxyLog(java.lang.String);
}

-dontoptimize

# Мне прятать нечего
-dontobfuscate
