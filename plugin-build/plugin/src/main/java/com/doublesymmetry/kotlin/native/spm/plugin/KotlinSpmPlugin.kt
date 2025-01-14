package com.doublesymmetry.kotlin.native.spm.plugin

import com.doublesymmetry.kotlin.native.spm.entity.impl.PlatformManager
import com.doublesymmetry.kotlin.native.spm.swiftPackageBuildDirs
import com.doublesymmetry.kotlin.native.spm.tasks.*
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

@Suppress("TooManyFunctions")
abstract class KotlinSpmPlugin : Plugin<Project> {

    override fun apply(project: Project) = project.pluginManager.withPlugin(MULTIPLATFORM_PLUGIN_NAME) {
        val multiplatformExtension = project.extensions.getByName(KOTLIN_PROJECT_EXTENSION_NAME)
                as KotlinMultiplatformExtension
        val spmExtension = project.extensions.create(SPM_EXTENSION_NAME, KotlinSpmExtension::class.java, project)

        val availablePlatforms = spmExtension.platformsManagerContainer

        // Graph task registration (order should not be changed)
        registerSpmCleanTask(project, availablePlatforms)

        // Integration Swift Package dependency to Kotlin
        registerInitializeSwiftPackageProjectTask(project, availablePlatforms)
        registerCreatePackageSwiftFileTask(project, availablePlatforms)
        registerGenerateXcodeTask(project, availablePlatforms)
        registerBuildFrameworksTask(project, availablePlatforms)
        registerGenerateDefFileTask(project, availablePlatforms)
        registerConfigureLinkerOptionsTask(project, availablePlatforms, multiplatformExtension)
        registerInteropFrameworkTask(project, availablePlatforms, multiplatformExtension)
        registerSpmImportTask(project, availablePlatforms, multiplatformExtension)

        // Integration Kotlin library as Swift Package to Xcode project
        registerBundleXCFrameworkTask(project, availablePlatforms, multiplatformExtension)
        registerArchiveXCFrameworkTask(project)
//        registerPublishXCFramework(project, availablePlatforms)
    }

    private fun registerSpmCleanTask(
        project: Project,
        platforms: NamedDomainObjectContainer<PlatformManager.SwiftPackageManager>,
    ) {
        platforms.all { platform ->
            project.tasks.register(
                "$CLEAN_SWIFT_PACKAGE_PROJECT_TASK_NAME${platform.family}",
                CleanSwiftPackageProjectTask::class.java
            ) { task ->
                task.platformFamily.set(platform.family)
            }
        }
    }

    private fun registerInitializeSwiftPackageProjectTask(
        project: Project,
        platforms: NamedDomainObjectContainer<PlatformManager.SwiftPackageManager>,
    ) {
        platforms.all { platform ->
            project.tasks.register(
                "$INITIALIZE_SWIFT_PACKAGE_PROJECT_TASK_NAME${platform.family}",
                InitializeSwiftPackageProjectTask::class.java
            ) { task ->
                task.platformFamily.set(platform.family)
            }
        }
    }

    private fun registerCreatePackageSwiftFileTask(
        project: Project,
        platforms: NamedDomainObjectContainer<PlatformManager.SwiftPackageManager>,
    ) {
        platforms.all { platform ->
            val initProjectTask = project.tasks.named(
                "$INITIALIZE_SWIFT_PACKAGE_PROJECT_TASK_NAME${platform.family}",
                InitializeSwiftPackageProjectTask::class.java
            )

            project.tasks.register(
                "$CREATE_PACKAGE_SWIFT_FILE_TASK_NAME${platform.family}",
                CreatePackageSwiftFileTask::class.java
            ) { task ->
                task.platformFamily.set(platform.family)
                task.platformVersion.set(platform.version)
                task.platformDependencies.addAll(platform.dependencies)

                task.dependsOn(initProjectTask)
            }
        }
    }

    private fun registerGenerateXcodeTask(
        project: Project,
        platforms: NamedDomainObjectContainer<PlatformManager.SwiftPackageManager>,
    ) {
        platforms.all { platform ->
            val createPackageSwiftFileTask = project.tasks.named(
                "$CREATE_PACKAGE_SWIFT_FILE_TASK_NAME${platform.family}",
                CreatePackageSwiftFileTask::class.java
            )

            project.tasks.register(
                "$GENERATE_XCODE_TASK_NAME${platform.family}",
                GenerateXcodeTask::class.java
            ) { task ->
                task.platformFamily.set(platform.family)

                task.dependsOn(createPackageSwiftFileTask)
            }
        }
    }

