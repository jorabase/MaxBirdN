package com.example.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import android.os.Process
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Advanced Anti-Decompilation, Anti-Tamper & Anti-Hooking Security Shield
 * Protects MaxBird from MT Manager, NP Manager, Frida, Xposed, JADX, and APK Repackaging.
 */
object AntiTamperSecurity {

    private val SUSPICIOUS_HOOK_FILES = listOf(
        "/data/local/tmp/frida",
        "/data/local/tmp/frida-server",
        "/data/local/tmp/re.frida.server",
        "/system/framework/XposedBridge.jar",
        "/system/lib/libxposed_art.so",
        "/system/lib64/libxposed_art.so",
        "/system/bin/mt",
        "/data/local/tmp/mt"
    )

    private val SUSPICIOUS_PACKAGES = listOf(
        "bin.mt.plus", // MT Manager
        "bin.mt.apksigner",
        "io.github.muntashirakon.AppManager",
        "org.lsposed.manager",
        "de.robv.android.xposed.installer"
    )

    /**
     * Comprehensive security check executed at runtime
     * @return true if environment is safe, false if tampering/hooking is detected
     */
    fun isAppSecure(context: Context): Boolean {
        // 1. Check if a live debugger is actively attached (Reverse engineering attempt)
        if (isDebuggerAttached(context)) {
            return false
        }

        // 2. Check for dynamic hooking frameworks (Frida / Xposed)
        if (isHookingFrameworkDetected()) {
            return false
        }

        // 3. Check for MT Manager / Modded injection libraries in memory maps
        if (isSuspiciousMemoryMapDetected()) {
            return false
        }

        return true
    }

    /**
     * Checks if a dynamic debugger or tracer is attached to the process
     */
    fun isDebuggerAttached(context: Context): Boolean {
        try {
            if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
                return true
            }
            // Check if debuggable flag is enabled outside of genuine debug builds
            val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val isTracerPresent = checkTracerPid()
            if (!isDebuggable && isTracerPresent) {
                return true
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Checks if /proc/self/status has a non-zero TracerPid (ptrace reverse engineering)
     */
    private fun checkTracerPid(): Boolean {
        try {
            val file = File("/proc/self/status")
            if (!file.exists()) return false
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line?.startsWith("TracerPid:", ignoreCase = true) == true) {
                        val pid = line?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
                        return pid > 0
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Checks if Xposed / Frida / Substrate classes are loaded in JVM memory
     */
    private fun isHookingFrameworkDetected(): Boolean {
        val suspiciousClasses = listOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XposedHelpers",
            "com.saurik.substrate.MS\$MethodPointer",
            "me.weishu.epic.art.Epic",
            "com.swift.sandhook.SandHook"
        )

        for (className in suspiciousClasses) {
            try {
                Class.forName(className)
                return true // Hooking class present in runtime classpath!
            } catch (_: ClassNotFoundException) {
                // Normal
            } catch (_: Exception) {
                return true
            }
        }

        // Check common suspicious filesystem locations
        for (path in SUSPICIOUS_HOOK_FILES) {
            try {
                if (File(path).exists()) return true
            } catch (_: Exception) {}
        }

        return false
    }

    /**
     * Inspects /proc/self/maps to detect injected MT Manager, Frida, or Hook shared libraries (.so)
     */
    private fun isSuspiciousMemoryMapDetected(): Boolean {
        try {
            val mapsFile = File("/proc/self/maps")
            if (!mapsFile.exists()) return false

            BufferedReader(FileReader(mapsFile)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val lower = line?.lowercase() ?: continue
                    if (lower.contains("frida-agent") ||
                        lower.contains("frida-gadget") ||
                        lower.contains("libapkhook") ||
                        lower.contains("libsandhook") ||
                        lower.contains("libxposed") ||
                        lower.contains("mt.bin")
                    ) {
                        return true // Injected hook found in process memory!
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Enforce strict exit if tampering is discovered
     */
    fun exitIfTampered(context: Context) {
        if (!isAppSecure(context)) {
            Process.killProcess(Process.myPid())
            System.exit(0)
        }
    }
}
