import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.springframework.boot.gradle.plugin.SpringBootPlugin

// API のクライアントライブラリ。openapi/auth.yaml から生成し、Maven リポジトリへ publish する。
// 呼び出し側（BFF）はこのリポジトリのソースではなく、publish された成果物にバージョンで依存する。
// 生成コードは src/generated に置き git で管理する（`./gradlew generate`）。手で直さず、仕様を直す。
plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    id("io.spring.dependency-management")
    id("org.openapi.generator")
}

group = "com.example.fullstacklab"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    api("org.springframework:spring-web")
    implementation("tools.jackson.core:jackson-databind")
    implementation("tools.jackson.module:jackson-module-kotlin")
}

val openApiSpec = rootProject.layout.projectDirectory.file("openapi/auth.yaml")

val generateClient by tasks.registering(GenerateTask::class) {
    generatorName.set("kotlin")
    library.set("jvm-spring-restclient")
    inputSpec.set(openApiSpec.asFile.path)
    outputDir.set(layout.projectDirectory.dir("src/generated").asFile.path)
    packageName.set("com.example.fullstacklab.authclient")
    configOptions.set(
        mapOf(
            "serializationLibrary" to "jackson",
            "useSpringBoot4" to "true",
            "useJackson3" to "true",
            "omitGradleWrapper" to "true",
            "enumPropertyNaming" to "UPPERCASE",
            "sourceFolder" to "kotlin",
        ),
    )
    // ライブラリのソースだけを生成する（README、build.gradle、docs、テストの雛形は作らない）
    globalProperties.set(
        mapOf(
            "apis" to "", "models" to "", "supportingFiles" to "",
            "apiDocs" to "false", "modelDocs" to "false", "apiTests" to "false", "modelTests" to "false",
        ),
    )
    // 仕様から消えたものが残らないよう、生成する前に前回の生成物を消す（.openapi-generator-ignore は残す）
    doFirst { delete(layout.projectDirectory.dir("src/generated/kotlin")) }
}

kotlin {
    sourceSets["main"].kotlin.srcDir("src/generated/kotlin")
}

// 契約（仕様の info.version）と成果物のバージョンがずれたまま配布しないための検査
val checkSpecVersion by tasks.registering {
    doLast {
        val specVersion = Regex("""^\s{2}version:\s*["']?([^"'\s]+)""", RegexOption.MULTILINE)
            .find(openApiSpec.asFile.readText())?.groupValues?.get(1)
        check(specVersion == project.version.toString()) {
            "openapi/auth.yaml の info.version ($specVersion) と gradle.properties の version (${project.version}) が一致していません"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("client") {
            from(components["java"])
            artifactId = "auth-client"
        }
    }
    repositories {
        // ローカル: Reposilite、CI: GitHub Packages、AWS: CodeArtifact。URL と資格情報は環境変数で切り替える
        maven {
            name = "lab"
            url = uri(providers.environmentVariable("MAVEN_REPO_PUBLISH_URL").getOrElse("http://localhost:8081/releases"))
            isAllowInsecureProtocol = true
            credentials {
                username = providers.environmentVariable("MAVEN_REPO_USER").getOrElse("")
                password = providers.environmentVariable("MAVEN_REPO_PASSWORD").getOrElse("")
            }
        }
    }
}

tasks.withType<PublishToMavenRepository> {
    dependsOn(checkSpecVersion)
}
