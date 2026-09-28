plugins {
    id("java-library")
    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.vanniktech.publish)
}

repositories {
    mavenCentral()
}

// Region lookup benchmarks. Never part of the build: run them with `gradlew :api:jmh`, and pass
// `-Pjmh.args="<regex> <jmh options>"` to narrow or tune a run.
val jmh: SourceSet = sourceSets.create("jmh") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
}

dependencies {
    // Paper API only (no NMS) — provides org.bukkit.* and the jspecify annotations.
    paperweight.paperDevBundle(libs.versions.paper.api.get())

    "jmhImplementation"(libs.jmh.core)
    "jmhAnnotationProcessor"(libs.jmh.generator)
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.register<JavaExec>("jmh") {
    group = "verification"
    description = "Runs the region lookup benchmarks."
    classpath = jmh.runtimeClasspath
    mainClass = "org.openjdk.jmh.Main"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    args(providers.gradleProperty("jmh.args").getOrElse("RegionLookupBenchmark").split(" "))
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(group.toString(), "uworldguard-api", version.toString())

    pom {
        name = "uWorldGuard API"
        description = "Region and flag API for the uWorldGuard Paper plugin."
        inceptionYear = "2026"
        url = "https://github.com/tricrotism/uWorldGuard"
        licenses {
            license {
                name = "MIT License"
                url = "https://github.com/tricrotism/uWorldGuard/blob/master/LICENSE.md"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "tricrotism"
                name = "Sage Kummer"
                url = "https://github.com/tricrotism"
            }
        }
        scm {
            url = "https://github.com/tricrotism/uWorldGuard"
            connection = "scm:git:git://github.com/tricrotism/uWorldGuard.git"
            developerConnection = "scm:git:ssh://git@github.com/tricrotism/uWorldGuard.git"
        }
    }
}
