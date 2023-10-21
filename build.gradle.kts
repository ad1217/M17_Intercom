// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.1.2" apply false
    id("com.android.library") version "8.1.2" apply false
    id("org.jetbrains.kotlin.android") version "1.8.20" apply false
}

tasks.register<Zip>("magisk_module") {
    from(file("magisk_module"))
    archiveFileName = "open_aguiExtModule_permissions.zip"
    destinationDirectory = file(layout.buildDirectory)
}
