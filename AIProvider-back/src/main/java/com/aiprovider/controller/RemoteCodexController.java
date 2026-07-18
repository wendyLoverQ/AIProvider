package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.service.RemoteCodexService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/remote-codex")
public class RemoteCodexController {
    private final RemoteCodexService service; public RemoteCodexController(RemoteCodexService service){this.service=service;}
    private void authorize(String token){service.authorize(token);}
    @GetMapping("/status") public Result<Map<String,Object>> status(@RequestHeader(value="X-Remote-Codex-Token",required=false) String token){authorize(token);return Result.success(service.status());}
    @PostMapping("/login") public Result<Map<String,Object>> login(@RequestHeader("X-Remote-Codex-Token") String token){authorize(token);return Result.success(service.startLogin());}
    @GetMapping("/quota") public Result<Map<String,Object>> quota(@RequestHeader("X-Remote-Codex-Token") String token){authorize(token);return Result.success(service.quota());}
    @GetMapping("/conversations") public Result<List<Map<String,Object>>> list(@RequestHeader("X-Remote-Codex-Token") String token){authorize(token);return Result.success(service.list());}
    @PostMapping("/conversations") public Result<Map<String,Object>> create(@RequestHeader("X-Remote-Codex-Token") String token){authorize(token);return Result.success(service.create());}
    @GetMapping("/conversations/{id}") public Result<Map<String,Object>> get(@RequestHeader("X-Remote-Codex-Token") String token,@PathVariable String id){authorize(token);return Result.success(service.conversation(id));}
    @PostMapping("/conversations/{id}/messages") public Result<Map<String,Object>> send(@RequestHeader("X-Remote-Codex-Token") String token,@PathVariable String id,@RequestBody Map<String,String> body){authorize(token);return Result.success(service.send(id,body.get("content")));}
}
