package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ShopDetailDto;
import com.example.triplesweb.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Controller
@RequestMapping("/shop")
public class ShopController {

    private final ShopService shopService;

    @GetMapping
    public String shopPage(Model model) {
        model.addAttribute("shopSections", shopService.listGroupedBySection());
        return "shop/shop";
    }

    @GetMapping("/{id:[0-9]+}")
    public String shopDetail(@PathVariable long id, Model model) {
        try {
            ShopDetailDto shop = shopService.getDetail(id);
            model.addAttribute("shop", shop);
            return "shop/shop_detail";
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