    private fun registerBuildFrameworksTask(
        project: Project,
        platforms: NamedDomainObjectContainer<PlatformManager.SwiftPackageManager>,
    ) {
        platforms.all { platform ->
            val generateXcodeTask = project.tasks.named(
                "$GENERATE_XCODE_TASK_NAME${platform.family}",
                GenerateXcodeTask::class.java
            )

            platform.dependenciesContainer.all { dependency ->
                project.tasks.register(
                    "$BUILD_FRAMEWORK_TASK_NAME${platform.family}${dependency.dependencyName}",
                    BuildFrameworksTask::class.java
                ) { task ->
                    task.platformFamily.set(platform.family)
                    task.platformDependency.set(dependency.dependencyName)

                    task.dependsOn(generateXcodeTask)
                }
            }
        }
    }

    private fun registerGenerateDefFileTask(
        project: Project,
        platforms: NamedDomainObjectContainer<PlatformManager.SwiftPackageManager>,
    ) {
        platforms.all { platform ->
            platform.dependenciesContainer.all { dependency ->
                val buildFrameworkTask = project.tasks.named(
                    "$BUILD_FRAMEWORK_TASK_NAME${platform.family}${dependency.dependencyName}",
                    BuildFrameworksTask::class.java
                )

                project.tasks.register(
                    "$GENERATE_DEF_FILE_TASK_NAME${platform.family}${dependency.dependencyName}",
                    GenerateDefFileTask::class.java
                ) { task ->
                    task.platformFamily.set(platform.family)
                    task.platformDependency.set(dependency.dependencyName)

                    task.dependsOn(buildFrameworkTask)
                }
            }
        }
    }

    private fun registerConfigureLinkerOptionsTask(
        project: Project,
        platforms: NamedDomainObjectContainer<PlatformManager.SwiftPackageManager>,
        multiplatformExtension: KotlinMultiplatformExtension,
    ) {
        multiplatformExtension.supportedTargets().all { mppTarget ->
            platforms.all platforms@{ platform ->
                val family = platform.family
                if (family != mppTarget.konanTarget.family) return@platforms

                platform.dependenciesContainer.all { dependency ->
                    val buildFrameworkTask = project.tasks.named(
                        "$BUILD_FRAMEWORK_TASK_NAME${platform.family}${dependency.dependencyName}",
                        BuildFrameworksTask::class.java
                    )

                    mppTarget.binaries.withType(Framework::class.java) {
                        it.isStatic = false
                        it.transitiveExport = true
                        it.linkerOpts(
                            "-F${buildFrameworkTask.get().outputFrameworkDirectory.get().parent}",
                            "-framework",
                            dependency.dependencyName
                        )
                    }
                }
            }
        }
    }

