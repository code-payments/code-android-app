plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.ksp.symbol.processing)
}
