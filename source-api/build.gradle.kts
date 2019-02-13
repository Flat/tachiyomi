plugins {
  id("java-library")
  id("kotlin")
  id("com.github.dcendents.android-maven")
}

dependencies {
  implementationProject(Projects.common)
}
