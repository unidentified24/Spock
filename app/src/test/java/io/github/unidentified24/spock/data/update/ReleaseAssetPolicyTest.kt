package io.github.unidentified24.spock.data.update
import org.junit.Assert.*
import org.junit.Test
class ReleaseAssetPolicyTest {
 private val base="https://github.com/unidentified24/Spock/releases/download/"
 @Test fun validOfficialAsset(){assertTrue(ReleaseAssetPolicy.allows("2.2",base+"2.2/Spock-2.2.apk"))}
 @Test fun prefixedTag(){assertTrue(ReleaseAssetPolicy.allows("2.2",base+"v2.2/Spock-2.2.apk"))}
 @Test fun traversalVersionRejected(){assertFalse(ReleaseAssetPolicy.version("../../file"))}
 @Test fun wrongRepositoryRejected(){assertFalse(ReleaseAssetPolicy.allows("2.2",base.replace("Spock","Other")+"2.2/Spock-2.2.apk"))}
 @Test fun wrongAssetRejected(){assertFalse(ReleaseAssetPolicy.allows("2.2",base+"2.2/anything.apk"))}
 @Test fun httpAndQueryRejected(){assertFalse(ReleaseAssetPolicy.allows("2.2",base.replace("https:","http:")+"2.2/Spock-2.2.apk"));assertFalse(ReleaseAssetPolicy.allows("2.2",base+"2.2/Spock-2.2.apk?x=1"))}
}
