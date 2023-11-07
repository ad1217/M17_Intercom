## Building

1. Root your phone with [Magisk](https://topjohnwu.github.io/Magisk/install.html)
2. Fetch `framework.jar` and `android.hidl.base-V1.0-java.jar`

   This app needs system/hidden functions, and this was the easiest/simplest way I could find to make them available.
   If you know a better way, please tell me!

   ```bash
   mkdir app/system_libs
   cd app/system_libs

   # retrieve from running phone
   adb pull /system/framework/framework.jar .
   adb pull /system/framework/android.hidl.base-V1.0-java.jar .

   # convert to .class files, ignoring actual code (basically "headers" only)
   dex2jar framework.jar -nc
   dex2jar android.hidl.base-V1.0-java.jar -nc

   # (optional) delete the original jar files
   rm framework.jar android.hidl.base-V1.0-java.jar
   ```

3. Build and install with Android Studio (probably easier) or `./gradlew installDebug`
4. Build the Magisk module with `./gradlew magisk_module`, and install `build/open_aguiExtModule_permissions.zip` with Magisk on the phone
