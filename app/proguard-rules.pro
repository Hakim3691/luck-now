# Chaquopy: the runtime loads Python classes reflectively.
-keep class com.chaquo.python.** { *; }
-keep class org.json.** { *; }
-dontwarn com.chaquo.python.**

# llama.cpp JNI bridge.
-keep class com.lucknow.llm.LlamaNative { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# JavaScript bridge is called from WebView.
-keepclassmembers class com.lucknow.scrape.WebViewInterceptionDriver$JsBridge {
    public *;
}
