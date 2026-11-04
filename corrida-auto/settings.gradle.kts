pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "corrida-auto"
include(":core")
// O módulo Android só entra quando há um Android SDK configurado
// (local.properties com sdk.dir ou ANDROID_HOME). Assim o núcleo
// pode ser testado em qualquer máquina com: gradle :core:test
val temSdk = file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") } ||
    System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null
if (temSdk) include(":app")
