package io.github.unidentified24.spock.data.update

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    @Test fun supportsFutureVersionsNumerically() {
        assertTrue(ReleaseVersion.isNewer("v1.10", "1.9"))
        assertTrue(ReleaseVersion.isNewer("2.0", "1.999"))
        assertTrue(ReleaseVersion.isNewer("1.9999999999999999999999999", "1.9999999999999999999999998"))
        assertFalse(ReleaseVersion.isNewer("1.7.0", "1.7"))
        assertFalse(ReleaseVersion.isNewer("1.7-beta", "1.6"))
        assertFalse(ReleaseVersion.isNewer("Release", "1.6"))
    }
}
