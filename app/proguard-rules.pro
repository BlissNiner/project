# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# 高德地图 SDK 混淆规则
-keep class com.amap.api.**{*;}
-keep class com.autonavi.**{*;}
-keep class com.loc.**{*;}
-keep class com.amap.api.location.**{*;}
-dontwarn com.amap.api.**
-dontwarn com.autonavi.**

# OkHttp platform used only on JVM and when Conscrypt and other security providers are available.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# 高德SDK混淆补充
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
-keep class com.amap.api.maps.**{*;}
-keep class com.amap.api.services.**{*;}
-keep class com.amap.api.location.**{*;}

# 项目核心类不混淆（避免反射/序列化问题）
-keep class com.zcshou.gogogo.**{*;}
-keep class com.zcshou.service.**{*;}
-keep class com.zcshou.utils.**{*;}
-keep class com.zcshou.database.**{*;}
