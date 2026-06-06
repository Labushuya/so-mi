# core-llm-llama keeps the JNI bridge intact across R8 — both the upstream
# com.arm.aichat package and our io.somi.llm.llama wrapper need to retain
# their `external fun` declarations so System.loadLibrary can resolve them.

-keep class com.arm.aichat.** { *; }
-keep class io.somi.llm.llama.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
