plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.protobuf) apply false
}

// VS Code's Java extension builds this project in the background; terminal builds (env.sh sets
// cliBuild) use their own output directory so the two never delete each other's generated sources.
if (providers.gradleProperty("cliBuild").isPresent) {
    subprojects { layout.buildDirectory.set(rootDir.resolve("build-cli/$name")) }
}
