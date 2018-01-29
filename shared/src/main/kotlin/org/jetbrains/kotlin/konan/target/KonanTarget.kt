/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.konan.target

import org.jetbrains.kotlin.konan.util.visibleName
import org.jetbrains.kotlin.konan.util.Named
import org.jetbrains.kotlin.konan.target.KonanTarget.*

enum class Family(val exeSuffix:String, val dynamicPrefix: String, val dynamicSuffix: String) {
    OSX     ("kexe", "lib", "dylib"),
    IOS     ("kexe", "lib", "dylib"),
    LINUX   ("kexe", "lib", "so"   ),
    WINDOWS ("exe" , ""   , "dll"  ),
    ANDROID ("so"  , "lib", "so"   ),
    WASM    ("wasm", ""   , "wasm" ),
    ZEPHYR  ("o"   , "lib", "a"    )
}

enum class Architecture(val bitness: Int) {
    X64(64),
    ARM64(64),
    ARM32(32),
    MIPS32(32),
    MIPSEL32(32),
    WASM32(32);
}

sealed class KonanTarget(override val name: String, val family: Family, val architecture: Architecture, val detailedName: String) : Named {
    object ANDROID_ARM32 :  KonanTarget( "android_arm32",   Family.ANDROID,     Architecture.ARM32,     "android_arm32")
    object ANDROID_ARM64 :  KonanTarget( "android_arm64",   Family.ANDROID,     Architecture.ARM64,     "android_arm64")
    object IPHONE :         KonanTarget( "iphone",          Family.IOS,         Architecture.ARM64,     "ios")
    object IPHONE_SIM :     KonanTarget( "iphone_sim",      Family.IOS,         Architecture.X64,       "ios_sim")
    object LINUX :          KonanTarget( "linux",           Family.LINUX,       Architecture.X64,       "linux")
    object MINGW :          KonanTarget( "mingw",           Family.WINDOWS,     Architecture.X64,       "mingw")
    object MACBOOK :        KonanTarget( "macbook",         Family.OSX,         Architecture.X64,       "osx")
    object RASPBERRYPI :    KonanTarget( "raspberrypi",     Family.LINUX,       Architecture.ARM32,     "raspberrypi")
    object LINUX_MIPS32 :   KonanTarget( "linux_mips32",    Family.LINUX,       Architecture.MIPS32,    "linux_mips32")
    object LINUX_MIPSEL32 : KonanTarget( "linux_mipsel32",  Family.LINUX,       Architecture.MIPSEL32,  "linux_mipsel32")
    object WASM32 :         KonanTarget( "wasm32",          Family.WASM,        Architecture.WASM32,    "wasm32")

    // Tunable targets
    class ZEPHYR(val subName: String, val genericName: String = "zephyr") : KonanTarget("${genericName}_$subName", Family.ZEPHYR, Architecture.ARM32, "${genericName}_$subName")

    companion object {
        protected val memberObjects = mutableListOf<KonanTarget>()
        fun values() = memberObjects

        // TODO: need a better way to enumerated predefined targets.
        val predefinedTargets = listOf(
            ANDROID_ARM32, ANDROID_ARM64, IPHONE, IPHONE_SIM, LINUX, MINGW, MACBOOK, RASPBERRYPI, LINUX_MIPS32, LINUX_MIPSEL32, WASM32
        )

        val zephyrSubtargets = ZephyrBoardManager.availableBoards.map { ZEPHYR(it) }
    }

    init {
       memberObjects.add(this)
    }
}

fun hostTargetSuffix(host: KonanTarget, target: KonanTarget) =
    if (target == host) host.detailedName else "${host.detailedName}-${target.detailedName}"

enum class CompilerOutputKind {
    PROGRAM {
        override fun suffix(target: KonanTarget?) = ".${target!!.family.exeSuffix}"
    },
    DYNAMIC {
        override fun suffix(target: KonanTarget?) = ".${target!!.family.dynamicSuffix}"
        override fun prefix(target: KonanTarget?) = "${target!!.family.dynamicPrefix}"
    },
    FRAMEWORK {
        override fun suffix(target: KonanTarget?): String = ".framework"
    },
    LIBRARY {
        override fun suffix(target: KonanTarget?) = ".klib"
    },
    BITCODE {
        override fun suffix(target: KonanTarget?) = ".bc"
    };

    abstract fun suffix(target: KonanTarget? = null): String
    open fun prefix(target: KonanTarget? = null): String = ""
}

