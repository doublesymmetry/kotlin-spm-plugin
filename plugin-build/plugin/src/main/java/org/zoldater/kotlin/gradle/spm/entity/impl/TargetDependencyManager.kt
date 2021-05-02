package org.zoldater.kotlin.gradle.spm.entity.impl

import org.zoldater.kotlin.gradle.spm.entity.TargetDependency
import org.zoldater.kotlin.gradle.spm.entity.TargetDependencyMarker
import org.zoldater.kotlin.gradle.spm.entity.impl.TargetDependencyManager.Target

/**
 * @see [Target.Dependency](https://github.com/apple/swift-package-manager/blob/main/Documentation/PackageDescription.md#target-dependency)
 * @see [Target.Dependency](https://docs.swift.org/package-manager/PackageDescription/PackageDescription.html#target-dependency)
 */
@TargetDependencyMarker
class TargetDependencyManager {
    val targetDependencies = mutableListOf<TargetDependency>()

    fun target(name: String, condition: String? = null) {
        val target = Target(name, condition)
        targetDependencies.add(target)
    }

    fun product(name: String, `package`: String, condition: String? = null) {
        val product = Product(name, `package`, condition)
        targetDependencies.add(product)
    }

    data class Target(
        private val name: String,
        val condition: String? = null
    ) : TargetDependency {
        override fun getName(): String = name
    }

    data class Product(
        private val name: String,
        val `package`: String,
        val condition: String? = null
    ) : TargetDependency {
        override fun getName(): String = name
    }
}
