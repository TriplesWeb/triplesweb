package com.example.triplesweb.controller;

import com.example.triplesweb.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest(value = CosmoObjektSyncController.class, properties = "chemistry.admin.password=test-password")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@Import(AboutAdminAuth.class)
class CosmoObjektSyncControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CosmoObjektSyncService sync;
    @Test void anonymousCannotImport() throws Exception {
        mvc.perform(post("/api/cosmo/objekts/sync")).andExpect(status().isForbidden());
        verifyNoInteractions(sync);
    }
    @Test void adminReceivesCountsOnly() throws Exception {
        when(sync.sync()).thenReturn(new CosmoObjektSyncService.Result(4, 4, 0));
        mvc.perform(post("/api/cosmo/objekts/sync").sessionAttr(AboutAdminAuth.SESSION_ADMIN, true))
                .andExpect(status().isOk()).andExpect(content().json("{\"collections\":4,\"objekts\":4,\"changed\":0}", true));
    }
}
