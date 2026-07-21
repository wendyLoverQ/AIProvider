package com.aiprovider.model.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AsrApiResponseTest {
    @Test void serializesPublicContractWithTextField(){AsrRecordVO record=new AsrRecordVO();record.setRecordId("asr_1");record.setRecognizedText("今天天气很好");record.setCharacterId("character_001");record.setProvider("groq");record.setModel("whisper-large-v3");record.setLanguage("zh");JsonNode json=new ObjectMapper().valueToTree(AsrApiResponse.success(record));assertTrue(json.path("success").asBoolean());assertEquals("今天天气很好",json.path("data").path("text").asText());assertFalse(json.path("data").has("recognizedText"));}
}
