package com.example.triplesweb.controller;

import com.example.triplesweb.service.GravityService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class CosmoGravityDetailController {
    private final GravityService service;
    public CosmoGravityDetailController(GravityService service) { this.service = service; }

    @GetMapping("/cosmo/gravity/{id:[0-9]+}")
    public String detail(@PathVariable("id") long id,
            @RequestParam(name = "pollId", required = false) Long selectedPoll, Model model) {
        try {
            JsonNode gravity = service.fetchGravityDetails(id);
            model.addAttribute("gravity", service.toMap(gravity));
            model.addAttribute("bannerImageUrl", GravityService.safeImage(gravity.path("bannerImageUrl").asText("")));
            List<Map<String, Object>> polls = new ArrayList<>();
            Long pollId = selectedPoll;
            boolean found = false;
            for (JsonNode poll : gravity.path("polls")) {
                long current = poll.path("id").asLong();
                if (pollId == null) pollId = current;
                Map<String, Object> row = new LinkedHashMap<>(service.toMap(poll));
                row.put("selected", pollId == current);
                polls.add(row);
                if (pollId == current) found = true;
            }
            model.addAttribute("polls", polls);
            if (selectedPoll != null && !found) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if (pollId != null) {
                // Render official COSMO results without third-party aggregation calls.
                for (JsonNode poll : gravity.path("polls")) {
                    if (poll.path("id").asLong() == pollId) {
                        model.addAttribute("selectedPoll", service.toMap(poll));
                        model.addAttribute("officialResults", service.toMap(poll.path("result")));
                        model.addAttribute("hasOfficialResults", poll.path("result").path("voteResults").size() > 0);
                    }
                }

            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            model.addAttribute("detailError", GravityService.describeApiError(e));
        } catch (Exception e) {
            model.addAttribute("detailError", GravityService.describeApiError(e));
        }
        return "cosmo/gravity-detail";
    }

}
