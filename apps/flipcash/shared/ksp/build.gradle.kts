plugins {
    kotlin("jvm")
    id(Plugins.kotlin_ksp)
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(Libs.ksp_symbol_processing)
}