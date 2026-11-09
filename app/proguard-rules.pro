# 크래시 스택 트레이스에서 파일명과 줄 번호를 읽을 수 있게 남긴다(mapping.txt와 함께 해독).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization (라이브러리 README의 앱용 권장 규칙).
# 백업 파일(BackupData 등)과 Gemini 요청·응답이 이 직렬화기를 쓴다.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.chatlearn.**$$serializer { *; }
-keepclassmembers class com.chatlearn.** { *** Companion; }
-keepclasseswithmembers class com.chatlearn.** { kotlinx.serialization.KSerializer serializer(...); }

# Tink(security-crypto의 암호화 저장소)가 참조하는 컴파일 전용 어노테이션. 런타임에는 필요 없다.
# 없으면 R8이 "Missing class com.google.errorprone.annotations.*"로 빌드를 멈춘다(CI에서 확인).
-dontwarn com.google.errorprone.annotations.**
