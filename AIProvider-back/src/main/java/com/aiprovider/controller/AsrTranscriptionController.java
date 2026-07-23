package com.aiprovider.controller;

import com.aiprovider.model.vo.AsrApiResponse;
import com.aiprovider.service.AsrTranscriptionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/asr/transcriptions")
public class AsrTranscriptionController {
    private final AsrTranscriptionService service;public AsrTranscriptionController(AsrTranscriptionService service){this.service=service;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public AsrApiResponse transcribe(@RequestParam("audio") MultipartFile audio,@RequestParam(required=false) String sessionId,@RequestParam(defaultValue="zh") String language,@RequestParam String requestId){return AsrApiResponse.success(service.transcribe(audio,sessionId,language,requestId));}
}
