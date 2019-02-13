plugins {
  id("java-library")
  id("kotlin")
  id("kotlin-kapt")
  id("com.github.dcendents.android-maven")
}

dependencies {
  api(Deps.kotlin.stdlib)
  api(Deps.rxJava)
  api(Deps.okhttp)
  api(Deps.jsoup)
  api(Deps.coroutines.core)

  implementation(Deps.toothpick.runtime)
}
