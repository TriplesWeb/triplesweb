# 전역 레이아웃 패키지 (`layout/`)

페이지마다 복제하지 말고 아래 partial만 사용합니다.

## 필수 골격

```mustache
{{>layout/head-open}}
<title>페이지 제목</title>
<!-- 페이지 전용 CSS/JS 링크만 추가 -->
</head>
<body class="site-page ...">
{{>layout/header}}
<main>...</main>
{{>layout/page-end}}
```

## 구성

| Partial / 자산 | 역할 |
|---|---|
| `head-open` | `<!DOCTYPE>` + `<html class="dark">` + `<head>` + 전역 head |
| `site-head` | favicon·폰트·`site.css`·`site-ui.css` |
| `favicon` | 모바일/시즌필터/헤더/푸터/크롬 CSS·JS |
| `header` | 전역 네비게이션 |
| `footer` | 전역 푸터 |
| `page-end` | footer + `</body></html>` |
| `triples-head` | site-head + triples 번들 CSS |
| `gravity-fonts` | site-head + gravity 번들 CSS |

## 규칙

- `charset` / `viewport` / Pretendard / `site-ui.css` / 헤더·푸터 CSS는 페이지에 다시 넣지 않습니다.
- `body`에는 항상 `site-page`를 포함해 푸터가 아래로 붙게 합니다.
- 영어 페이지는 컨트롤러에서 `pageLang=en` 등을 넣습니다 (`chemistry-en`).
