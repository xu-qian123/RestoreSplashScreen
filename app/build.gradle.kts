plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.gswxxn.restoresplashscreen"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gswxxn.restoresplashscreen"
        minSdk = 37
        targetSdk = 37
        versionCode = 4000
        versionName = "4.0"
    }

    packaging.resources {
        excludes += setOf(
            "META-INF/*.version",
            "META-INF/*.kotlin_module",
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/NOTICE*",
            "META-INF/LICENSE*"
        )
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // 签名: 优先读环境变量 (CI), 其次读 local.properties
    val localProps = java.util.Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun prop(name: String): String =
        System.getenv(name) ?: localProps.getProperty(name.lowercase().replace('_', '.'), "")
    val keystorePath = prop("KEYSTORE_PATH")
    val keystorePass = prop("KEYSTORE_PASS")
    val keyAlias = prop("KEY_ALIAS")
    val keyPassword = prop("KEY_PASSWORD")
    val isKeyStoreAvailable = keystorePath.isNotBlank() && keystorePass.isNotBlank() &&
        keyAlias.isNotBlank() && keyPassword.isNotBlank()
    if (isKeyStoreAvailable) {
        signingConfigs {
            create("universal") {
                storeFile = file(keystorePath)
                storePassword = keystorePass
                keyAlias = keyAlias
                keyPassword = keyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        all { if (isKeyStoreAvailable) signingConfig = signingConfigs.getByName("universal") }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    flavorDimensionList.add("tier")
    productFlavors {
        create("CI") {
            dimension = "tier"
            versionCode = 4001
            versionName = "4.0-CI.${getGitHeadRefsSuffix(rootProject)}"
        }
        create("app") {
            dimension = "tier"
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    applicationVariants.all {
        val buildType = buildType.name
        outputs.all {
            if (this is com.android.build.gradle.internal.api.ApkVariantOutputImpl) {
                this.outputFileName = "RestoreSplashScreen_${versionName}${if (buildType == "debug") "_debug" else ""}.apk"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.majorVersion
    }
}

dependencies {
    implementation(project(":blockmiui"))
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

tasks.register("getVersionCode") {
    println("4000-4.0")
}

/**
 * from [MiuiHomeR](https://github.com/qqlittleice/MiuiHome_R/blob/main/app/build.gradle.kts)
 * 用于获取 git commit id
 */
fun getGitHeadRefsSuffix(project: Project): String {
    // .git/HEAD描述当前目录所指向的分支信息，内容示例："ref: refs/heads/master\n"
    val headFile = File(project.rootProject.projectDir, ".git" + File.separator + "HEAD")
    if (headFile.exists()) {
        val string: String = headFile.readText(Charsets.UTF_8)
        val string1 = string.replace(Regex("""ref:|\s"""), "")
        val result = if (string1.isNotBlank() && string1.contains('/')) {
            val refFilePath = ".git" + File.separator + string1
            // 根据HEAD读取当前指向的hash值，路径示例为：".git/refs/heads/master"
            val refFile = File(project.rootProject.projectDir, refFilePath)
            // 索引文件内容为hash值+"\n"，
            // 示例："90312cd9157587d11779ed7be776e3220050b308\n"
            refFile.readText(Charsets.UTF_8).replace(Regex("""\s"""), "").subSequence(0, 7)
        } else {
            string.take(7)
        }
        println("commit_id: $result")
        return result.toString()
    } else {
        println("WARN: .git/HEAD does NOT exist")
        return ""
    }
}