    @Suppress("UnstableApiUsage")
    private fun registerInteropFrameworkTask(
        project: Project,
        platforms: NamedDomainObjectContainer<PlatformManager.SwiftPackageManager>,
        multiplatformExtension: KotlinMultiplatformExtension,
    ) {
        multiplatformExtension.supportedTargets().all { mppTarget ->
            platforms.all { platform ->
                val family = platform.family
                if (family == mppTarget.konanTarget.family) {
                    platform.dependenciesContainer.all { dependency ->
                        val defFileTask = project.tasks.named(
                            "$GENERATE_DEF_FILE_TASK_NAME${family}${dependency.dependencyName}",
                            GenerateDefFileTask::class.java
                        )
                        mppTarget.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME).cinterops.create(
                            dependency.dependencyName
                        ) { interop ->
                            val interopTask = project.tasks.getByPath(interop.interopProcessingTaskName)
                            interopTask.dependsOn(defFileTask)
                            interopTask.group = TASK_GROUP

                            interop.defFileProperty.set(defFileTask.flatMap { it.outputDefFile })
                            interop.packageName = "spm.${dependency.dependencyName}"
                        }
                    }
                }
            }
        }
    }

    private fun registerSpmImportTask(
        project: Project,
        platforms: NamedDomainObjectContainer<PlatformManager.SwiftPackageManager>,
        multiplatformExtension: KotlinMultiplatformExtension,
    ) {
        project.tasks.register(
            SPM_IMPORT_TASK_NAME,
            SpmImport::class.java
        ) { task ->
            multiplatformExtension.supportedTargets().all { mppTarget ->
                platforms.all { platform ->
                    val family = platform.family
                    if (family == mppTarget.konanTarget.family) {
                        platform.dependenciesContainer.all { dependency ->
                            val cinteropTask = project.tasks.getByName(
                                "cinterop${dependency.dependencyName}${mppTarget.targetName.capitalize()}"
                            )
                            if (cinteropTask.group == TASK_GROUP) {
                                task.dependsOn(cinteropTask)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun registerBundleXCFrameworkTask(
        project: Project,
        platforms: NamedDomainObjectContainer<PlatformManager.SwiftPackageManager>,
        multiplatformExtension: KotlinMultiplatformExtension,
    ) {
        project.tasks.register(
            BUNDLE_XCFRAMEWORK_TASK_NAME,
            BundleXCFramework::class.java
        ) { task ->
            multiplatformExtension.supportedTargets().all { mppTarget ->
                platforms.all { platform ->
                    val family = platform.family
                    if (family == mppTarget.konanTarget.family) {
                        val linkTask = project.tasks.getByName(
                            "linkReleaseFramework" + mppTarget.targetName.capitalize()
                        )
                        task.dependsOn(linkTask)
                    }
                }
            }
        }
    }

    private fun registerArchiveXCFrameworkTask(
        project: Project,
    ) {
        val bundleXCFrameworkTask = project.tasks.named(
            BUNDLE_XCFRAMEWORK_TASK_NAME,
            BundleXCFramework::class.java
        )

        project.tasks.register(
            ARCHIVE_XCFRAMEWORK_TASK_NAME,
            ArchiveXCFramework::class.java
        ) { task ->
            task.from(project.swiftPackageBuildDirs.xcFrameworkDir())
            task.xcFramework.set(bundleXCFrameworkTask.map { it.xcFramework })

            task.dependsOn(bundleXCFrameworkTask)
        }
    }

    @Suppress("UnstableApiUsage")
    private fun registerPublishXCFramework(
        project: Project,
        platforms: NamedDomainObjectContainer<PlatformManager.SwiftPackageManager>,
    ) {
        val archiveXCFrameworkTask = project.tasks.named(
            ARCHIVE_XCFRAMEWORK_TASK_NAME,
            ArchiveXCFramework::class.java
        )

        project.tasks.register(
            PUBLISH_XCFRAMEWORK_TASK_NAME,
            PublishXCFramework::class.java
        ) { task ->
            task.archiveXCFramework.set(archiveXCFrameworkTask.flatMap { it.archiveFile })
            task.family.set(platforms.map { it.family }.random())

            task.dependsOn(archiveXCFrameworkTask)
        }
    }

    companion object {
        private const val KOTLIN_PROJECT_EXTENSION_NAME = "kotlin"
        const val MULTIPLATFORM_PLUGIN_NAME = "kotlin-multiplatform"
        const val SPM_EXTENSION_NAME = "spm"
        const val TASK_GROUP = "swift package manager"

        const val CLEAN_SWIFT_PACKAGE_PROJECT_TASK_NAME = "cleanSwiftPackageProject"

        const val INITIALIZE_SWIFT_PACKAGE_PROJECT_TASK_NAME = "initializeSwiftPackageProject"
        const val CREATE_PACKAGE_SWIFT_FILE_TASK_NAME = "createPackageSwiftFile"
        const val GENERATE_XCODE_TASK_NAME = "generateXcode"
        const val BUILD_FRAMEWORK_TASK_NAME = "buildFrameworks"
        const val GENERATE_DEF_FILE_TASK_NAME = "generateDefFile"
        const val SPM_IMPORT_TASK_NAME = "spmImport"

        const val BUNDLE_XCFRAMEWORK_TASK_NAME = "bundleXCFramework"
        const val ARCHIVE_XCFRAMEWORK_TASK_NAME = "archiveXCFramework"
        const val PUBLISH_XCFRAMEWORK_TASK_NAME = "publishXCFramework"

        private fun KotlinMultiplatformExtension.supportedTargets() = targets
            .withType(KotlinNativeTarget::class.java)
            .matching { it.konanTarget.family.isAppleFamily }
    }
}
