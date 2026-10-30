package io.github.unidentified24.spock.playback
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineFilesTest {
 @get:Rule val temp=TemporaryFolder()
 @Test fun spotifyIdentitySurvivesChangedSearchMetadata(){assertEquals(OfflineFiles.key("spotify:track:abc|Old title"),OfflineFiles.key("spotify:track:abc|New title"))}
 @Test fun localUrisKeepTheirIdentity(){assertEquals("content://music/12",OfflineFiles.key("content://music/12"))}
 @Test fun emptyAndDeletedFilesAreNotSavedAudio(){val file=temp.newFile();assertFalse(OfflineFiles.available(file.path));file.writeText("audio");assertTrue(OfflineFiles.available(file.path));file.delete();assertFalse(OfflineFiles.available(file.path))}
 @Test fun audioContainerHeadersAreRequired(){
  val file=temp.newFile()
  for(header in listOf("fLaC0000","OggS0000","RIFF0000","ID300000","0000ftyp")){file.writeText(header);assertTrue(header,OfflineFiles.audioHeader(file))}
  file.writeText("<html>Error</html>");assertFalse(OfflineFiles.audioHeader(file))
 }
}
