package com.aiprovider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** One authenticated Codex app-server stdio connection. */
final class RemoteCodexAppServer implements Closeable {
    private final ObjectMapper json;
    private final Process process;
    private final OutputStreamWriter writer;
    private final Consumer<JsonNode> notifications;
    private final Consumer<Throwable> failure;
    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<Long, CompletableFuture<JsonNode>>();

    RemoteCodexAppServer(String command, ObjectMapper json, Consumer<JsonNode> notifications, Consumer<Throwable> failure) throws Exception {
        this.json = json; this.notifications = notifications; this.failure = failure;
        process = new ProcessBuilder(command, "app-server", "--stdio").redirectErrorStream(false).start();
        writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        Thread stdout = new Thread(this::readStdout, "remote-codex-app-server"); stdout.setDaemon(true); stdout.start();
        Thread stderr = new Thread(this::readStderr, "remote-codex-app-server-stderr"); stderr.setDaemon(true); stderr.start();
        ObjectNode params = json.createObjectNode();
        ObjectNode client = params.putObject("clientInfo"); client.put("name", "aiprovider-remote-codex"); client.put("version", "2.0.0");
        params.putObject("capabilities").put("experimentalApi", true);
        request("initialize", params);
        notify("initialized", json.createObjectNode());
    }

    JsonNode request(String method, JsonNode params) throws Exception {
        long id = ids.incrementAndGet(); CompletableFuture<JsonNode> future = new CompletableFuture<JsonNode>(); pending.put(id, future);
        ObjectNode message = json.createObjectNode(); message.put("id", id); message.put("method", method); message.set("params", params);
        write(message); JsonNode response = future.get(30, TimeUnit.SECONDS);
        if (response.has("error")) throw new RemoteCodexException(response.path("error").path("message").asText(response.path("error").toString()));
        return response.path("result");
    }

    void notify(String method, JsonNode params) throws Exception { ObjectNode message=json.createObjectNode();message.put("method",method);message.set("params",params);write(message); }
    private synchronized void write(JsonNode message) throws Exception { writer.write(json.writeValueAsString(message)); writer.write('\n'); writer.flush(); }
    private void readStdout() { try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){JsonNode message=json.readTree(line);if(message.has("id")){CompletableFuture<JsonNode> future=pending.remove(message.path("id").asLong());if(future!=null)future.complete(message);}else if(message.has("method"))notifications.accept(message);}}catch(Throwable exception){if(process.isAlive())failure.accept(exception);}finally{RemoteCodexException ended=new RemoteCodexException("Codex app-server 已结束");for(CompletableFuture<JsonNode> future:pending.values())future.completeExceptionally(ended);pending.clear();} }
    private void readStderr() { try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getErrorStream(),StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){if(!line.trim().isEmpty()){ObjectNode event=json.createObjectNode();event.put("method","app-server/stderr");event.putObject("params").put("message",line);notifications.accept(event);}}}catch(Exception ignored){} }
    boolean isAlive(){return process.isAlive();}
    @Override public void close(){try{writer.close();}catch(Exception ignored){}if(process.isAlive())process.destroy();try{if(!process.waitFor(2,TimeUnit.SECONDS))process.destroyForcibly();}catch(Exception ignored){process.destroyForcibly();}}
}
