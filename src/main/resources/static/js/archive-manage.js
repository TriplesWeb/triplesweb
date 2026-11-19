(function () {
    const kind = document.querySelector("[data-archive-kind]")?.dataset.archiveKind || "image";
    const noun = kind === "video" ? "영상" : "사진";
    const dateLabel = document.querySelector("[data-date-label]")?.dataset.dateLabel || (noun + " 날짜");
    const dialogTriggers = new WeakMap();

    function authHeaders() {
        const headerMap = {};
        const csrf = document.querySelector('meta[name="_csrf"]');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
        if (csrf && csrfHeader && csrf.content && csrfHeader.content) headerMap[csrfHeader.content] = csrf.content;
        return window.AboutAdminAuth ? AboutAdminAuth.applyAuthHeaders(headerMap) : headerMap;
    }

    /** HTTPS 프록시 뒤에서 302 Location 이 http 로 와도 같은 경로로 안전하게 이동한다. */
    function navigateAfterUpload(locationHeader) {
        if (!locationHeader) {
            location.reload();
            return;
        }
        try {
            const url = new URL(locationHeader, window.location.href);
            window.location.assign(url.pathname + url.search + url.hash);
        } catch (_) {
            if (locationHeader.startsWith("/")) window.location.assign(locationHeader);
            else location.reload();
        }
    }

    function uploadSucceeded(response) {
        return response.ok
            || response.type === "opaqueredirect"
            || response.status === 0
            || (response.status >= 300 && response.status < 400);
    }

    function openDialog(dialog) {
        if (!dialog) return;
        dialogTriggers.set(dialog, document.activeElement);
        dialog.hidden = false;
        document.body.style.overflow = "hidden";
        dialog.querySelector("[name='title'], [name='registeredOn'], [name='description']")?.focus();
    }

    function closeDialog(dialog) {
        if (!dialog) return;
        dialog.hidden = true;
        dialog.querySelectorAll('input[type="file"]').forEach(input => {
            releaseUrl(input._archiveLastFile);
            (input._archiveLastFiles || []).forEach(releaseUrl);
            input._archiveLastFile = null;
            input._archiveLastFiles = null;
        });
        dialogTriggers.get(dialog)?.focus();
        if (!document.querySelector(".ar-dialog:not([hidden])")) document.body.style.overflow = "";
    }

    function mediaNode(url, cropped) {
        const node = document.createElement(kind === "video" ? "video" : "img");
        node.src = url;
        if (kind === "video") {
            node.controls = !cropped;
            node.muted = true;
            node.playsInline = true;
            node.preload = "metadata";
        } else {
            node.alt = cropped ? "목록 카드 미리보기" : "원본 미리보기";
            node.decoding = "async";
        }
        return node;
    }

    function previewCardBody() {
        const body = document.createElement('div');
        body.className = 'ar-body';
        const title = document.createElement('h3');
        title.dataset.previewTitle = '';
        const meta = document.createElement('div');
        meta.className = 'ar-meta';
        meta.dataset.previewArtists = '';
        body.append(title, meta);
        return body;
    }

    function archiveSymbolLogo(frame) {
        const n = Number(frame);
        if (!Number.isInteger(n) || n < 1 || n > 24) return '';
        return '/uploads/images/' + encodeURIComponent('상징로고') + '/S' + n + '.png';
    }

    function memberColor(button) {
        const inline = button?.style?.getPropertyValue('--artist-color')?.trim();
        if (inline) return inline;
        if (button && button.isConnected) {
            const computed = getComputedStyle(button).getPropertyValue('--artist-color').trim();
            if (computed) return computed;
        }
        return '#b9cac4';
    }

    function memberChip(button, name) {
        const color = memberColor(button);
        const symbol = archiveSymbolLogo(button?.dataset.frame);
        const portrait = button?.querySelector('.artist-tab-photo')?.getAttribute('src') || '';
        const image = symbol || portrait;
        const chip = document.createElement('span');
        chip.className = 'archive-member-chip' + (symbol ? ' archive-symbol-chip' : '');
        chip.style.setProperty('--member-color', color);
        chip.title = name;
        chip.setAttribute('aria-label', name);
        if (image) {
            const img = document.createElement('img');
            img.src = image;
            img.alt = name;
            img.referrerPolicy = 'no-referrer';
            chip.appendChild(img);
        }
        const label = document.createElement('span');
        label.className = 'archive-chip-name';
        label.textContent = name;
        chip.appendChild(label);
        return chip;
    }

    function selectedMemberItems(root) {
        const picker = root?.querySelector?.('.member-portrait-picker') || root;
        return Array.from((picker || document).querySelectorAll('.mpp-item.is-active'));
    }

    function showPreview(box, url) {
        if (!box) return;
        const full = box.querySelector(".ar-preview-full");
        const card = box.querySelector(".ar-preview-card");
        const error = box.querySelector('[data-preview-error]');
        if (error) { error.hidden = true; error.textContent = ''; }
        delete box.dataset.invalid;
        delete box.dataset.pending;
        if (!url || !full || !card) {
            box.hidden = true;
            full && (full.innerHTML = "");
            card && (card.innerHTML = "");
            return;
        }
        full.replaceChildren(mediaNode(url, false));
        const article = document.createElement('article');
        article.className = 'ar-panel ar-card';
        if (kind === 'image') {
            card.classList.add('is-photo-preview');
            card.classList.remove('is-video-preview');
            article.classList.add('ar-preview-photo-card');
            const image = mediaNode(url, true);
            box.dataset.pending = 'true';
            image.addEventListener('load', () => { if (image.isConnected) delete box.dataset.pending; });
            image.addEventListener('error', () => {
                if (!image.isConnected) return;
                delete box.dataset.pending;
                box.dataset.invalid = 'true';
                if (error) {
                    error.textContent = '사진을 읽지 못했습니다. 손상되지 않은 이미지 파일을 다시 선택해 주세요.';
                    error.hidden = false;
                }
            });
            const link = document.createElement('button');
            link.type = 'button';
            link.className = 'ar-photo-link';
            link.tabIndex = -1;
            link.append(image);
            article.append(link, previewCardBody());
        } else {
            card.classList.add('is-video-preview');
            card.classList.remove('is-photo-preview');
            article.classList.add('ar-preview-video-card');
            const thumb = document.createElement('div');
            thumb.className = 'ar-video-thumb';
            const poster = document.createElement('img');
            poster.alt = '목록 카드 미리보기';
            poster.hidden = true;
            poster.dataset.previewPoster = '';
            const placeholder = document.createElement('span');
            placeholder.className = 'ar-video-placeholder';
            placeholder.textContent = '영상';
            const play = document.createElement('span');
            play.className = 'ar-video-play';
            play.setAttribute('aria-hidden', 'true');
            play.textContent = '▶';
            thumb.append(poster, placeholder, play);
            article.append(thumb, previewCardBody());
        }
        card.replaceChildren(article);
        box.hidden = false;
        syncPreview(box.closest('form'));
        syncThumbnail(box.closest('form'));
    }

    function rememberUrl(file) {
        if (!file) return "";
        if (file._archivePreviewUrl) return file._archivePreviewUrl;
        const ext = file.name.split('.').pop().toLowerCase();
        const type = { jfif: 'image/jpeg', jpg: 'image/jpeg', jpeg: 'image/jpeg', webp: 'image/webp' }[ext];
        const url = URL.createObjectURL(type ? new Blob([file], { type }) : file);
        file._archivePreviewUrl = url;
        return url;
    }

    function releaseUrl(file) {
        if (file && file._archivePreviewUrl) {
            URL.revokeObjectURL(file._archivePreviewUrl);
            file._archivePreviewUrl = "";
        }
    }

    function releaseUnusedFiles(previous, next) {
        const keep = new Set(next || []);
        (previous || []).forEach(file => {
            if (!keep.has(file)) releaseUrl(file);
        });
    }

    function isImageFile(file) {
        if (!file) return false;
        if (file.type && file.type.startsWith('image/')) return true;
        return /\.(jpe?g|jfif|png|webp|gif|bmp)$/i.test(file.name || '');
    }

    function selectedArtists(root) {
        return window.MemberPortraitPicker ? MemberPortraitPicker.selectedValues(root) : [];
    }

    function setInputFiles(input, files) {
        if (!input) return;
        const transfer = new DataTransfer();
        files.forEach(file => transfer.items.add(file));
        input.files = transfer.files;
        syncFileName(input);
        input.dispatchEvent(new Event('change', { bubbles: true }));
    }

    function removeFileAt(input, index) {
        const files = Array.from(input?.files || []);
        const removed = files[index];
        if (!removed) return;
        releaseUrl(removed);
        setInputFiles(input, files.filter((_, i) => i !== index));
    }

    function syncFileName(input) {
        const label = input?.closest('.ar-file')?.querySelector('[data-file-name]');
        if (!label) return;
        const files = Array.from(input.files || []);
        const form = input.closest('form');
        const preview = form?.querySelector('.ar-preview');
        label.classList.toggle('has-files', files.length > 0);
        label.classList.toggle('has-thumbs', files.some(isImageFile));
        if (!files.length) {
            label.replaceChildren();
            label.textContent = '선택된 파일 없음';
            return;
        }
        label.replaceChildren();
        files.forEach((file, index) => {
            if (isImageFile(file)) {
                const tile = document.createElement('div');
                tile.className = 'ar-file-tile';
                tile.title = file.name;
                const thumb = document.createElement('button');
                thumb.type = 'button';
                thumb.className = 'ar-file-thumb';
                thumb.setAttribute('aria-label', file.name + ' 미리보기');
                const img = document.createElement('img');
                img.alt = '';
                img.decoding = 'async';
                img.src = rememberUrl(file);
                thumb.appendChild(img);
                thumb.addEventListener('click', event => {
                    event.preventDefault();
                    event.stopPropagation();
                    if (preview && input.name === 'file') showPreview(preview, rememberUrl(file));
                });
                const remove = document.createElement('button');
                remove.type = 'button';
                remove.className = 'ar-file-remove';
                remove.textContent = '×';
                remove.title = file.name + ' 제거';
                remove.setAttribute('aria-label', file.name + ' 제거');
                remove.addEventListener('click', event => {
                    event.preventDefault();
                    event.stopPropagation();
                    removeFileAt(input, index);
                });
                tile.append(thumb, remove);
                label.appendChild(tile);
                return;
            }
            const chip = document.createElement('button');
            chip.type = 'button';
            chip.className = 'ar-file-chip';
            chip.textContent = file.name + ' ×';
            chip.title = file.name + ' 제거';
            chip.setAttribute('aria-label', file.name + ' 제거');
            chip.addEventListener('click', event => {
                event.preventDefault();
                event.stopPropagation();
                removeFileAt(input, index);
            });
            label.appendChild(chip);
        });
    }

    function bindFileNames(form) {
        if (!form) return;
        form.querySelectorAll('.ar-file input[type="file"]').forEach(input => {
            input.addEventListener('change', () => syncFileName(input));
        });
        form.addEventListener('reset', () => {
            queueMicrotask(() => form.querySelectorAll('.ar-file input[type="file"]').forEach(syncFileName));
        });
    }

    function syncPreview(form) {
        if (!form) return;
        const preview = form.querySelector('.ar-preview');
        if (!preview) return;
        const title = preview.querySelector('[data-preview-title]');
        if (title) {
            const typedTitle = form.querySelector('[name="title"]')?.value.trim() || '';
            const typedDescription = form.querySelector('[name="description"]')?.value.trim() || '';
            title.textContent = typedTitle || typedDescription || (kind === 'video' ? '영상 제목' : '사진');
        }
        const meta = preview.querySelector('[data-preview-artists]');
        if (meta) {
            const items = selectedMemberItems(form.closest('.ar-dialog'));
            meta.style.setProperty('--member-count', String(items.length || 1));
            meta.replaceChildren();
            items.forEach(button => {
                const name = (button.dataset.value || button.dataset.name || '').trim();
                if (name) meta.appendChild(memberChip(button, name));
            });
            const registered = form.querySelector('[name="registeredOn"]')?.value || '';
            if (registered) {
                const time = document.createElement('time');
                time.className = 'ar-card-date';
                time.dateTime = registered;
                time.textContent = registered.replaceAll('-', '/');
                meta.appendChild(time);
            }
        }
        const info = preview.querySelector('[data-preview-file]');
        const files = Array.from(form.querySelector('input[name="file"]')?.files || []);
        if (info) {
            if (!files.length) {
                info.textContent = kind === 'video' ? '현재 저장된 영상' : '현재 저장된 사진';
            } else if (files.length === 1) {
                info.textContent = `${files[0].name} · ${(files[0].size / 1024).toLocaleString('ko', { maximumFractionDigits: 1 })} KB`;
            } else {
                info.textContent = `${files.length}장 선택`;
            }
        }
    }

    function renderArtists(root) {
        if (!root) return;
        const values = selectedArtists(root);
        const count = root.querySelector('[data-picker-count]');
        if (count) count.textContent = String(values.length);
        const chips = root.querySelector('[data-artist-chips]');
        if (chips) {
            chips.replaceChildren();
            values.forEach(name => {
                const chip = document.createElement('button');
                chip.type = 'button';
                chip.className = 'ar-artist-chip';
                chip.textContent = name + ' ×';
                chip.setAttribute('aria-label', name + ' 선택 해제');
                chip.addEventListener('click', () => {
                    root.querySelectorAll('.mpp-item').forEach(button => {
                        if (button.dataset.value === name) window.MemberPortraitPicker?.setActive(button, false);
                    });
                    renderArtists(root);
                });
                chips.appendChild(chip);
            });
            if (!values.length) chips.textContent = '멤버를 선택하면 여기에 표시됩니다.';
        }
        syncPreview(root.querySelector('form'));
    }

    function applyArtists(root, raw) {
        if (window.MemberPortraitPicker) MemberPortraitPicker.clear(root);
        const names = (raw || "").split(",").map(part => part.trim()).filter(Boolean);
        root.querySelectorAll(".mpp-item").forEach(button => {
            const value = (button.dataset.value || "").trim();
            if (names.includes(value) && window.MemberPortraitPicker) {
                window.MemberPortraitPicker.setActive(button, true);
            }
        });
        renderArtists(root);
    }

    function bindPicker(root) {
        if (!root) return;
        window.MemberPortraitPicker?.bindMultiSelect(root, {
            onChange: () => renderArtists(root)
        });
        root.querySelector("[data-picker-clear]")?.addEventListener("click", () => {
            window.MemberPortraitPicker?.clear(root);
            renderArtists(root);
        });
        root.querySelector('form')?.addEventListener('input', () => syncPreview(root.querySelector('form')));
        window.MemberPortraitPicker?.clear(root);
        renderArtists(root);
    }

    function acceptedFile(file) {
        if (kind === 'video') return !file.type || file.type.startsWith('video/');
        return /\.(jpe?g|jfif|png|webp|gif|bmp)$/i.test(file.name) || file.type.startsWith('image/');
    }

    function extensionForImageType(type) {
        return ({
            'image/jpeg': 'jpg',
            'image/jpg': 'jpg',
            'image/pjpeg': 'jpg',
            'image/png': 'png',
            'image/webp': 'webp',
            'image/gif': 'gif',
            'image/bmp': 'bmp',
            'image/x-ms-bmp': 'bmp'
        })[type] || 'png';
    }

    function normalizeClipboardImage(file) {
        if (!file) return null;
        const type = file.type && file.type.startsWith('image/') ? file.type : 'image/png';
        const name = (file.name && /\.(jpe?g|jfif|png|webp|gif|bmp)$/i.test(file.name))
            ? file.name
            : ('clipboard.' + extensionForImageType(type));
        if (file instanceof File && file.name === name && file.type === type) return file;
        return new File([file], name, { type, lastModified: Date.now() });
    }

    function imageFilesFromClipboard(clipboardData) {
        if (!clipboardData) return [];
        const collected = [];
        const seen = new Set();
        const push = file => {
            const normalized = normalizeClipboardImage(file);
            if (!normalized) return;
            const key = normalized.name + ':' + normalized.size + ':' + normalized.type + ':' + normalized.lastModified;
            if (seen.has(key)) return;
            seen.add(key);
            collected.push(normalized);
        };
        const items = clipboardData.items;
        if (items) {
            for (let i = 0; i < items.length; i += 1) {
                const item = items[i];
                if (item.kind === 'file' && item.type && item.type.startsWith('image/')) {
                    push(item.getAsFile());
                }
            }
        }
        if (!collected.length) {
            const files = clipboardData.files;
            if (files) {
                for (let i = 0; i < files.length; i += 1) {
                    const candidate = files[i];
                    if (candidate && candidate.type && candidate.type.startsWith('image/')) push(candidate);
                }
            }
        }
        return collected;
    }

    function assignFilesToInput(input, files, { append = false } = {}) {
        if (!input || !files?.length) return false;
        const next = append && input.multiple
            ? Array.from(input.files || []).concat(files)
            : files.slice(0, input.multiple ? files.length : 1);
        setInputFiles(input, next);
        return true;
    }

    function syncThumbnail(form) {
        if (!form) return;
        const url = form.dataset.thumbnailUrl || '';
        form.querySelectorAll('.ar-preview video').forEach(video => {
            if (url) video.poster = url;
            else video.removeAttribute('poster');
        });
        const poster = form.querySelector('[data-preview-poster]');
        const placeholder = form.querySelector('.ar-preview-card .ar-video-placeholder');
        if (poster) {
            if (url) {
                poster.src = url;
                poster.hidden = false;
                if (placeholder) placeholder.hidden = true;
            } else {
                poster.removeAttribute('src');
                poster.hidden = true;
                if (placeholder) placeholder.hidden = false;
            }
        }
    }

    function showThumbnail(form, url) {
        if (!form) return;
        const box = form.querySelector('[data-thumbnail-preview]');
        if (!box) return;
        const input = form.querySelector('[name="thumbnail"]');
        const error = form.querySelector('[data-thumbnail-error]');
        const image = box.querySelector('img');
        form.dataset.thumbnailUrl = url || '';
        delete form.dataset.thumbnailInvalid;
        delete form.dataset.thumbnailPending;
        if (error) { error.hidden = true; error.textContent = ''; }
        box.hidden = !url;
        if (url) {
            const file = input.files?.[0];
            box.querySelector('[data-thumbnail-info]').textContent = file ? file.name : '현재 저장된 썸네일';
            if (file) form.dataset.thumbnailPending = 'true';
            image.onload = () => { delete form.dataset.thumbnailPending; };
            image.onerror = () => {
                delete form.dataset.thumbnailPending;
                if (input.files?.[0]) form.dataset.thumbnailInvalid = 'true';
                if (error) { error.textContent = '썸네일 이미지를 읽지 못했습니다. 다른 사진을 선택하거나 썸네일을 해제해 주세요.'; error.hidden = false; }
            };
            image.src = url;
        } else {
            image.removeAttribute('src');
        }
        syncThumbnail(form);
    }

    function resetThumbnail(form, original = '') {
        const input = form?.querySelector('[name="thumbnail"]');
        if (!input) return;
        releaseUrl(input._archiveLastFile);
        input._archiveLastFile = null;
        input.value = '';
        syncFileName(input);
        form.dataset.originalThumbnail = original;
        const clear = form.querySelector('[name="clearThumbnail"]');
        if (clear) clear.value = 'false';
        showThumbnail(form, original);
    }

    function bindThumbnail(form) {
        const input = form?.querySelector('[name="thumbnail"]');
        if (!input) return;
        const clear = form.querySelector('[name="clearThumbnail"]');
        input.addEventListener('change', () => {
            releaseUrl(input._archiveLastFile);
            input._archiveLastFile = null;
            const file = input.files?.[0];
            if (!file) { showThumbnail(form, clear?.value === 'true' ? '' : form.dataset.originalThumbnail || ''); return; }
            if (!/\.(jpe?g|jfif|png|webp|gif|bmp)$/i.test(file.name)) {
                alert('썸네일은 JPG, JFIF, PNG, WebP, GIF, BMP 이미지 파일로 선택해 주세요.');
                input.value = '';
                syncFileName(input);
                showThumbnail(form, clear?.value === 'true' ? '' : form.dataset.originalThumbnail || '');
                return;
            }
            if (clear) clear.value = 'false';
            input._archiveLastFile = file;
            showThumbnail(form, rememberUrl(file));
        });
        form.querySelector('[data-thumbnail-clear]')?.addEventListener('click', () => {
            releaseUrl(input._archiveLastFile);
            input._archiveLastFile = null;
            input.value = '';
            syncFileName(input);
            if (clear) clear.value = 'true';
            showThumbnail(form, '');
        });
    }

    function validThumbnail(form) {
        if (form.dataset.thumbnailInvalid || form.dataset.thumbnailPending) {
            alert('썸네일 미리보기를 확인하거나 다른 이미지 파일을 선택해 주세요.');
            return false;
        }
        return true;
    }

    document.querySelectorAll(".ar-dialog").forEach(dialog => {
        dialog.querySelectorAll("[data-ar-close]:not(.ar-dialog-backdrop)").forEach(button => {
            button.addEventListener("click", () => closeDialog(dialog));
        });
    });
    document.addEventListener("keydown", event => {
        if (event.key !== "Escape") return;
        const open = [...document.querySelectorAll(".ar-dialog:not([hidden])")].pop();
        if (open) closeDialog(open);
    });

    const uploadDialog = document.getElementById("uploadModal");
    const uploadForm = document.getElementById("archiveUploadForm");
    const uploadPreview = document.getElementById("uploadPreview");
    const uploadFile = uploadForm?.querySelector('input[name="file"]');
    bindPicker(uploadDialog);
    bindThumbnail(uploadForm);
    bindFileNames(uploadForm);

    function defaultUploadArtists() {
        const fromPage = document.querySelector("[data-archive-kind]")?.dataset.defaultArtists;
        if (fromPage != null && fromPage.trim()) return fromPage.trim();
        const params = new URLSearchParams(window.location.search);
        return [...new Set(params.getAll("members").map(name => name.trim()).filter(Boolean))].join(",");
    }

    window.showAddModal = function () {
        if (!uploadForm || !uploadDialog) return;
        uploadForm.reset();
        resetThumbnail(uploadForm);
        // 멤버 폴더·필터에서 열면 해당 멤버를 기본 선택(변경 가능)
        applyArtists(uploadDialog, defaultUploadArtists());
        if (uploadFile) {
            releaseUrl(uploadFile._archiveLastFile);
            (uploadFile._archiveLastFiles || []).forEach(releaseUrl);
            uploadFile._archiveLastFile = null;
            uploadFile._archiveLastFiles = null;
        }
        showPreview(uploadPreview, "");
        window.bindArchiveDateFields?.(uploadForm);
        uploadForm.querySelectorAll('.ar-file input[type="file"]').forEach(syncFileName);
        openDialog(uploadDialog);
    };

    uploadFile?.addEventListener("change", () => {
        const files = Array.from(uploadFile.files || []);
        releaseUnusedFiles(uploadFile._archiveLastFiles, files);
        uploadFile._archiveLastFile = files[0] || null;
        uploadFile._archiveLastFiles = files;
        if (!files.length) {
            showPreview(uploadPreview, "");
            syncFileName(uploadFile);
            return;
        }
        const invalid = files.find(file => !acceptedFile(file));
        if (invalid) {
            alert(kind === "video" ? "동영상 파일만 선택할 수 있습니다." : "이미지 파일만 선택할 수 있습니다.");
            (uploadFile._archiveLastFiles || []).forEach(releaseUrl);
            uploadFile.value = "";
            uploadFile._archiveLastFile = null;
            uploadFile._archiveLastFiles = null;
            syncFileName(uploadFile);
            showPreview(uploadPreview, "");
            return;
        }
        showPreview(uploadPreview, rememberUrl(files[0]));
        files.slice(1).forEach(rememberUrl);
        syncFileName(uploadFile);
    });

    uploadForm?.addEventListener("submit", async event => {
        event.preventDefault();
        const title = uploadForm.querySelector("[name='title']")?.value.trim();
        const files = Array.from(uploadFile?.files || []);
        if (kind === "video" && !title) {
            alert("제목을 입력해 주세요.");
            return;
        }
        if (!files.length) {
            alert(noun + " 파일을 선택해 주세요.");
            return;
        }
        if (!uploadForm.querySelector("[name='registeredOn']")?.value) {
            alert(dateLabel + "를 선택해 주세요.");
            return;
        }
        if (!uploadForm.querySelector("[name='source']")?.value) {
            alert("출처를 선택해 주세요.");
            return;
        }
        if (!uploadPreview || uploadPreview.hidden || uploadPreview.dataset.invalid || uploadPreview.dataset.pending) {
            alert("미리보기를 확인한 뒤 추가해 주세요.");
            return;
        }
        const confirmMessage = kind === "image" && files.length > 1
            ? "사진 " + files.length + "장을 한 게시글로 추가하시겠습니까?"
            : "이 " + noun + "을 추가하시겠습니까?";
        if (!window.confirm(confirmMessage)) return;
        if (!validThumbnail(uploadForm)) return;
        const body = new FormData(uploadForm);
        body.delete("artistNames");
        body.delete("extraArtists");
        selectedArtists(uploadDialog).forEach(name => body.append("artistNames", name));
        const button = uploadForm.querySelector("[type='submit']");
        if (button) button.disabled = true;
        try {
            const response = await fetch(uploadForm.dataset.uploadUrl || "/archive/upload", {
                method: "POST",
                body,
                headers: authHeaders(),
                credentials: "same-origin",
                // 프록시 뒤 절대 http Location 을 fetch 가 따라가면 Mixed Content 로 실패한다.
                redirect: "manual"
            });
            if (uploadSucceeded(response)) {
                navigateAfterUpload(response.headers.get("Location"));
                return;
            }
            const message = (await response.text()).replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
            alert(response.status === 403 ? "관리자만 추가할 수 있습니다." : (message.slice(0, 180) || "추가에 실패했습니다."));
        } catch (_) {
            alert("추가 요청에 실패했습니다. 네트워크 연결을 확인해 주세요.");
        } finally {
            if (button) button.disabled = false;
        }
    });

    const editDialog = document.getElementById("editModal");
    const editForm = document.getElementById("archiveEditForm");
    const editPreview = document.getElementById("editPreview");
    const editFile = editForm?.querySelector('input[name="file"]');
    bindPicker(editDialog);
    bindThumbnail(editForm);
    bindFileNames(editForm);

    function activeImageFileInput() {
        if (kind !== "image") return null;
        if (uploadDialog && !uploadDialog.hidden) return uploadFile || null;
        if (editDialog && !editDialog.hidden) return editFile || null;
        return null;
    }

    document.addEventListener("paste", event => {
        const input = activeImageFileInput();
        if (!input) return;
        const files = imageFilesFromClipboard(event.clipboardData);
        if (!files.length) return;
        event.preventDefault();
        assignFilesToInput(input, files, { append: Boolean(input.multiple) });
    });

    function showEdit(button) {
        if (!editForm || !editDialog) return;
        editForm.reset();
        resetThumbnail(editForm, button.dataset.thumbnailUrl || '');
        editForm.dataset.id = button.dataset.id || "";
        editForm.dataset.originalUrl = button.dataset.url || '';
        const titleInput = editForm.querySelector("[name='title']");
        if (titleInput) titleInput.value = button.dataset.title || "";
        const descriptionInput = editForm.querySelector("[name='description']");
        if (descriptionInput) {
            descriptionInput.value = button.dataset.description
                || (kind === "image" ? (button.dataset.title || "") : "");
        }
        const registered = editForm.querySelector("[name='registeredOn']");
        if (registered) {
            registered.value = button.dataset.registered || "";
            registered.dispatchEvent(new Event("input", { bubbles: true }));
        }
        applyArtists(editDialog, button.dataset.artists || "");
        const sourceSelect = editForm.querySelector("[name='source']");
        if (sourceSelect) sourceSelect.value = button.dataset.source || "";
        if (editFile) {
            releaseUrl(editFile._archiveLastFile);
            (editFile._archiveLastFiles || []).forEach(releaseUrl);
            editFile._archiveLastFile = null;
            editFile._archiveLastFiles = null;
        }
        showPreview(editPreview, button.dataset.url || "");
        window.bindArchiveDateFields?.(editForm);
        editForm.querySelectorAll('.ar-file input[type="file"]').forEach(syncFileName);
        openDialog(editDialog);
    }

    editFile?.addEventListener("change", () => {
        const files = Array.from(editFile.files || []);
        releaseUnusedFiles(editFile._archiveLastFiles, files);
        editFile._archiveLastFile = files[0] || null;
        editFile._archiveLastFiles = files;
        if (!files.length) {
            showPreview(editPreview, editForm.dataset.originalUrl || '');
            syncFileName(editFile);
            return;
        }
        const invalid = files.find(file => !acceptedFile(file));
        if (invalid) {
            alert(kind === "video" ? "동영상 파일만 선택할 수 있습니다." : "이미지 파일만 선택할 수 있습니다.");
            files.forEach(releaseUrl);
            editFile.value = "";
            editFile._archiveLastFile = null;
            editFile._archiveLastFiles = null;
            syncFileName(editFile);
            showPreview(editPreview, editForm.dataset.originalUrl || '');
            return;
        }
        showPreview(editPreview, rememberUrl(files[0]));
        files.slice(1).forEach(rememberUrl);
        syncFileName(editFile);
    });

    async function removeArchive(id, title) {
        if (!id) {
            alert("삭제할 자료를 찾지 못했습니다.");
            return;
        }
        const label = title ? "‘" + title + "’ " : "이 ";
        if (!window.confirm(label + noun + "을 삭제하시겠습니까? 삭제하면 되돌릴 수 없습니다.")) return;
        try {
            const response = await fetch("/archive/" + encodeURIComponent(id), {
                method: "DELETE", headers: authHeaders(), credentials: "same-origin"
            });
            if (response.ok) {
                location.reload();
                return;
            }
            alert(response.status === 403 ? "관리자만 삭제할 수 있습니다." : (response.status === 404 ? "이미 삭제된 자료입니다." : "삭제에 실패했습니다."));
        } catch (_) {
            alert("삭제 요청에 실패했습니다. 네트워크 연결을 확인해 주세요.");
        }
    }

    document.addEventListener("click", event => {
        const edit = event.target.closest("[data-archive-edit]");
        if (edit) {
            showEdit(edit);
            return;
        }
        const remove = event.target.closest("[data-archive-delete]");
        if (remove) removeArchive(remove.dataset.id, remove.dataset.description || remove.dataset.title);
    });

    editForm?.addEventListener("submit", async event => {
        event.preventDefault();
        const title = editForm.querySelector("[name='title']")?.value.trim() || "";
        const description = editForm.querySelector("[name='description']")?.value.trim() || "";
        if (kind === "video" && !title) {
            alert("제목을 입력해 주세요.");
            return;
        }
        const registeredOn = editForm.querySelector("[name='registeredOn']")?.value || "";
        if (!registeredOn) {
            alert(dateLabel + "를 선택해 주세요.");
            return;
        }
        if (!editForm.dataset.id) {
            alert("수정할 자료를 찾지 못했습니다.");
            return;
        }
        const replaceFiles = Array.from(editFile?.files || []);
        const replacing = replaceFiles.length > 0;
        if (replacing && (editPreview?.dataset.invalid || editPreview?.dataset.pending)) {
            alert('사진을 읽지 못했습니다. 다른 이미지 파일을 선택해 주세요.');
            return;
        }
        const message = replacing
            ? (kind === "image" && replaceFiles.length > 1
                ? "이 게시글 사진을 " + replaceFiles.length + "장으로 통째로 교체할까요?"
                : "이 " + noun + "을 수정하시겠습니까? 선택한 파일로 기존 미디어가 바뀝니다.")
            : "이 " + noun + "을 수정하시겠습니까?";
        if (!window.confirm(message)) return;
        if (!validThumbnail(editForm)) return;
        const body = new FormData();
        if (kind === "video") body.set("title", title);
        else body.set("description", description);
        body.set("registeredOn", registeredOn);
        body.set("artistNames", selectedArtists(editDialog).join(","));
        const source = editForm.querySelector("[name='source']")?.value || "";
        if (source) body.set("source", source);
        if (replacing) replaceFiles.forEach(file => body.append("file", file));
        const thumbnail = editForm.querySelector('[name="thumbnail"]')?.files?.[0];
        if (thumbnail) body.set('thumbnail', thumbnail);
        const clearThumbnail = editForm.querySelector('[name="clearThumbnail"]');
        if (clearThumbnail) body.set('clearThumbnail', clearThumbnail.value);
        const button = editForm.querySelector("[type='submit']");
        if (button) button.disabled = true;
        try {
            const response = await fetch("/archive/" + encodeURIComponent(editForm.dataset.id), {
                method: "POST", body, headers: authHeaders(), credentials: "same-origin"
            });
            if (response.ok) {
                location.reload();
                return;
            }
            const text = (await response.text()).replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
            alert(response.status === 403 ? "관리자만 수정할 수 있습니다." : (text.slice(0, 180) || "수정에 실패했습니다."));
        } catch (_) {
            alert("수정 요청에 실패했습니다. 네트워크 연결을 확인해 주세요.");
        } finally {
            if (button) button.disabled = false;
        }
    });
})();
