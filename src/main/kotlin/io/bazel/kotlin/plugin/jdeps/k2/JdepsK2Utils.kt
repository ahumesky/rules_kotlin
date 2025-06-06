package io.bazel.kotlin.plugin.jdeps.k2

import io.bazel.kotlin.plugin.jdeps.k2.RefCache.vbseClass
import io.bazel.kotlin.plugin.jdeps.k2.RefCache.vbseGetVirtualFileMethod
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.load.kotlin.JvmPackagePartSource
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinarySourceElement
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource

private object RefCache {
  val vbseClass: Class<*>? by lazy {
    runCatching {
      Class.forName("org.jetbrains.kotlin.fir.java.VirtualFileBasedSourceElement")
    }.getOrNull()
  }

  val vbseGetVirtualFileMethod by lazy {
    vbseClass
      ?.runCatching {
        getMethod("getVirtualFile")
      }?.getOrNull()
  }

  val jbseClass: Class<*>? by lazy {
    runCatching {
      Class.forName("org.jetbrains.kotlin.fir.java.JavaBinarySourceElement")
    }.getOrNull()
  }

  val jbseGetJavaClassMethod by lazy {
    jbseClass
      ?.runCatching {
        getMethod("getJavaClass")
      }?.getOrNull()
  }
}

/**
 * Returns whether class is coming from JVM runtime env. There is no need to track these classes.
 *
 * @param className the class name of the class
 * @return whether class is provided by JSM runtime or not
 */
internal fun isJvmClass(className: String): Boolean =
  className.startsWith("java") || className.startsWith("modules/java.base/java/")

internal fun DeserializedContainerSource.classId(): ClassId? =
  when (this) {
    is JvmPackagePartSource -> classId
    is KotlinJvmBinarySourceElement -> binaryClass.classId
    else -> null
  }

internal fun SourceElement.binaryClass(): String? =
  if (this is KotlinJvmBinarySourceElement) {
    binaryClass.location
  } else if (this is JvmPackagePartSource) {
    this.knownJvmBinaryClass?.location
  } else if (vbseClass != null && vbseClass!!.isInstance(this)) {
    val virtualFile = vbseGetVirtualFileMethod!!.invoke(this)
    virtualFile?.javaClass!!.getMethod("getPath").invoke(virtualFile) as? String
  } else if (RefCache.jbseClass != null && RefCache.jbseClass!!.isInstance(this)) {
    val jClass = RefCache.jbseGetJavaClassMethod!!.invoke(this)
    val virtualFile = jClass!!.javaClass.getMethod("getVirtualFile").invoke(jClass)
    virtualFile.javaClass.getMethod("getPath").invoke(virtualFile) as? String
  } else {
    null
  }

internal fun DeserializedContainerSource.binaryClass(): String? =
  when (this) {
    is JvmPackagePartSource -> this.knownJvmBinaryClass?.location
    is KotlinJvmBinarySourceElement -> binaryClass.location
    else -> null
  }
