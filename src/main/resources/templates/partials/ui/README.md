# daisyUI 공통 UI

daisyUI 5 `triplesdark` 기준. `input-bordered` / `select-bordered` 없음 — `input` / `select` / `textarea` 사용.

| 용도 | 클래스 |
|------|--------|
| Primary | `btn btn-primary` |
| Secondary | `btn btn-soft` |
| Danger | `btn btn-error` |
| 검색 제출 | `btn btn-primary btn-sm btn-circle` |
| 텍스트 | `input` / `input input-sm w-full` |
| Select | `select` / `select w-full` |
| Textarea | `textarea w-full` |
| 검색 partial | `{{>partials/ui/search-field}}` |
| 뒤로가기 | `{{>partials/ui/back-link}}` (`backHref`, `backLabel`, 선택 `backAriaLabel` / `backBar`) |

금지: `site-field`, `site-search*`, `btn-custom-*`, `site-modal` 마크업, daisyUI `tab`/`tab-content`.
