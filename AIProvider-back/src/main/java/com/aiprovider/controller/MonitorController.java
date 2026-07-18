package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.model.vo.MonitorSummaryVO;
import com.aiprovider.model.vo.MonitorPageVO;
import com.aiprovider.service.MonitorService;
import com.aiprovider.service.CloudServerMonitorService;
import com.aiprovider.model.vo.CloudServerMonitorVO;
import com.aiprovider.model.vo.AwsBillingMonitorVO;
import com.aiprovider.service.AwsBillingMonitorService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {
    private final MonitorService service; private final CloudServerMonitorService cloudServers; private final AwsBillingMonitorService awsBilling;
    public MonitorController(MonitorService service,CloudServerMonitorService cloudServers,AwsBillingMonitorService awsBilling){this.service=service;this.cloudServers=cloudServers;this.awsBilling=awsBilling;}
    @GetMapping("/summary") public Result<MonitorSummaryVO> summary(){return Result.success(service.summary());}
    @GetMapping("/cloud-servers") public Result<Map<String,CloudServerMonitorVO>> cloudServers(){return Result.success(cloudServers.current());}
    @GetMapping("/aws-billing") public Result<AwsBillingMonitorVO> awsBilling(){return Result.success(awsBilling.current());}
    @GetMapping("/ai-overview") public Result<Map<String,Object>> overview(){return Result.success(service.overview());}
    @GetMapping("/ai-timeseries") public Result<List<Map<String,Object>>> timeseries(@RequestParam(defaultValue="24h") String range){return Result.success(service.timeseries(range));}
    @GetMapping("/providers") public Result<Map<String,Object>> providers(){return Result.success(service.providers());}
    @GetMapping("/failures") public Result<MonitorPageVO<Map<String,Object>>> failures(@RequestParam(defaultValue="24h") String range,@RequestParam(required=false) String provider,@RequestParam(required=false) String model,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return Result.success(service.failures(range,provider,model,page,pageSize));}
}
