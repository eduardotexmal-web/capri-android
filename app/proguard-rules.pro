# The page calls these by name (HostBridge.kt)
-keepclassmembers class io.github.emmanuelquintana.capri.HostBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Open source: class and method names stay readable in the APK (no renaming), so anyone (and any
# antivirus) can see what it does. Unused code is still removed.
-dontobfuscate
