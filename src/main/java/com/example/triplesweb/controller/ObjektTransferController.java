package com.example.triplesweb.controller;

import com.example.triplesweb.service.ObjektTransferService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import java.util.Map;

@Controller
public class ObjektTransferController {
    private final ObjektTransferService transfers;
    public ObjektTransferController(ObjektTransferService transfers) { this.transfers = transfers; }

    @GetMapping("/cosmo/transfers")
    public String page(@RequestParam(defaultValue = "") String nickname, Model model) {
        model.addAttribute("nickname", nickname);
        return "cosmo/transfers";
    }

    @GetMapping("/api/objekt/transfers")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> account(@RequestParam String nickname,
            @RequestParam(defaultValue = "") String cursorTimestamp, @RequestParam(defaultValue = "") String cursorId) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(transfers.account(nickname.trim(), cursorTimestamp, cursorId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                    .body(Map.of("message", "닉네임과 다음 페이지 정보를 확인해 주세요."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
                    .body(Map.of("message", "전송 내역을 불러오지 못했습니다. 잠시 후 다시 조회해 주세요."));
        }
    }
}
