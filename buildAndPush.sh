export ANDROID_HOME=$HOME/Android/Sdk
set -e
./gradlew app:assembleRelease lint
adb remount
adb push app/build/outputs/apk/app-release-unsigned.apk /system/framework/XposedBridge.jar
adb reboot

