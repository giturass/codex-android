# Project-specific R8 rules belong here
-keep class * extends androidx.room3.RoomDatabase { <init>(); }

# JSch 通过配置中的类名动态加载认证、密钥交换和 JCE 算法实现
-keep,allowoptimization class com.jcraft.jsch.UserAuthNone { *; }
-keep,allowoptimization class com.jcraft.jsch.UserAuthPassword { *; }
-keep,allowoptimization class com.jcraft.jsch.UserAuthPublicKey { *; }
-keep,allowoptimization class com.jcraft.jsch.DH** { *; }
-keep,allowoptimization class com.jcraft.jsch.jce.** { *; }
-keep,allowoptimization class com.jcraft.jsch.jbcrypt.** { *; }
