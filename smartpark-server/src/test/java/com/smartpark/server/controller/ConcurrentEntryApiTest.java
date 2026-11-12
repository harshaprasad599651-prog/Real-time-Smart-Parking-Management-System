package com.smartpark.server.controller;

import com.smartpark.server.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Simultaneous HTTP requests from many "operators" against the same slot / vehicle. */
class ConcurrentEntryApiTest extends AbstractApiIntegrationTest {

    @Test
    void twentySimultaneousEntriesForOneSlotYieldOne201() throws Exception {
        List<Callable<Response>> requests = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String vehicle = "RACE%03d".formatted(i);
            requests.add(() -> enter(vehicle, "CAR", "A-01"));
        }

        List<Response> responses = fireTogether(requests);

        Map<Integer, Long> byStatus = responses.stream().collect(Collectors.groupingBy(Response::status, Collectors.counting()));
        assertThat(byStatus).containsEntry(201, 1L).containsEntry(409, 19L);
        assertThat(responses).filteredOn(r -> r.status() == 409)
                .allSatisfy(r -> assertThat(r.body()).containsAnyOf("SLOT_UNAVAILABLE", "CONCURRENT_UPDATE"));
        assertThat((List<?>) get("/api/parking/sessions/active").json("$")).hasSize(1);
    }

    @Test
    void simultaneousExitsOfOneVehicleYieldOne200() throws Exception {
        enter("ABC123", "CAR", "A-01").expect(201);
        List<Callable<Response>> requests = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            requests.add(() -> leave("ABC123"));
        }

        Map<Integer, Long> byStatus = fireTogether(requests).stream()
                .collect(Collectors.groupingBy(Response::status, Collectors.counting()));

        assertThat(byStatus).containsEntry(200, 1L).containsEntry(404, 9L);
        assertThat((String) get("/api/slots/A-01").json("$.status")).isEqualTo("AVAILABLE");
    }

    private static List<Response> fireTogether(List<Callable<Response>> requests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Response>> futures = requests.stream()
                    .map(call -> pool.submit(() -> {
                        go.await();
                        return call.call();
                    })).toList();
            go.countDown();
            List<Response> responses = new ArrayList<>();
            for (Future<Response> f : futures) {
                responses.add(f.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }
}
