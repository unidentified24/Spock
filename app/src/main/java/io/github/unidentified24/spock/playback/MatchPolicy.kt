package io.github.unidentified24.spock.playback

/** Missing explicit badges are not proof of a different recording. Named clean
 * edits remain excluded; artist/title/duration guards run before this policy. */
object MatchPolicy {
    fun editionAllowed(wantExplicit:Boolean?,candidateExplicit:Boolean,title:String,titleScore:Double,durationScore:Double?):Boolean = when(wantExplicit) {
        null -> true
        false -> !candidateExplicit
        true -> candidateExplicit || (
            !Regex("\\b(clean|censored|radio edit|edited version)\\b",RegexOption.IGNORE_CASE).containsMatchIn(title) &&
                titleScore>=0.82 && durationScore?.let{it>=0.94}==true)
    }
}
