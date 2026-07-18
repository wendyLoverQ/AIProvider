package com.aiprovider.service;

import com.aiprovider.repository.RemoteCodexRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
public class RemoteCodexService {
    private final RemoteCodexRepository repository; private final ObjectMapper json; private final String command,workingDirectory,accessToken;
    private final ExecutorService turns=Executors.newFixedThreadPool(2); private final ExecutorService loginWorker=Executors.newSingleThreadExecutor(); private final ExecutorService quotaWorker=Executors.newSingleThreadExecutor();
    private volatile Process loginProcess; private volatile String loginOutput=""; private volatile String loginState="IDLE";
    private volatile Instant authCheckedAt=Instant.EPOCH; private volatile boolean loggedIn;

    public RemoteCodexService(RemoteCodexRepository repository,ObjectMapper json,
        @Value("${remote-codex.command:codex}") String command,
        @Value("${remote-codex.working-directory:}") String workingDirectory,
        @Value("${remote-codex.access-token:}") String accessToken) {
        this.repository=repository;this.json=json;this.command=command;this.workingDirectory=workingDirectory;this.accessToken=accessToken;
    }

    public void authorize(String supplied) {
        if(accessToken==null||accessToken.length()<16)throw new RemoteCodexException("远程 Codex 访问密钥尚未配置");
        byte[] expected=accessToken.getBytes(StandardCharsets.UTF_8),actual=(supplied==null?"":supplied).getBytes(StandardCharsets.UTF_8);
        if(!MessageDigest.isEqual(expected,actual))throw new SecurityException("远程 Codex 访问密钥不正确");
    }
    public Map<String,Object> status() {
        if(Duration.between(authCheckedAt,Instant.now()).getSeconds()>=10){loggedIn=checkLogin();authCheckedAt=Instant.now();}
        Map<String,Object> value=new LinkedHashMap<String,Object>();value.put("available",Files.isExecutable(Paths.get(command))||"codex".equals(command));
        value.put("loggedIn",loggedIn);value.put("loginState",loginState);value.put("loginOutput",loginOutput);value.put("workingDirectory",workingDirectory);return value;
    }
    public Map<String,Object> startLogin() {
        synchronized(this){if(loginProcess!=null&&loginProcess.isAlive())return status();loginOutput="";loginState="RUNNING";
            loginWorker.submit(this::runDeviceLogin);}return status();
    }
    public List<Map<String,Object>> list(){return repository.list();}
    public Map<String,Object> quota() {
        if(!checkLogin())throw new RemoteCodexException("远程 Codex 尚未登录");
        Process process=null;
        try {
            process=new ProcessBuilder(command,"app-server").redirectErrorStream(true).start();
            Process running=process;
            Future<Map<String,Object>> response=quotaWorker.submit(()->readQuota(running));
            return response.get(15,TimeUnit.SECONDS);
        } catch(Exception exception) {
            throw new RemoteCodexException("Codex 额度读取失败："+(exception.getMessage()==null?"未知错误":exception.getMessage()));
        } finally { if(process!=null&&process.isAlive())process.destroyForcibly(); }
    }
    public Map<String,Object> create(){String id=UUID.randomUUID().toString();repository.create(id,"新对话",LocalDateTime.now());return conversation(id);}
    public Map<String,Object> conversation(String id){Map<String,Object> value=new LinkedHashMap<String,Object>(repository.get(id));value.put("messages",repository.messages(id));return value;}
    public Map<String,Object> send(String id,String prompt) {
        String text=prompt==null?"":prompt.trim();if(text.isEmpty())throw new IllegalArgumentException("请输入对话内容");if(text.length()>20000)throw new IllegalArgumentException("单条消息不能超过 20000 字");
        Map<String,Object> current=repository.get(id);if("RUNNING".equals(current.get("status")))throw new IllegalArgumentException("当前对话仍在执行，请等待回复完成");
        if(!checkLogin())throw new RemoteCodexException("远程 Codex 尚未登录");LocalDateTime now=LocalDateTime.now();repository.message(id,"user",text,now);repository.running(id,now);
        String threadId=current.get("codexThreadId")==null?null:String.valueOf(current.get("codexThreadId"));turns.submit(()->runTurn(id,threadId,text));return conversation(id);
    }

