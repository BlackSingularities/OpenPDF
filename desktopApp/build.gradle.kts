import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val composeVersion = "1.10.3"
// Wersja z pliku VERSION albo z parametru -PversionName (w CI z tagu).
val appVersion: String = (findProperty("versionName") as String?)
    ?: rootProject.file("VERSION").readText().trim()

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.apache.pdfbox:pdfbox:3.0.5")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
}

// Wersja dostępna w programie (Ustawienia → O programie, sprawdzanie aktualizacji).
val writeVersion by tasks.registering {
    val out = layout.buildDirectory.dir("generated/version")
    val v = appVersion
    inputs.property("version", v)
    outputs.dir(out)
    doLast {
        val f = out.get().file("openpdf-version.txt").asFile
        f.parentFile.mkdirs()
        f.writeText(v)
    }
}
sourceSets.main { resources.srcDir(writeVersion) }

compose.desktop {
    application {
        mainClass = "com.blacksingularities.openpdf.desktop.MainKt"
        jvmArgs += listOf("-Xss4m", "-XX:+UseG1GC", "-Dsun.java2d.d3d=false", "-Dfile.encoding=UTF-8")
        nativeDistributions {
            // Jedynym plikiem wydania jest instalator Inno Setup (installer/OpenDocument.iss) zbudowany z tego obrazu aplikacji.
            targetFormats(TargetFormat.Exe)
            packageName = "OpenDocument"
            packageVersion = appVersion.substringBefore('-')
            description = "OpenDocument – przeglądarka dokumentów"
            vendor = "redzikowski.com"
            copyright = "© redzikowski.com"
            modules("java.desktop", "java.prefs", "java.logging", "java.naming", "java.net.http", "jdk.crypto.ec", "jdk.unsupported", "java.xml")
            windows {
                iconFile.set(project.file("installer/icon.ico"))
                upgradeUuid = "8B0E3C52-6B7B-4D71-9F59-3C1A0F2D7E41"
            }
        }
        buildTypes.release.proguard { isEnabled.set(false) }
    }
}

