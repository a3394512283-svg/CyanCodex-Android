# CyanCodex 主 App R8 混淆规则
# 目标: 业务代码全部混淆, 仅保留反射/Gson/Retrofit/插件接口所需的类与成员

# --- Retrofit 接口 (代理反射) ---
-keep interface com.canglan.cyancodex.data.ApiService { *; }

# --- Gson 模型 (字段名反射; 无 @SerializedName 的字段必须保留原名) ---
-keep class com.canglan.cyancodex.data.** { *; }

# --- 插件接口: 外部 JAR 实现本 App 的接口, 方法签名必须保持 ---
-keep interface com.canglan.cyancodex.plugin.CyanPlugin { *; }

# --- 插件管理器: 通过类名反射实例化, 但实现类在外部 JAR, 仅保留接口调用 ---
-keep class com.canglan.cyancodex.plugin.PluginManager { *; }

# --- 服务器图片/JSON 中按字段名解析的模型 ---
-keep class com.canglan.cyancodex.data.ApiResp { *; }

# --- OkHttp/Retrofit 内部反射 (官方规则兜底) ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*

# --- Retrofit 官方 keep 规则: suspend 函数返回类型经 Continuation 泛型传递 ---
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# --- Gson TypeToken 泛型 (匿名子类的泛型签名必须保留, 否则反射取类型失败) ---
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.Gson { *; }

# --- Compose: 保持可组合性 (R8 对 Compose 的官方建议) ---
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
-dontwarn androidx.compose.**

# --- 悬浮窗/输入法服务 (系统通过类名/Manifest 引用) ---
-keep class com.canglan.cyancodex.ime.CyanCodexIme { *; }
-keep class com.canglan.cyancodex.overlay.FloatWindowService { *; }

# --- 避免 enum 被精简破坏 when 表达式 ---
-keepclassmembers enum * { *; }
