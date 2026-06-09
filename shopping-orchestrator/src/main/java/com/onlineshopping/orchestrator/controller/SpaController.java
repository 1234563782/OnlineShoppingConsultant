package com.onlineshopping.orchestrator.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/register", "/chat"})
    public String spaRoutes() {
        return "forward:/index.html";
    }
}
