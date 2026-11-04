plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android")
}

// O GitHub Actions passa -PversionCode=<número do build>; cada build vira a release "v<número>".
val codigoVersao = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val repo = "Chatosss/Chatosss"
val branchPadroes = (findProperty("branchPadroes") as String?) ?: "claude/ride-acceptance-manager-u73mzf"

android {
    namespace = "br.com.autocorrida"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.autocorrida"
        minSdk = 26
        targetSdk = 35
        versionCode = codigoVersao
        versionName = "0.$codigoVersao"
        buildConfigField("String", "URL_RELEASES", "\"https://api.github.com/repos/$repo/releases/latest\"")
        buildConfigField(
            "String", "URL_PADROES",
            "\"https://raw.githubusercontent.com/$repo/$branchPadroes/corrida-auto/padroes-remotos.json\"",
        )
    }

    // O Android só instala uma atualização se ela for assinada pela MESMA chave.
    // A chave fica nos secrets do GitHub (ASSINATURA_JKS_BASE64, ASSINATURA_SENHA, ASSINATURA_ALIAS),
    // nunca no repositório. Sem os secrets, o build usa a chave de debug (só para teste).
    val jksBase64 = System.getenv("ASSINATURA_JKS_BASE64")
    val temChave = !jksBase64.isNullOrBlank()
    signingConfigs {
        if (temChave) {
            create("fixa") {
                val f = layout.buildDirectory.file("assinatura.jks").get().asFile
                f.parentFile.mkdirs()
                f.writeBytes(java.util.Base64.getMimeDecoder().decode(jksBase64))
                storeFile = f
                storePassword = System.getenv("ASSINATURA_SENHA")
                keyAlias = System.getenv("ASSINATURA_ALIAS")
                keyPassword = System.getenv("ASSINATURA_SENHA")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (temChave) signingConfigs.getByName("fixa") else signingConfigs.getByName("debug")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.material:material:1.12.0")
}
