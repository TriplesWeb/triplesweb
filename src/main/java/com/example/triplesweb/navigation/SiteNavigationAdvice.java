package com.example.triplesweb.navigation;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.util.List;

@ControllerAdvice
public class SiteNavigationAdvice {
    @ModelAttribute("siteNavigation")
    public List<SiteNavigation.Section> navigation() { return SiteNavigation.PRIMARY; }
}
