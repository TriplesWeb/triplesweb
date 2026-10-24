package com.example.triplesweb.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CosmoAccountRedirectTest {
    @Autowired MockMvc mvc;

    @Test void oldAccountAddressOpensNicknameSearch() throws Exception {
        mvc.perform(get("/cosmo/account"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cosmo/collection"));
        mvc.perform(get("/cosmo/collection"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("닉네임을 검색하면")))
                .andExpect(content().string(not(containsString("COSMO 계정 연결"))))
                .andExpect(content().string(not(containsString("내 COSMO"))));
    }

    @Test void gravityStillOffersAccountConnection() throws Exception {
        mvc.perform(get("/cosmo/gravity"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("COSMO 계정 연결")))
                .andExpect(content().string(not(containsString("class=\"gv-pill\" href=\"/cosmo/collection\""))));
    }
}
