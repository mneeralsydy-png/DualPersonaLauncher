# Add project specific ProGuard rules here.

# Keep model classes for Room
-keep class com.dualpersona.launcher.data.entity.** { *; }

# Keep encryption related classes
-keep class com.dualpersona.launcher.security.** { *; }

# Keep BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# Lottie
-keep class com.airbnb.lottie.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** { **[] $VALUES; public *; }
