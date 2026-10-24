package com.example.triplesweb.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CosmoAccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class CosmoAccountControllerTest {
    @Autowired MockMvc mvc;

    @Test void accountPageGoesToNicknameSearch() throws Exception {
        mvc.perform(get("/cosmo/account"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cosmo/collection"));
    }
}
