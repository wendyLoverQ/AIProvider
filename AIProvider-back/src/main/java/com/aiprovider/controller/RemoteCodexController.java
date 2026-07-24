package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.service.RemoteCodexService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/remote-codex")
public class RemoteCodexController {
    private final RemoteCodexService service; public RemoteCodexController(RemoteCodexService service){this.service=service;}
    @GetMapping("/status") public Result<Map<String,Object>> status(){return Result.success(service.status());}
    @PostMapping("/login") public Result<Map<String,Object>> login(){return Result.success(service.startLogin());}
    @GetMapping("/quota") public Result<Map<String,Object>> quota(){return Result.success(service.quota());}
    @GetMapping("/models") public Result<Object> models(){return Result.success(service.models());}
    @GetMapping("/conversations") public Result<List<Map<String,Object>>> list(){return Result.success(service.list());}
    @PostMapping("/conversations") public Result<Map<String,Object>> create(){return Result.success(service.create());}
    @GetMapping("/conversations/{id}") public Result<Map<String,Object>> get(@PathVariable String id){return Result.success(service.conversation(id));}
    @PostMapping("/conversations/{id}/messages") public Result<Map<String,Object>> send(@PathVariable String id,@RequestBody Map<String,String> body){return Result.success(service.send(id,body.get("content"),body.get("model")));}
    @PostMapping("/conversations/{id}/steer") public Result<Map<String,Object>> steer(@PathVariable String id,@RequestBody Map<String,String> body){return Result.success(service.steer(id,body.get("content")));}
    @PostMapping("/conversations/{id}/interrupt") public Result<Map<String,Object>> interrupt(@PathVariable String id){return Result.success(service.interrupt(id));}
    @GetMapping(value="/conversations/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE) public ResponseEntity<SseEmitter> events(@PathVariable String id){return ResponseEntity.ok().header("X-Accel-Buffering","no").header("Cache-Control","no-cache").body(service.subscribe(id));}
}
