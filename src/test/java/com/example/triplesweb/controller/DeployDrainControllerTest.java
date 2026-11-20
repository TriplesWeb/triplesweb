package com.example.triplesweb.controller;

import com.example.triplesweb.config.InFlightRequestTracker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeployDrainController.class)
@AutoConfigureMockMvc(addFilters = false)
class DeployDrainControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean InFlightRequestTracker tracker;

    @Test
    void reportsInFlightCount() throws Exception {
        when(tracker.current()).thenReturn(3);
        mvc.perform(get("/internal/deploy/inflight"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inFlight").value(3));
    }
}
