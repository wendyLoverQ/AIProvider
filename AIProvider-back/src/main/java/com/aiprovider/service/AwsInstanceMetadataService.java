package com.aiprovider.service;

import com.aiprovider.model.vo.CloudServerMonitorVO;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@Service
public class AwsInstanceMetadataService {
    private static final String ROOT="http://169.254.169.254/latest/";
    private final RestTemplate http;
    private volatile Cache cache;

    public AwsInstanceMetadataService() {
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000); factory.setReadTimeout(1000); http=new RestTemplate(factory);
    }

    public CloudServerMonitorVO.Instance current() {
        Cache snapshot=cache; Instant now=Instant.now();
        if(snapshot!=null&&Duration.between(snapshot.at,now).compareTo(Duration.ofHours(6))<0)return snapshot.value;
        synchronized(this){snapshot=cache;now=Instant.now();if(snapshot!=null&&Duration.between(snapshot.at,now).compareTo(Duration.ofHours(6))<0)return snapshot.value;
            CloudServerMonitorVO.Instance value=fetch();cache=new Cache(now,value);return value;}
    }

    private CloudServerMonitorVO.Instance fetch() {
        try {
            HttpHeaders tokenHeaders=new HttpHeaders(); tokenHeaders.set("X-aws-ec2-metadata-token-ttl-seconds","21600");
            String token=http.exchange(ROOT+"api/token",HttpMethod.PUT,new HttpEntity<Object>(tokenHeaders),String.class).getBody();
            HttpHeaders headers=new HttpHeaders(); headers.set("X-aws-ec2-metadata-token",token==null?"":token);
            String az=get("meta-data/placement/availability-zone",headers);
            String role=getOptional("meta-data/iam/security-credentials/",headers);
            return new CloudServerMonitorVO.Instance(get("meta-data/instance-id",headers),get("meta-data/instance-type",headers),
                az!=null&&az.length()>1?az.substring(0,az.length()-1):null,az,getOptional("meta-data/public-ipv4",headers),
                getOptional("meta-data/local-ipv4",headers),getOptional("meta-data/ami-id",headers),operatingSystem(),
                role!=null&&!role.trim().isEmpty(),role!=null&&!role.trim().isEmpty()?"IAM_ROLE_AVAILABLE":"NO_IAM_ROLE");
        } catch(Exception exception) {
            return new CloudServerMonitorVO.Instance(null,null,null,null,null,null,null,operatingSystem(),false,"IMDS_UNAVAILABLE");
        }
    }
    private String get(String path,HttpHeaders headers){return http.exchange(ROOT+path,HttpMethod.GET,new HttpEntity<Object>(headers),String.class).getBody();}
    private String getOptional(String path,HttpHeaders headers){try{return get(path,headers);}catch(Exception ignored){return null;}}
    private static String operatingSystem(){
        try{for(String line:Files.readAllLines(Paths.get("/etc/os-release"),StandardCharsets.UTF_8))if(line.startsWith("PRETTY_NAME="))return line.substring(12).replace("\"","");}catch(Exception ignored){}
        return System.getProperty("os.name")+" "+System.getProperty("os.version");
    }
    private static class Cache{final Instant at;final CloudServerMonitorVO.Instance value;Cache(Instant at,CloudServerMonitorVO.Instance value){this.at=at;this.value=value;}}
}
