buildscript {
  repositories {
    google()
    jcenter()
  }
  dependencies {
    classpath("com.android.tools.build:gradle:3.3.0")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Deps.kotlin.version}")
    classpath("com.github.dcendents:android-maven-gradle-plugin:2.1")
  }
}

plugins {
  id("com.github.ben-manes.versions") version "0.20.0"
}

allprojects {
  repositories {
    google()
    maven { setUrl("https://jitpack.io") }
    maven { setUrl("https://google.bintray.com/flexbox-layout") }
    jcenter()
  }
}

tasks.register("clean", Delete::class) {
  delete(rootProject.buildDir)
}
