package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ObjektSerialSupplyServiceTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test void countsUniqueExistingSerialsAndNeverFillsHoles() throws Exception {
        var result=ObjektSerialSupplyService.analyze(json.readTree("{\"serials\":[9,1,2,2,4,10,11],\"spun\":[2]}"),123L);
        assertThat(result.count()).isEqualTo(6);
        assertThat(result.ranges()).containsExactly(new ObjektSerialSupplyService.Range(1,2),new ObjektSerialSupplyService.Range(4,4),new ObjektSerialSupplyService.Range(9,11));
        assertThat(result.observedAt()).isEqualTo(123L);
    }
    @Test void repeatedRequestsShareOneBackgroundFetch() throws Exception {
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var started=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/collections/sample/serials",exchange->{
            calls.incrementAndGet();started.countDown();
            try{release.await(3,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            byte[] body="{\"serials\":[1,3,4]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        var service=new ObjektSerialSupplyService("http://127.0.0.1:"+server.getAddress().getPort());
        try{
            assertThat(service.view("sample",0,40)).containsEntry("status","loading");
            assertThat(started.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            for(int i=0;i<30;i++)assertThat(service.view("sample",0,40)).containsEntry("status","loading");
            release.countDown();long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            java.util.Map<String,Object> state;
            do{Thread.sleep(10);state=service.view("sample",0,40);}while(!"available".equals(state.get("status"))&&System.nanoTime()<deadline);
            assertThat(state).containsEntry("count",3L).containsEntry("status","available");
            assertThat(calls.get()).isEqualTo(1);
        }finally{release.countDown();service.close();server.stop(0);}
    }
    @Test void emptyValidListIsZeroButFailedOrPartialResponseIsNotZero() throws Exception {
        assertThat(ObjektSerialSupplyService.analyze(json.readTree("{\"serials\":[]}"),1).count()).isZero();
        for(String body:new String[]{"{}","{\"serials\":[1],\"hasNext\":true}","{\"serials\":[1],\"nextCursor\":\"more\"}","{\"serials\":[0]}","{\"serials\":[1.5]}","{\"serials\":[9007199254740992]}"}){
            var value=json.readTree(body);assertThatThrownBy(()->ObjektSerialSupplyService.analyze(value,1)).isInstanceOf(IllegalStateException.class);
        }
    }
}
