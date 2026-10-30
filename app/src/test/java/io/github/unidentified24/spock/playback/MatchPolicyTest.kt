package io.github.unidentified24.spock.playback
import org.junit.Assert.*
import org.junit.Test
class MatchPolicyTest {
    @Test fun missingBadgeCanUseStrongRecordingEvidence(){assertTrue(MatchPolicy.editionAllowed(true,false,"Right Track",.99,.99))}
    @Test fun namedCleanEditCannotStandInForExplicit(){assertFalse(MatchPolicy.editionAllowed(true,false,"Right Track (Clean)",1.0,1.0))}
    @Test fun radioEditCannotStandInForExplicit(){assertFalse(MatchPolicy.editionAllowed(true,false,"Right Track - Radio Edit",1.0,1.0))}
    @Test fun unknownBadgeNeedsTitleAndDuration(){assertFalse(MatchPolicy.editionAllowed(true,false,"Other Track",.6,1.0));assertFalse(MatchPolicy.editionAllowed(true,false,"Right Track",1.0,null))}
    @Test fun cleanRequestStillRejectsExplicit(){assertFalse(MatchPolicy.editionAllowed(false,true,"Right Track",1.0,1.0))}
    @Test fun explicitBadgeIsAccepted(){assertTrue(MatchPolicy.editionAllowed(true,true,"Right Track",.95,.99))}
}
