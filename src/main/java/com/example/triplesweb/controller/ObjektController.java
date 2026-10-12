package com.example.triplesweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ObjektController {

    @GetMapping("/objekt")
    public String ObjektPage() {
        return "objekt";
    }
}
