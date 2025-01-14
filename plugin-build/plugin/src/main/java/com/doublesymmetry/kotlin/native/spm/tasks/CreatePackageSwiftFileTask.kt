package com.doublesymmetry.kotlin.native.spm.tasks

import com.doublesymmetry.kotlin.native.spm.entity.impl.DependencyManager
import com.doublesymmetry.kotlin.native.spm.plugin.KotlinSpmPlugin
import com.doublesymmetry.kotlin.native.spm.swiftPackageBuildDirs
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.konan.target.Family
import java.io.File

@CacheableTask
abstract class CreatePackageSwiftFileTask : DefaultTask() {
    init {
        description = "Create package.swift file with content"
        group = KotlinSpmPlugin.TASK_GROUP
    }

    private val swiftPackageTemplateContent = this::class.java.getResource("/Package.swift")!!.readText()

    @Input
    val platformFamily: Property<Family> = project.objects.property(Family::class.java)

    @Input
    val platformVersion: Property<String> = project.objects.property(String::class.java)

    @Nested
    val platformDependencies: NamedDomainObjectContainer<DependencyManager.Package> =
        project.container(DependencyManager.Package::class.java)

    @get:OutputFile
    val outputPlatformPackageSwiftFile: Provider<File>
        get() = platformFamily.map { project.swiftPackageBuildDirs.packageSwiftFile(it) }

    @TaskAction
    fun action() {
        val family = platformFamily.get()

        val dependencyArea = platformDependencies
            .toList()
            .joinToString(", ") { it.convertToPackageContent() }

        val targetDependencyArea = platformDependencies
            .toList()
            .joinToString(", ") { "\"${it.dependencyName}\"" }

        project.swiftPackageBuildDirs.platformRoot(family)
            .resolve("Package.swift")
            .writeText(swiftPackageTemplateContent
                .replace("\$PLATFORM_NAME", family.name)
                .replace("\$PLATFORM_TYPE", family.toPlatformPackageSwiftTemplate())
                .replace("\$PLATFORM_VERSION", platformVersion.get())
                .replace("\$DEPENDENCIES", dependencyArea)
                .replace("\$TARGET_DEPENDENCY", targetDependencyArea)
            )
    }

    private fun Family.toPlatformPackageSwiftTemplate(): String = when (this) {
        Family.IOS -> ".iOS"
        Family.OSX -> ".macOS"
        Family.TVOS -> ".tvOS"
        Family.WATCHOS -> ".watchOS"
        else -> throw IllegalArgumentException("Apple family platform not found")
    }
}
