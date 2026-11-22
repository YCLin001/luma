# Luma Lite 保持规则
# 反射调用的都是系统类（ActivityOptions / BluetoothDevice），不需要额外 keep。
# 这里只兜住自己的入口，避免 R8 误删。
-keep class com.luma.lite.MainActivity { *; }
-keep class com.luma.lite.LumaNotifService { *; }
