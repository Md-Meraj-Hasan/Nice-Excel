package com.meraj.niceexcel.poc

/**
 * Isolated JNI boundary for the LibreOfficeKit experiment.
 *
 * This class is intentionally not referenced by the production app. It will only
 * become usable after the matching LibreOffice Android native core and JNI
 * bootstrap have been built and packaged.
 */
object LibreOfficeNativeProbe {
    @Volatile
    private var loadAttempted = false

    @Volatile
    private var loadFailure: Throwable? = null

    /** Attempts to load the official Android core; returns false if it is absent. */
    @Synchronized
    fun tryLoadCore(): Boolean {
        if (!loadAttempted) {
            loadAttempted = true
            try {
                System.loadLibrary("lo-native-code")
            } catch (t: Throwable) {
                loadFailure = t
            }
        }
        return loadFailure == null
    }

    fun diagnostic(): String = when {
        !loadAttempted -> "NOT_TESTED: native core load has not been attempted"
        loadFailure != null -> "NOT_AVAILABLE: ${loadFailure?.javaClass?.simpleName}: ${loadFailure?.message}"
        else -> "LIBRARY_LOADED: JNI bootstrap/engine initialization is still unverified"
    }
}
