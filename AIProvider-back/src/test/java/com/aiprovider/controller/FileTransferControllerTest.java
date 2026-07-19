package com.aiprovider.controller;

import com.aiprovider.model.vo.FileTransferDownload;
import com.aiprovider.model.vo.FileTransferFileVO;
import com.aiprovider.model.vo.FileTransferPreview;
import com.aiprovider.service.FileTransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileTransferControllerTest {
    private MockMvc mvc;
    private FileTransferService service;

    @BeforeEach void setUp() {
        service = mock(FileTransferService.class);
        mvc = MockMvcBuilders.standaloneSetup(new FileTransferController(service))
            .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test void exposesUploadListDownloadAndDeleteContract() throws Exception {
        FileTransferFileVO file = new FileTransferFileVO("device-file.txt", 6, Instant.parse("2026-07-19T01:02:03Z"));
        when(service.upload(any())).thenReturn(file);
        when(service.list()).thenReturn(Collections.singletonList(file));
        when(service.download("device-file.txt")).thenReturn(new FileTransferDownload(
            "device-file.txt", 6, new ByteArrayResource("second".getBytes("UTF-8"))));
        when(service.preview("picture.png")).thenReturn(new FileTransferPreview(
            "picture.png", 5, "image/png", new ByteArrayResource("image".getBytes("UTF-8"))));
        when(service.readText()).thenReturn("跨设备文本");

        mvc.perform(multipart("/api/file-transfer/upload")
                .file(new MockMultipartFile("file", "device-file.txt", "text/plain", "second".getBytes("UTF-8"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.fileName").value("device-file.txt"));
        mvc.perform(get("/api/file-transfer/files"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].fileSize").value(6));
        mvc.perform(get("/api/file-transfer/download/{fileName}", "device-file.txt"))
            .andExpect(status().isOk()).andExpect(header().string("Content-Length", "6"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
            .andExpect(content().bytes("second".getBytes("UTF-8")));
        mvc.perform(get("/api/file-transfer/preview/{fileName}", "picture.png"))
            .andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline;")))
            .andExpect(content().bytes("image".getBytes("UTF-8")));
        mvc.perform(get("/api/file-transfer/text"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.text").value("跨设备文本"));
        mvc.perform(post("/api/file-transfer/text").contentType("application/json").content("{\"text\":\"新文本\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.text").value("新文本"));
        verify(service).saveText("新文本");
        mvc.perform(delete("/api/file-transfer/{fileName}", "device-file.txt"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.deleted").value("device-file.txt"));
        verify(service).delete("device-file.txt");
    }

    @Test void streamsBatchDownloadAsZipAttachment() throws Exception {
        when(service.downloadBatch(Arrays.asList("one.txt", "two.txt")))
            .thenReturn(output -> output.write("zip".getBytes("UTF-8")));
        org.springframework.test.web.servlet.MvcResult result = mvc.perform(post("/api/file-transfer/download-batch")
                .param("fileName", "one.txt", "two.txt"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
            .andReturn();
        mvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/zip"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
            .andExpect(content().bytes("zip".getBytes("UTF-8")));
    }
}
