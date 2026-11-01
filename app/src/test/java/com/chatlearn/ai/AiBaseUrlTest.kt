package com.chatlearn.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class AiBaseUrlTest {
    private val default = "https://generativelanguage.googleapis.com"

    @Test fun releaseBuildsAlwaysUseGoogle() =
        assertEquals(default, aiBaseUrl(debuggable = false, overrideText = "http://10.0.2.2:8765"))

    @Test fun debugBuildsWithoutOverrideUseGoogle() {
        assertEquals(default, aiBaseUrl(debuggable = true, overrideText = null))
        assertEquals(default, aiBaseUrl(debuggable = true, overrideText = "  \n"))
    }

    @Test fun debugOverrideIsTrimmedAndLosesTrailingSlash() =
        assertEquals("http://10.0.2.2:8765", aiBaseUrl(debuggable = true, overrideText = " http://10.0.2.2:8765/ \n"))

    @Test fun overrideThatIsNotAnHttpUrlIsIgnored() =
        assertEquals(default, aiBaseUrl(debuggable = true, overrideText = "file:///etc/passwd"))
}
