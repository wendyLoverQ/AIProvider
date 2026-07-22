package com.aiprovider.service;

import com.aiprovider.mapper.AsrRecordMapper;
import com.aiprovider.model.vo.AsrQuotaVO;
import com.aiprovider.model.vo.AsrRecordVO;
import com.aiprovider.repository.AsrRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AsrTranscriptionServiceTest {
    @TempDir Path temp;

    @Test void persistsAndReturnsSuccessfulTranscriptionWithoutLoggingContent() throws Exception {
        AsrRecordRepository repository=mock(AsrRecordRepository.class);AsrProviderClient client=mock(AsrProviderClient.class);AsrTranscriptionService service=service(repository,client);when(repository.findByRequestId(anyString())).thenReturn(null);
        when(repository.findCharacterName("character_001")).thenReturn("小爱");doAnswer(invocation->{AsrRecordMapper.Record row=invocation.getArgument(0);row.setId(7L);return 1;}).when(repository).insert(any());when(repository.assignRecordId(eq(7L),anyString())).thenReturn(1);when(client.transcribe(any(),eq("whisper-large-v3"),eq("zh"))).thenReturn(new AsrProviderClient.Result("今天天气很好",8420L,2000L,1997L,"3h"));when(repository.markSuccess(eq(7L),eq("今天天气很好"),eq(8420L),anyLong(),eq(2000L),eq(1997L),eq("3h"))).thenReturn(1);when(repository.findByRecordId(anyString())).thenReturn(successRow());
        AsrRecordVO result=service.transcribe(audio(),"character_001","session_001","zh","request_001");
        assertEquals("今天天气很好",result.getRecognizedText());assertEquals("小爱",result.getCharacterNameSnapshot());verify(client).transcribe(any(),eq("whisper-large-v3"),eq("zh"));verify(repository).markSuccess(eq(7L),eq("今天天气很好"),eq(8420L),anyLong(),eq(2000L),eq(1997L),eq("3h"));
    }

    @Test void replaysCompletedRequestWithoutCallingProviderAgain() throws Exception {
        AsrRecordRepository repository=mock(AsrRecordRepository.class);AsrProviderClient client=mock(AsrProviderClient.class);AsrTranscriptionService service=service(repository,client);when(repository.findByRequestId("request_001")).thenReturn(successRow());
        AsrRecordVO result=service.transcribe(audio(),"character_001",null,"zh","request_001");
        assertEquals("asr_20260722_000007",result.getRecordId());verifyNoInteractions(client);verify(repository,never()).insert(any());
    }

    @Test void transcribesAudioWithoutCharacterMetadata() throws Exception {
        AsrRecordRepository repository=mock(AsrRecordRepository.class);AsrProviderClient client=mock(AsrProviderClient.class);AsrTranscriptionService service=service(repository,client);when(repository.findByRequestId(anyString())).thenReturn(null);
        doAnswer(invocation->{AsrRecordMapper.Record row=invocation.getArgument(0);assertNull(row.getCharacterId());assertNull(row.getCharacterNameSnapshot());row.setId(8L);return 1;}).when(repository).insert(any());when(repository.assignRecordId(eq(8L),anyString())).thenReturn(1);when(client.transcribe(any(),eq("whisper-large-v3"),eq("zh"))).thenReturn(new AsrProviderClient.Result("纯音频转写",1000L,null,null,null));when(repository.markSuccess(eq(8L),eq("纯音频转写"),eq(1000L),anyLong(),isNull(),isNull(),isNull())).thenReturn(1);when(repository.findByRecordId(anyString())).thenReturn(successRow());
        service.transcribe(audio(),null,null,"zh","request_audio_only");verify(repository,never()).findCharacterName(anyString());verify(client).transcribe(any(),eq("whisper-large-v3"),eq("zh"));
    }

    @Test void recordsProviderFailureAndReturnsStablePublicError() throws Exception {
        AsrRecordRepository repository=mock(AsrRecordRepository.class);AsrProviderClient client=mock(AsrProviderClient.class);AsrTranscriptionService service=service(repository,client);when(repository.findByRequestId(anyString())).thenReturn(null);doAnswer(invocation->{AsrRecordMapper.Record row=invocation.getArgument(0);row.setId(7L);return 1;}).when(repository).insert(any());when(repository.assignRecordId(eq(7L),anyString())).thenReturn(1);when(client.transcribe(any(),anyString(),anyString())).thenThrow(new AsrProviderException("ASR_PROVIDER_HTTP_429","upstream rate limit",null));when(repository.markFailed(eq(7L),anyLong(),eq("ASR_PROVIDER_HTTP_429"),eq("语音识别失败"))).thenReturn(1);
        AsrTranscriptionException error=assertThrows(AsrTranscriptionException.class,()->service.transcribe(audio(),"character_001",null,"zh","request_001"));
        assertEquals("ASR_TRANSCRIPTION_FAILED",error.getCode());assertEquals("request_001",error.getRequestId());verify(repository).markFailed(eq(7L),anyLong(),eq("ASR_PROVIDER_HTTP_429"),eq("语音识别失败"));
    }

    @Test void reportsProviderRequestSnapshotAndRecordedAudioUsage(){AsrRecordRepository repository=mock(AsrRecordRepository.class);AsrTranscriptionService service=service(repository,mock(AsrProviderClient.class));Map<String,Object> snapshot=new HashMap<>();snapshot.put("requestLimit",2000L);snapshot.put("requestsRemaining",1997L);snapshot.put("requestsResetAfter","3h");snapshot.put("capturedAt",LocalDateTime.of(2026,7,22,12,0));when(repository.findLatestQuotaSnapshot("groq","whisper-large-v3")).thenReturn(snapshot);when(repository.sumAudioDurationMs(eq("groq"),eq("whisper-large-v3"),any(),any())).thenReturn(4500L,12500L);AsrQuotaVO quota=service.quota();assertEquals(1997L,quota.getDailyRequestsRemaining());assertEquals(5L,quota.getHourlyAudioUsedSeconds());assertEquals(13L,quota.getDailyAudioUsedSeconds());assertEquals(7195L,quota.getHourlyAudioRemainingSeconds());assertEquals(28787L,quota.getDailyAudioRemainingSeconds());assertEquals("AIPROVIDER_RECORDED",quota.getAudioUsageScope());}

    @Test void convertsShanghaiQuotaWindowsToUtcDatabaseTime(){LocalDateTime[] window=AsrTranscriptionService.quotaWindows(Instant.parse("2026-07-21T18:30:00Z"));assertEquals(LocalDateTime.of(2026,7,21,17,30),window[0]);assertEquals(LocalDateTime.of(2026,7,21,16,0),window[1]);assertEquals(LocalDateTime.of(2026,7,21,18,30),window[2]);}

    private AsrTranscriptionService service(AsrRecordRepository repository,AsrProviderClient client){return new AsrTranscriptionService(repository,client,temp.toString(),"groq","whisper-large-v3",26214400L,7200L,28800L);}
    private MockMultipartFile audio(){return new MockMultipartFile("audio","voice.webm","audio/webm",new byte[]{1,2,3});}
    private Map<String,Object> successRow(){Map<String,Object> row=new HashMap<>();row.put("recordId","asr_20260722_000007");row.put("requestId","request_001");row.put("characterId","character_001");row.put("characterNameSnapshot","小爱");row.put("sessionId","session_001");row.put("audioFormat","webm");row.put("audioSize",3L);row.put("audioDurationMs",8420L);row.put("recognizedText","今天天气很好");row.put("provider","groq");row.put("model","whisper-large-v3");row.put("language","zh");row.put("processingTimeMs",1260L);row.put("status","SUCCESS");row.put("createdAt",LocalDateTime.of(2026,7,22,12,0));return row;}
}
