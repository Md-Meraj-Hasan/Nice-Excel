package com.meraj.niceexcel

import com.meraj.niceexcel.poc.LibreOfficeNativeProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibreOfficeNativeProbeTest {
    @Test fun loadsOfficialAndroidNativeDependenciesBeforeLibreOfficeCore() {
        val libs = LibreOfficeNativeProbe.requiredLibraries
        assertEquals("nspr4", libs.first())
        assertEquals("c++_shared", libs[libs.lastIndex - 1])
        assertEquals("lo-native-code", libs.last())
        assertEquals(libs.size, libs.toSet().size)
        assertTrue(libs.size >= 10)
    }
}
