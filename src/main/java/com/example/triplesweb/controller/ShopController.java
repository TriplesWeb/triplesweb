package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ShopCardDto;
import com.example.triplesweb.entity.Shop;
import com.example.triplesweb.entity.ShopImage;
import com.example.triplesweb.repository.ShopItemRepository;
import com.example.triplesweb.service.FileStorageService;
import com.example.triplesweb.service.ShopService;
import com.example.triplesweb.util.ShopCategory;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 굿즈·앨범 등 쇼핑 목록·상세·세션 장바구니·관리자 상품 등록.
 * URL: {@code /shop}, {@code /shop/item/{category}/{id}}, {@code /shop/cart}, …
 * 레이어: Optional — ShopService와 ShopItemRepository·FileStorageService 혼용, 조회 계층 통일 검토.
 */
@RequiredArgsConstructor
@Controller
@RequestMapping("/shop")
public class ShopController {

    private final ShopItemRepository shopItemRepository;
    private final ShopService shopService;
    private final FileStorageService fileStorageService;

    @GetMapping
    public String ShopPage(@RequestParam(required = false) String category, Model model) {
        String cat = (category == null || category.isBlank()) ? "album" : category;
        List<ShopCardDto> items = shopService.getCardsByCategory(cat);
        model.addAttribute("category", cat);             // 현재 탭
        model.addAttribute("isAlbum", "album".equals(cat));
        model.addAttribute("items", items);
        return "shop/shop";
    }
    record ImageSlide(String imagePath, boolean first) {}

    @GetMapping("/item/{category}/{id}")
    public String shopDetail(@PathVariable Long id,
                             Model model) {
        Shop item = shopItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<ImageSlide> slides = new ArrayList<>();
        List<ShopImage> imgs = item.getImages();
        if (imgs != null && !imgs.isEmpty()) {
            for (int i = 0; i < item.getImages().size(); i++) {
                String url = (imgs.get(i).getFile() != null) ? imgs.get(i).getFile().getUrl() : "/images/no-image.png";
                slides.add(new ImageSlide(url, i == 0));
            }
        }
        model.addAttribute("item", item);
        model.addAttribute("itemImages", slides);
        return "shop/shop_detail";
    }
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/shop_new")
    public String shopNewGoods(@RequestParam(required = false) String category, Model model, Authentication auth) {
        String cate = (category == null || category.isBlank()) ? "album" : normalizeCategory(category);
        Shop goods = Shop.builder().category(ShopCategory.from(cate).getKey()).build();
        bindTabFlags(model, goods.normalizedTab());
        model.addAttribute("goodsForm", goods);
        return "shop/shop_new";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/shop_new")
    public String createShopGoods (@ModelAttribute Shop shop,
                                   @RequestParam("files") List <MultipartFile> files) throws IOException {
        shop.setCategory(normalizeCategory(shop.getCategory()));
        Shop savedGoods = shopService.registerGoods(shop, files);
        return "redirect:/shop/item/" + savedGoods.getCategory() + "/" + savedGoods.getId();
    }

    @PostMapping("/cart/add/{id}")
    public String addToCart(@PathVariable Long id, HttpSession session) {
        List<Long> cart = (List<Long>) session.getAttribute("CART");
        if (cart == null) cart = new ArrayList<>();
        cart.add(id);
        session.setAttribute("CART", cart);
        return "redirect:/shop/cart";
    }


    @GetMapping("/cart")
    public String viewCart(HttpSession session, Model model) {
        List<Long> cart = (List<Long>) session.getAttribute("CART");
        if (cart == null) cart = new ArrayList<>();

        List<Shop> items = shopItemRepository.findAllById(cart);
        model.addAttribute("items", items);
        return "shop/shop_cart";
    }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/uploadFile")
    @ResponseBody
    public Map <String,String> uploadShopImage(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "빈 파일은 업로드할 수 없습니다.");
        }
        var meta = fileStorageService.saveWithMeta(file);
        return Map.of(
                "url",meta.getUrl(),
                "savedName",meta.getSavedName(),
                "originalName", meta.getOriginalName()
        );
    }
    private String normalizeCategory(String c) {
        if (c == null) return "album";
        c = c.trim().toLowerCase();
        if (c.equals("tour-merch") || c.equals("tourmerch")) return "tour";
        return c; // album, merch, photobook, magazine, tour 등 그대로
    }
    private void bindTabFlags(Model model, String t) {
        model.addAttribute("category", t);
        model.addAttribute("category_album", "album".equals(t));
        model.addAttribute("category_merch", "merch".equals(t));
        model.addAttribute("category_photobook", "photobook".equals(t));
        model.addAttribute("category_magazine", "magazine".equals(t));
        model.addAttribute("category_tour", "tour".equals(t));
    }
    private String blankToEmpty(String s) { return (s == null) ? "" : s.trim(); }
}