class TargetManager(val userRequest: String? = null) {
    val targets = KonanTarget.values().associate{ it.visibleName to it }
    val target = determineCurrent()
    val targetName
        get() = target.visibleName


    fun known(name: String): String {
        if (targets[name] == null) {
            throw TargetSupportException("Unknown target: $name. Use -list_targets to see the list of available targets")
        }
        return name
    }

    fun list() {
        enabled.forEach { 
            val isDefault = if (it == target) "(default)" else ""
            println(String.format("%1$-30s%2$-10s", "${it.visibleName}:", "$isDefault"))
        }
    }

    fun determineCurrent(): KonanTarget {
        return if (userRequest == null || userRequest == "host") {
            host
        } else {
            targets[known(userRequest)]!!
        }
    }

    val hostTargetSuffix get() = hostTargetSuffix(host, target)
    val targetSuffix get() = target.detailedName

    companion object {

        fun host_os(): String {
            val javaOsName = System.getProperty("os.name")
            return when {
                javaOsName == "Mac OS X" -> "osx"
                javaOsName == "Linux" -> "linux"
                javaOsName.startsWith("Windows") -> "windows"
                else -> throw TargetSupportException("Unknown operating system: ${javaOsName}")
            }
        }

        @JvmStatic
        fun simpleOsName(): String {
            val hostOs = host_os()
            return if (hostOs == "osx") "macos" else hostOs
        }

        @JvmStatic
        fun longerSystemName(): String = when (host) {
            KonanTarget.MACBOOK ->  "darwin-macos"
            KonanTarget.LINUX ->  "linux-x86-64"
            KonanTarget.MINGW -> "windows-x86-64"
            else -> throw TargetSupportException("Unknown host: $host")
        }

        val jniHostPlatformIncludeDir: String 
            get() = when(host) {
                KonanTarget.MACBOOK -> "darwin"
                KonanTarget.LINUX -> "linux"
                KonanTarget.MINGW ->"win32"
                else -> throw TargetSupportException("Unknown host: $host.")
            }

        fun host_arch(): String {
            val javaArch = System.getProperty("os.arch")
            return when (javaArch) {
                "x86_64" -> "x86_64"
                "amd64"  -> "x86_64"
                "arm64"  -> "arm64"
                else -> throw TargetSupportException("Unknown hardware platform: ${javaArch}")
            }
        }

        val host: KonanTarget = when (host_os()) {
            "osx"   -> KonanTarget.MACBOOK
            "linux" -> KonanTarget.LINUX
            "windows" -> KonanTarget.MINGW
            else -> throw TargetSupportException("Unknown host target: ${host_os()} ${host_arch()}")
        }

        val hostIsMac   = (host == KonanTarget.MACBOOK)
        val hostIsLinux = (host == KonanTarget.LINUX)
        val hostIsMingw = (host == KonanTarget.MINGW)

        val hostSuffix get() = host.detailedName
        @JvmStatic
        val hostName get() = host.visibleName

        @JvmStatic
        val enabled: List<KonanTarget> by lazy { 
            when (host) {
                KonanTarget.LINUX   -> listOf(
                    KonanTarget.LINUX,
                    KonanTarget.RASPBERRYPI,
                    KonanTarget.LINUX_MIPS32,
                    KonanTarget.LINUX_MIPSEL32,
                    KonanTarget.ANDROID_ARM32,
                    KonanTarget.ANDROID_ARM64,
                    KonanTarget.WASM32
                ) + KonanTarget.zephyrSubtargets
                KonanTarget.MINGW -> listOf(
                    KonanTarget.MINGW
                ) 
                KonanTarget.MACBOOK -> listOf(
                    KonanTarget.MACBOOK,
                    KonanTarget.IPHONE,
                    KonanTarget.IPHONE_SIM,
                    KonanTarget.ANDROID_ARM32,
                    KonanTarget.ANDROID_ARM64,
                    KonanTarget.WASM32
                ) + KonanTarget.zephyrSubtargets
                else ->
                    throw TargetSupportException("Unknown host platform: $host")
            }        }
    }
}

public val KonanTarget.enabled 
    get() = TargetManager.enabled.contains(this)

class TargetSupportException (message: String = "", cause: Throwable? = null) : Exception(message, cause)


// TODO: get the list of available borads from the konan.properties

object ZephyrBoardManager {
    val availableBoards = listOf("stm32f4-disco")
}

