package com.aiprovider.service;

import com.aiprovider.repository.RemoteCodexRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
public class RemoteCodexService {
    private final RemoteCodexRepository repository; private final ObjectMapper json; private final String command,workingDirectory;
    private final ExecutorService turns=Executors.newCachedThreadPool(); private final ExecutorService loginWorker=Executors.newSingleThreadExecutor(); private final ExecutorService quotaWorker=Executors.newSingleThreadExecutor();
    private final Map<String,LiveTurn> liveTurns=new ConcurrentHashMap<String,LiveTurn>();
    private final Map<String,CopyOnWriteArrayList<SseEmitter>> streams=new ConcurrentHashMap<String,CopyOnWriteArrayList<SseEmitter>>();
    private volatile Process loginProcess; private volatile String loginOutput=""; private volatile String loginState="IDLE";
    private volatile Instant authCheckedAt=Instant.EPOCH; private volatile boolean loggedIn;

    public RemoteCodexService(RemoteCodexRepository repository,ObjectMapper json,
        @Value("${remote-codex.command:codex}") String command,
        @Value("${remote-codex.working-directory:}") String workingDirectory) {
        this.repository=repository;this.json=json;this.command=command;this.workingDirectory=workingDirectory;
    }

    @PostConstruct public void recoverInterruptedTurns(){repository.recoverInterrupted(LocalDateTime.now());}

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
    public Map<String,Object> conversation(String id){Map<String,Object> value=new LinkedHashMap<String,Object>(repository.get(id));value.put("messages",repository.messages(id));LiveTurn live=liveTurns.get(id);if(live!=null){value.put("liveMessage",live.message.toString());value.put("liveEvents",new ArrayList<Map<String,Object>>(live.events));value.put("model",live.model);value.put("turnId",live.turnId);}return value;}
    public Map<String,Object> send(String id,String prompt,String model) {
        String text=prompt==null?"":prompt.trim();if(text.isEmpty())throw new IllegalArgumentException("请输入对话内容");if(text.length()>20000)throw new IllegalArgumentException("单条消息不能超过 20000 字");
        Map<String,Object> current=repository.get(id);if("RUNNING".equals(current.get("status")))throw new IllegalArgumentException("当前对话仍在执行，请等待回复完成");
        if(!checkLogin())throw new RemoteCodexException("远程 Codex 尚未登录");LocalDateTime now=LocalDateTime.now();repository.message(id,"user",text,now);repository.running(id,now);
        String threadId=current.get("codexThreadId")==null?null:String.valueOf(current.get("codexThreadId"));turns.submit(()->runTurn(id,threadId,text,cleanModel(model)));return conversation(id);
    }

    public Map<String,Object> steer(String id,String prompt){String text=validatePrompt(prompt);LiveTurn live=requireLive(id);repository.message(id,"user",text,LocalDateTime.now());try{ObjectNode params=json.createObjectNode();params.put("threadId",live.threadId);params.put("expectedTurnId",live.turnId);params.set("input",input(text));live.server.request("turn/steer",params);event(live,"user_input","插话",text,"completed");publish(id);return conversation(id);}catch(Exception exception){throw new RemoteCodexException("Codex 插话失败："+message(exception));}}
    public Map<String,Object> interrupt(String id){LiveTurn live=requireLive(id);try{ObjectNode params=json.createObjectNode();params.put("threadId",live.threadId);params.put("turnId",live.turnId);live.server.request("turn/interrupt",params);event(live,"turn","已请求停止","正在中断当前执行","inProgress");publish(id);return conversation(id);}catch(Exception exception){throw new RemoteCodexException("Codex 中断失败："+message(exception));}}
    public Object models(){RemoteCodexAppServer server=null;try{server=new RemoteCodexAppServer(command,json,event->{},error->{});return json.convertValue(server.request("model/list",json.createObjectNode()),Map.class);}catch(Exception exception){throw new RemoteCodexException("Codex 模型读取失败："+message(exception));}finally{if(server!=null)server.close();}}
    public SseEmitter subscribe(String id){repository.get(id);SseEmitter emitter=new SseEmitter(0L);streams.computeIfAbsent(id,key->new CopyOnWriteArrayList<SseEmitter>()).add(emitter);Runnable remove=()->streams.getOrDefault(id,new CopyOnWriteArrayList<SseEmitter>()).remove(emitter);emitter.onCompletion(remove);emitter.onTimeout(remove);emitter.onError(error->remove.run());sendEvent(id,emitter);return emitter;}

    private void runTurn(String conversationId,String existingThread,String prompt,String model) {LiveTurn live=new LiveTurn(conversationId,model);liveTurns.put(conversationId,live);try{live.server=new RemoteCodexAppServer(command,json,event->onEvent(live,event),error->fail(live,error));ObjectNode thread=json.createObjectNode();thread.put("approvalPolicy","never");thread.put("sandbox","danger-full-access");thread.put("cwd",resolveWorkingDirectory().toString());if(model!=null)thread.put("model",model);JsonNode started;if(existingThread==null||existingThread.isEmpty())started=live.server.request("thread/start",thread);else{thread.put("threadId",existingThread);started=live.server.request("thread/resume",thread);}live.threadId=started.path("thread").path("id").asText(existingThread);ObjectNode turn=json.createObjectNode();turn.put("threadId",live.threadId);turn.set("input",input(prompt));if(model!=null)turn.put("model",model);JsonNode response=live.server.request("turn/start",turn);live.turnId=response.path("turn").path("id").asText();publish(conversationId);}catch(Exception exception){fail(live,exception);}}

