# CyanCodex 审核员 App R8 混淆规则
# 业务代码全混淆, 仅保留反射/系统组件所需

# --- Activity/Service 等系统组件: Manifest 按类名引用, 必须保留 ---
-keep class com.canglan.cyancodexreviewer.ui.MainActivity { *; }

# --- Retrofit 接口 + Gson 模型 ---
-keep interface com.canglan.cyancodexreviewer.data.ReviewerApi { *; }
-keep class com.canglan.cyancodexreviewer.data.** { *; }

# --- Retrofit 官方 keep 规则 (suspend 泛型) ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# --- Gson TypeToken ---
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.Gson { *; }

# --- Compose ---
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
-dontwarn androidx.compose.**
