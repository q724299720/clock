# MySQL Connector / JDBC 大量反射，需保留
-keep class com.mysql.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.mysql.**
-dontwarn java.sql.**
-dontwarn javax.**
-dontwarn org.slf4j.**

# HikariCP
-keep class com.zaxxer.hikari.** { *; }
-dontwarn com.zaxxer.hikari.**

# Room 实体
-keep class com.smartclock.data.local.** { *; }

# jBCrypt
-keep class org.mindrot.jbcrypt.** { *; }

# 农历库
-keep class com.nlf.calendar.** { *; }