    private void runTurn(String conversationId,String threadId,String prompt) {
        String resolvedThread=threadId;String error=null;List<String> replies=new ArrayList<String>();
        try {
            List<String> args=turnCommand(threadId);
            Process process=new ProcessBuilder(args).redirectErrorStream(true).start();try(OutputStreamWriter writer=new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8)){writer.write(prompt);writer.write("\n");}
            StringBuilder raw=new StringBuilder();try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){String line;
                while((line=reader.readLine())!=null){if(raw.length()<12000)raw.append(line).append('\n');try{JsonNode event=json.readTree(line);String type=event.path("type").asText();
                    if("thread.started".equals(type))resolvedThread=event.path("thread_id").asText(resolvedThread);
                    if("item.completed".equals(type)&&"agent_message".equals(event.path("item").path("type").asText())){String reply=event.path("item").path("text").asText();if(!reply.trim().isEmpty())replies.add(reply);}
                    if("error".equals(type))error=event.path("message").asText("Codex 执行失败");}catch(Exception ignored){}}
            }
            if(!process.waitFor(30,TimeUnit.MINUTES)){process.destroyForcibly();throw new RemoteCodexException("Codex 对话执行超过 30 分钟");}
            if(process.exitValue()!=0)throw new RemoteCodexException(error==null?compact(raw.toString()):error);
            if(replies.isEmpty())throw new RemoteCodexException("Codex 没有返回可显示的回复");
            for(String reply:replies)repository.message(conversationId,"assistant",reply,LocalDateTime.now());repository.completed(conversationId,resolvedThread,LocalDateTime.now());
        } catch(Exception exception) {String message=exception.getMessage();repository.failed(conversationId,message==null?"Codex 执行失败":message,LocalDateTime.now());}
    }

    List<String> turnCommand(String threadId) {
        List<String> args=new ArrayList<String>();args.add(command);args.add("exec");
        if(threadId!=null&&!threadId.isEmpty()){args.add("resume");args.add("--json");args.add("--skip-git-repo-check");args.add("--dangerously-bypass-approvals-and-sandbox");args.add(threadId);args.add("-");}
        else {Path cwd=resolveWorkingDirectory();args.add("--json");args.add("--color");args.add("never");args.add("--dangerously-bypass-approvals-and-sandbox");args.add("--skip-git-repo-check");args.add("-C");args.add(cwd.toString());args.add("-");}
        return args;
    }

    private Map<String,Object> readQuota(Process process) throws Exception {
        try(OutputStreamWriter writer=new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8);
            BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))) {
            writer.write("{\"id\":1,\"method\":\"initialize\",\"params\":{\"clientInfo\":{\"name\":\"aiprovider-remote-codex\",\"version\":\"1.0.0\"},\"capabilities\":{}}}\n");writer.flush();
            boolean initialized=false;String line;
            while((line=reader.readLine())!=null){JsonNode message;try{message=json.readTree(stripAnsi(line));}catch(Exception ignored){continue;}
                if(!initialized&&message.path("id").asInt(-1)==1){writer.write("{\"method\":\"initialized\"}\n{\"id\":2,\"method\":\"account/rateLimits/read\",\"params\":{}}\n");writer.flush();initialized=true;continue;}
                if(message.path("id").asInt(-1)==2){if(message.has("error"))throw new RemoteCodexException(message.path("error").toString());JsonNode result=message.path("result");if(result.isMissingNode())throw new RemoteCodexException("Codex 未返回额度数据");return json.convertValue(result,Map.class);}
            }
            throw new RemoteCodexException("Codex 额度接口提前结束");
        }
    }

    private void runDeviceLogin() {
        try {Process process=new ProcessBuilder(command,"login","--device-auth").redirectErrorStream(true).start();loginProcess=process;StringBuilder output=new StringBuilder();
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){if(output.length()<8000)output.append(stripAnsi(line)).append('\n');loginOutput=output.toString();}}
            int code=process.waitFor();loggedIn=code==0&&checkLogin();loginState=loggedIn?"COMPLETED":"ERROR";authCheckedAt=Instant.now();
        } catch(Exception exception){loginOutput=exception.getMessage();loginState="ERROR";} finally {loginProcess=null;}
    }
    private boolean checkLogin(){try{Process process=new ProcessBuilder(command,"login","status").redirectErrorStream(true).start();boolean done=process.waitFor(8,TimeUnit.SECONDS);if(!done)process.destroyForcibly();return done&&process.exitValue()==0;}catch(Exception ignored){return false;}}
    private Path resolveWorkingDirectory(){if(workingDirectory==null||workingDirectory.trim().isEmpty())throw new RemoteCodexException("远程 Codex 工作目录尚未配置");Path path=Paths.get(workingDirectory).toAbsolutePath().normalize();if(!Files.isDirectory(path))throw new RemoteCodexException("远程 Codex 工作目录不存在");return path;}
    private String compact(String value){String clean=stripAnsi(value).trim();return clean.length()>1000?clean.substring(clean.length()-1000):clean;}
    private String stripAnsi(String value){return value==null?"":value.replaceAll("\\x1B\\[[;\\d]*[ -/]*[@-~]","");}
    @PreDestroy public void shutdown(){turns.shutdownNow();loginWorker.shutdownNow();quotaWorker.shutdownNow();Process value=loginProcess;if(value!=null&&value.isAlive())value.destroyForcibly();}
}
