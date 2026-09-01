plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.android.tools.build:gradle:9.3.1")
}

gradlePlugin {
    plugins {
        create("secgen") {
            id = "com.nexora.docly.secgen"
            implementationClass = "com.nexora.docly.secgen.SecGenPlugin"
        }
    }
}
