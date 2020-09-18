import com.moowork.gradle.node.npm.NpmTask

plugins {
    id("idea")
    id("com.github.node-gradle.node") version "2.2.3"
}

idea {
    module {
        excludeDirs.add(file("node_modules"))
    }
}

node {
    version = "12.16.1"
    yarnVersion = "1.13.0"
    download = true
}

tasks {

    register("npmBuild", NpmTask::class.java) {
        dependsOn(named("npmInstall"))
        group = "build"
        setArgs(listOf("run", "build"))
        inputs.files("package.json", "package-lock.json", "tsconfig.json")
        inputs.dir("src")
        outputs.dir("build")
    }
}
