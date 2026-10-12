package com.example.triplesweb.controller;


import com.example.triplesweb.entity.Comment;
import com.example.triplesweb.repository.CommentRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class MyCommentController {
    @Autowired
    private CommentRepository commentRepository;

    @GetMapping("/mycomments")
    public String myComments(HttpSession session, Model model) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            model.addAttribute("errorMessage", "로그인 후 이용해주세요.");
            return "redirect:/members/login";
        }

        List<Comment> myList = commentRepository.findByUserId(userId, Sort.by(Sort.Direction.DESC, "formattedCreatedAt"));

        // boardName 같이 넣어야 하므로 map 변환
        List<Map<String, Object>> mappedResult = myList.stream().map(comment -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", comment.getArticle().getId());
            map.put("userId",comment.getUserId());
            map.put("body",comment.getBody());
            map.put("title", comment.getArticle().getTitle());

            map.put("category", comment.getArticle().getCategory());
            map.put("boardName", switch (comment.getArticle().getCategory()) {
                case "free" -> "자유";
                case "notice" -> "공지";
                case "trade" -> "교환";
                default -> "기타";
            });
            return map;
        }).toList();

        model.addAttribute("resultList", mappedResult);
        model.addAttribute("keyword", "내가 작성한 댓글");
        return "members/mycomments";
    }
}
