# Compose i AndroidX dostarczają własne reguły konsumenckie.
-keepattributes *Annotation*, Signature, InnerClasses

# PdfBox-Android ładuje zasoby (czcionki, mapy kodowań) i klasy filtrów po nazwach.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.**
-dontwarn java.awt.**
