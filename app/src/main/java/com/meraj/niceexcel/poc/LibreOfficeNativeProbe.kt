package com.meraj.niceexcel.poc

/**
 * Diagnostic loader for the official LibreOffice Android native runtime.
 * This does not initialize LibreOfficeKit or calculate workbooks. The exact
 * matching native libraries and Bootstrap JNI classes must come from the same
 * LibreOffice source revision.
 */
object LibreOfficeNativeProbe {
    /** Load order follows LibreOffice's Android Bootstrap NativeLibLoader. */
    val requiredLibraries: List<String> = listOf(
        "nspr4", "plds4", "plc4", "nssutil3", "freebl3",
        "sqlite3", "softokn3", "nss3", "nssckbi", "nssdbm3",
        "smime3", "ssl3", "c++_shared", "lo-native-code"
    )

    @Volatile private var loadAttempted = false
    @Volatile private var loadedLibraries: List<String> = emptyList()
    @Volatile private var failedLibrary: String? = null
    @Volatile private var loadFailure: Throwable? = null

    /** Attempts ordered loading. Returns false on the first missing/broken library. */
    @Synchronized
    fun tryLoadCore(): Boolean {
        if (loadAttempted) return loadFailure == null && loadedLibraries.size == requiredLibraries.size
        loadAttempted = true
        val loaded = mutableListOf<String>()
        for (library in requiredLibraries) {
            try {
                System.loadLibrary(library)
                loaded += library
                loadedLibraries = loaded.toList()
            } catch (t: Throwable) {
                failedLibrary = library
                loadFailure = t
                return false
            }
        }
        return true
    }

    fun diagnostic(): String = when {
        !loadAttempted -> "NOT_TESTED: native loading has not been attempted"
        loadFailure != null -> "NOT_AVAILABLE: failed loading ${failedLibrary}: ${loadFailure?.javaClass?.simpleName}: ${loadFailure?.message}; loaded=${loadedLibraries.joinToString()}"
        loadedLibraries.size == requiredLibraries.size -> "LIBRARIES_LOADED: JNI bootstrap and LibreOfficeKit initialization are still unverified"
        else -> "INCOMPLETE: loaded=${loadedLibraries.joinToString()}"
    }
}
