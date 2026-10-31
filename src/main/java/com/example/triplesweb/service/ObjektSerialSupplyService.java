package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** 발행된 시리얼 집합만 사용한다. 최대 번호나 토큰 ID로 발행량을 추정하지 않는다. */
@Service
public class ObjektSerialSupplyService {
    public record Range(long first, long last) {}
    public record Snapshot(long count, List<Range> ranges, long observedAt) {}
    private static final long TTL=60_000, MAX_STALE=300_000;
    private static final int CACHE_SIZE=128;
    private final RestClient client;
    private final Map<String, Entry> cache=new LinkedHashMap<>(16,.75f,true);
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(64), task->{Thread t=new Thread(task,"objekt-serial-supply");t.setDaemon(true);return t;});
    private static class Entry { volatile Snapshot snapshot; volatile boolean loading; volatile long retryAt; }

    public ObjektSerialSupplyService(@Value("${objekt.api.base-url:https://objekt.top/api/v1}") String baseUrl) {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(6));
        client=RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public static Snapshot analyze(JsonNode body, long now) {
        JsonNode values=body==null?null:body.get("serials");
        if(values==null||!values.isArray()||values.size()>500_000||body.path("hasNext").asBoolean()
                ||!body.path("nextCursor").isMissingNode()&&!body.path("nextCursor").isNull())
            throw new IllegalStateException("완전한 발행 시리얼 목록이 아닙니다.");
        long[] serials=new long[values.size()];int i=0;
        for(JsonNode value:values){
            if(!value.isIntegralNumber()||!value.canConvertToLong()||value.asLong()<1||value.asLong()>9_007_199_254_740_991L)
                throw new IllegalStateException("발행 시리얼 응답이 올바르지 않습니다.");
            serials[i++]=value.asLong();
        }
        Arrays.sort(serials);
        List<Range> ranges=new ArrayList<>();long count=0,first=0,last=0;
        for(long serial:serials){
            if(count>0&&serial==last)continue;
            if(count==0){first=last=serial;}
            else if(serial==last+1){last=serial;}
            else{ranges.add(new Range(first,last));first=last=serial;}
            count++;
        }
        if(count>0)ranges.add(new Range(first,last));
        return new Snapshot(count,List.copyOf(ranges),now);
    }

    private Entry request(String slug) {
        if(slug==null||!slug.matches("[A-Za-z0-9_-]{1,220}"))return new Entry();
        Entry entry;
        synchronized(cache){
            entry=cache.computeIfAbsent(slug,key->new Entry());
            while(cache.size()>CACHE_SIZE)cache.remove(cache.keySet().iterator().next());
        }
        long now=System.currentTimeMillis();
        synchronized(entry){
            if(!entry.loading&&now>=entry.retryAt&&(entry.snapshot==null||now-entry.snapshot.observedAt()>=TTL)){
                entry.loading=true;
                Entry target=entry;
                try{worker.execute(()->{
                    try{target.snapshot=analyze(client.get().uri("/collections/{slug}/serials",slug).retrieve().body(JsonNode.class),System.currentTimeMillis());}
                    catch(RuntimeException failure){target.retryAt=System.currentTimeMillis()+15_000;}
                    finally{target.loading=false;}
                });}catch(RejectedExecutionException busy){entry.loading=false;entry.retryAt=now+2_000;}
            }
        }
        return entry;
    }

    public Map<String,Object> view(String slug,int offset,int limit){
        Entry entry=request(slug);Snapshot snapshot=entry.snapshot;
        long now=System.currentTimeMillis();
        if(snapshot==null||now-snapshot.observedAt()>MAX_STALE)return Map.of("status",entry.loading?"loading":"unavailable");
        int start=Math.min(Math.max(0,offset),snapshot.ranges().size()),end=Math.min(snapshot.ranges().size(),start+Math.min(100,Math.max(1,limit)));
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("status","available");result.put("count",snapshot.count());
        result.put("ranges",snapshot.ranges().subList(start,end));result.put("totalRanges",snapshot.ranges().size());
        result.put("hasMore",end<snapshot.ranges().size());result.put("nextOffset",end);
        result.put("observedAt",Instant.ofEpochMilli(snapshot.observedAt()).toString());
        result.put("stale",now-snapshot.observedAt()>=TTL);result.put("source","objekt.top");
        return result;
    }

    public List<Map<String,Object>> decorate(List<Map<String,Object>> items){
        return items.stream().map(item->{
            Map<String,Object> copy=new LinkedHashMap<>(item);copy.remove("mintCount");copy.remove("mintCountKnown");
            Map<String,Object> state=view(String.valueOf(item.getOrDefault("slug","")),0,1);
            copy.put("supplyStatus",state.get("status"));
            if("available".equals(state.get("status"))){copy.put("mintCount",state.get("count"));copy.put("mintCountKnown",true);copy.put("supplyObservedAt",state.get("observedAt"));}
            return copy;
        }).toList();
    }
    @PreDestroy public void close(){worker.shutdownNow();}
}
