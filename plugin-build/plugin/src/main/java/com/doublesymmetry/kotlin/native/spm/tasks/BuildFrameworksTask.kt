package com.doublesymmetry.kotlin.native.spm.tasks

import com.doublesymmetry.kotlin.native.spm.plugin.KotlinSpmPlugin
import com.doublesymmetry.kotlin.native.spm.swiftPackageBuildDirs
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.konan.target.Family
import java.io.File

@CacheableTask
abstract class BuildFrameworksTask : Exec() {
    init {
        /**
         * Task like a command: `xcodebuild build --target ${TARGET_NAME}`
         */
        description = "Build the target in the build root"
        group = KotlinSpmPlugin.TASK_GROUP
    }

    @Input
    val platformFamily: Property<Family> = project.objects.property(Family::class.java)

    @Input
    val platformDependency: Property<String> = project.objects.property(String::class.java)

    @get:OutputDirectory
    val outputFrameworkDirectory: Provider<File>
        get() = platformFamily.map {
            project.swiftPackageBuildDirs.releaseDir(it).resolve("${platformDependency.get()}.framework")
        }

    override fun exec() {
        val family = platformFamily.get()

        workingDir = project.swiftPackageBuildDirs.platformRoot(family)
        commandLine(
            "xcodebuild", "build",
            "-project", "${family.name}.xcodeproj",
            "-target", platformDependency.get(),
            "-sdk", family.toSdk(),
            "-configuration", "Release",
            "-quiet"
        )

        super.exec()
    }

    private fun Family.toSdk() : String {
        return when(this) {
            Family.OSX -> "macosx"
            Family.IOS -> "iphonesimulator"
            else -> ""
        }
    }
}