    List<String> turnCommand(String threadId) {
        List<String> args=new ArrayList<String>();args.add(command);args.add("exec");
        if(threadId!=null&&!threadId.isEmpty()){args.add("resume");args.add("--json");args.add("--skip-git-repo-check");args.add("--dangerously-bypass-approvals-and-sandbox");args.add(threadId);args.add("-");}
        else {Path cwd=resolveWorkingDirectory();args.add("--json");args.add("--color");args.add("never");args.add("--dangerously-bypass-approvals-and-sandbox");args.add("--skip-git-repo-check");args.add("-C");args.add(cwd.toString());args.add("-");}
        return args;
    }

    private ArrayNode input(String text){ArrayNode input=json.createArrayNode();ObjectNode item=input.addObject();item.put("type","text");item.put("text",text);return input;}
    private String validatePrompt(String prompt){String text=prompt==null?"":prompt.trim();if(text.isEmpty())throw new IllegalArgumentException("请输入对话内容");if(text.length()>20000)throw new IllegalArgumentException("单条消息不能超过 20000 字");return text;}
    private String cleanModel(String model){String value=model==null?"":model.trim();return value.isEmpty()?null:value;}
    private LiveTurn requireLive(String id){LiveTurn live=liveTurns.get(id);if(live==null||live.server==null||!live.server.isAlive()||live.turnId==null)throw new IllegalArgumentException("当前对话没有可操作的运行任务");return live;}
    private void onEvent(LiveTurn live,JsonNode notification){String method=notification.path("method").asText();JsonNode params=notification.path("params");if("item/agentMessage/delta".equals(method)){live.message.append(params.path("delta").asText());}else if("turn/started".equals(method)){live.turnId=params.path("turn").path("id").asText(live.turnId);event(live,"turn","开始执行","Codex 已开始处理任务","inProgress");}else if("item/started".equals(method)||"item/completed".equals(method)){JsonNode item=params.path("item");String type=item.path("type").asText("tool");String title=item.path("command").asText(item.path("name").asText(type));String detail=item.path("aggregatedOutput").asText(item.path("path").asText(item.toString()));event(live,type,title,detail,"item/completed".equals(method)?"completed":"inProgress");}else if("turn/completed".equals(method)){String status=params.path("turn").path("status").asText("completed");if(live.finished)return;live.finished=true;String reply=live.message.toString().trim();if(!reply.isEmpty())repository.message(live.conversationId,"assistant",reply,LocalDateTime.now());if("failed".equalsIgnoreCase(status))repository.failed(live.conversationId,params.path("turn").path("error").path("message").asText("Codex 执行失败"),LocalDateTime.now());else repository.completed(live.conversationId,live.threadId,LocalDateTime.now());publish(live.conversationId);closeLater(live);}else if("error".equals(method)){event(live,"error","Codex 错误",params.path("message").asText(params.toString()),"failed");}else if("app-server/stderr".equals(method)){event(live,"stderr","Codex 输出",params.path("message").asText(),"inProgress");}publish(live.conversationId);}
    private void event(LiveTurn live,String type,String title,String detail,String status){Map<String,Object> value=new LinkedHashMap<String,Object>();value.put("id",UUID.randomUUID().toString());value.put("type",type);value.put("title",title);value.put("detail",detail);value.put("status",status);value.put("createdAt",LocalDateTime.now().toString());live.events.add(value);while(live.events.size()>120)live.events.remove(0);}
    private void fail(LiveTurn live,Throwable exception){if(live.finished)return;live.finished=true;repository.failed(live.conversationId,message(exception),LocalDateTime.now());publish(live.conversationId);closeLater(live);}
    private String message(Throwable exception){return exception.getMessage()==null?"Codex 执行失败":exception.getMessage();}
    private void closeLater(LiveTurn live){turns.submit(()->{try{Thread.sleep(1500);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}liveTurns.remove(live.conversationId,live);if(live.server!=null)live.server.close();publish(live.conversationId);});}
    private void publish(String id){for(SseEmitter emitter:streams.getOrDefault(id,new CopyOnWriteArrayList<SseEmitter>()))sendEvent(id,emitter);}
    private void sendEvent(String id,SseEmitter emitter){try{emitter.send(SseEmitter.event().name("conversation").data(conversation(id)));}catch(Exception exception){emitter.complete();streams.getOrDefault(id,new CopyOnWriteArrayList<SseEmitter>()).remove(emitter);}}
    private static final class LiveTurn {final String conversationId,model;final StringBuilder message=new StringBuilder();final List<Map<String,Object>> events=new CopyOnWriteArrayList<Map<String,Object>>();volatile RemoteCodexAppServer server;volatile String threadId,turnId;volatile boolean finished;LiveTurn(String conversationId,String model){this.conversationId=conversationId;this.model=model;}}

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
    @PreDestroy public void shutdown(){for(LiveTurn live:liveTurns.values())if(live.server!=null)live.server.close();for(CopyOnWriteArrayList<SseEmitter> values:streams.values())for(SseEmitter emitter:values)emitter.complete();turns.shutdownNow();loginWorker.shutdownNow();quotaWorker.shutdownNow();Process value=loginProcess;if(value!=null&&value.isAlive())value.destroyForcibly();}
}
