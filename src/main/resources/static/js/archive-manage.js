(function () {
    const kind = document.querySelector("[data-archive-kind]")?.dataset.archiveKind || "image";
    const noun = kind === "video" ? "영상" : "사진";
    const extraNames = new WeakMap();
    const dialogTriggers = new WeakMap();

    function authHeaders() {
        const headerMap = {};
        const csrf = document.querySelector('meta[name="_csrf"]');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
        if (csrf && csrfHeader && csrf.content && csrfHeader.content) headerMap[csrfHeader.content] = csrf.content;
        return window.AboutAdminAuth ? AboutAdminAuth.applyAuthHeaders(headerMap) : headerMap;
    }

    function openDialog(dialog) {
        if (!dialog) return;
        dialogTriggers.set(dialog, document.activeElement);
        dialog.hidden = false;
        document.body.style.overflow = "hidden";
        dialog.querySelector("[name='title']")?.focus();
    }

    function closeDialog(dialog) {
        if (!dialog) return;
        dialog.hidden = true;
        dialog.querySelectorAll('input[type="file"]').forEach(input => {
            releaseUrl(input._archiveLastFile);
            input._archiveLastFile = null;
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
        if (kind === 'image') {
            card.classList.add('is-photo-preview');
            const article = document.createElement('article');
            article.className = 'ar-panel ar-card ar-preview-photo-card';
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
            const body = document.createElement('div');
            body.className = 'ar-body';
            const title = document.createElement('h3');
            title.dataset.previewTitle = '';
            const artists = document.createElement('div');
            artists.className = 'ar-meta';
            artists.dataset.previewArtists = '';
            body.append(title, artists);
            article.append(image, body);
            card.replaceChildren(article);
        } else {
            card.replaceChildren(mediaNode(url, true));
        }
        box.hidden = false;
        syncPreview(box.closest('form'));
        syncThumbnail(box.closest('form'));
    }

    function rememberUrl(file) {
        if (!file) return "";
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

    function selectedArtists(root, extra) {
        const values = window.MemberPortraitPicker ? MemberPortraitPicker.selectedValues(root) : [];
        (extraNames.get(root) || []).forEach(name => { if (!values.includes(name)) values.push(name); });
        (extra?.value || "").split(",").map(part => part.trim()).filter(Boolean).forEach(name => {
            if (!values.includes(name)) values.push(name);
        });
        return values;
    }

    function syncPreview(form) {
        if (!form) return;
        const preview = form.querySelector('.ar-preview');
        if (!preview) return;
        const title = preview.querySelector('[data-preview-title]');
        if (title) title.textContent = form.querySelector('[name="title"]')?.value.trim() || '사진 제목';
        const artists = preview.querySelector('[data-preview-artists]');
        if (artists) {
            artists.replaceChildren();
            const names = selectedArtists(form.closest('.ar-dialog'), form.querySelector('[name="extraArtists"]'));
            (names.length ? names : ['출연자 미지정']).forEach(name => {
                const badge = document.createElement('span');
                badge.className = 'ar-badge';
                badge.textContent = name;
                artists.appendChild(badge);
            });
        }
        const info = preview.querySelector('[data-preview-file]');
        const file = form.querySelector('input[type="file"]')?.files?.[0];
        if (info) info.textContent = file ? `${file.name} · ${(file.size / 1024).toLocaleString('ko', { maximumFractionDigits: 1 })} KB` : '현재 저장된 사진';
    }

    function renderArtists(root) {
        if (!root) return;
        const values = selectedArtists(root, null);
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
                    extraNames.set(root, (extraNames.get(root) || []).filter(value => value !== name));
                    renderArtists(root);
                    root.querySelector('[name="extraArtists"]')?.focus();
                });
                chips.appendChild(chip);
            });
            if (!values.length) chips.textContent = '출연자를 선택하면 여기에 표시됩니다.';
        }
        syncPreview(root.querySelector('form'));
    }

    function applyArtists(root, extra, raw) {
        if (window.MemberPortraitPicker) MemberPortraitPicker.clear(root);
        const names = (raw || "").split(",").map(part => part.trim()).filter(Boolean);
        const known = new Set();
        root.querySelectorAll(".mpp-item").forEach(button => {
            const value = (button.dataset.value || "").trim();
            if (names.includes(value) && window.MemberPortraitPicker) {
                window.MemberPortraitPicker?.setActive(button, true);
                known.add(value);
            }
        });
        const unknown = names.filter(name => !known.has(name));
        extraNames.set(root, root.querySelector('[data-artist-tags]') ? unknown : []);
        if (extra) extra.value = root.querySelector('[data-artist-tags]') ? '' : unknown.join(', ');
        renderArtists(root);
    }

    function bindPicker(root) {
        if (!root) return;
        window.MemberPortraitPicker?.bindMultiSelect(root, {
            onChange: () => renderArtists(root)
        });
        root.querySelector("[data-picker-clear]")?.addEventListener("click", () => {
            window.MemberPortraitPicker?.clear(root);
            extraNames.set(root, []);
            const input = root.querySelector('[name="extraArtists"]');
            if (input) input.value = '';
            renderArtists(root);
        });
        const input = root.querySelector('[data-artist-tags] [name="extraArtists"]');
        function addNames() {
            const names = (input?.value || '').split(/[,\n]/).map(name => name.trim()).filter(Boolean);
            const added = extraNames.get(root) || [];
            names.forEach(name => {
                const button = [...root.querySelectorAll('.mpp-item')].find(item => (item.dataset.value || '').toLocaleLowerCase() === name.toLocaleLowerCase());
                if (button && window.MemberPortraitPicker) MemberPortraitPicker.setActive(button, true);
                else if (!added.some(value => value.toLocaleLowerCase() === name.toLocaleLowerCase())) added.push(name);
            });
            extraNames.set(root, added);
            if (input) { input.value = ''; input.focus(); }
            renderArtists(root);
        }
        root.querySelector('[data-artist-add]')?.addEventListener('click', addNames);
        input?.addEventListener('keydown', event => {
            if (event.key === 'Enter' && !event.isComposing) { event.preventDefault(); addNames(); }
        });
        root.querySelector('form')?.addEventListener('input', () => syncPreview(root.querySelector('form')));
        window.MemberPortraitPicker?.clear(root);
        extraNames.set(root, []);
        renderArtists(root);
    }

    function acceptedFile(file) {
        if (kind === 'video') return !file.type || file.type.startsWith('video/');
        return /\.(jpe?g|jfif|png|webp|gif|bmp)$/i.test(file.name) || file.type.startsWith('image/');
    }

    function syncThumbnail(form) {
        if (!form) return;
        const url = form.dataset.thumbnailUrl || '';
        form.querySelectorAll('.ar-preview video').forEach(video => {
            if (url) video.poster = url;
            else video.removeAttribute('poster');
        });
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
        dialog.querySelectorAll("[data-ar-close]").forEach(button => {
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
    const uploadFile = uploadForm?.querySelector('input[type="file"]');
    bindPicker(uploadDialog);
    bindThumbnail(uploadForm);

    window.showAddModal = function () {
        if (!uploadForm || !uploadDialog) return;
        uploadForm.reset();
        resetThumbnail(uploadForm);
        applyArtists(uploadDialog, uploadForm.querySelector("[name='extraArtists']"), "");
        if (uploadFile) releaseUrl(uploadFile._archiveLastFile);
        showPreview(uploadPreview, "");
        openDialog(uploadDialog);
    };

    uploadFile?.addEventListener("change", () => {
        const file = uploadFile.files && uploadFile.files[0];
        releaseUrl(uploadFile._archiveLastFile);
        uploadFile._archiveLastFile = file || null;
        if (!file) {
            showPreview(uploadPreview, "");
            return;
        }
        if (!acceptedFile(file)) {
            alert(kind === "video" ? "동영상 파일만 선택할 수 있습니다." : "이미지 파일만 선택할 수 있습니다.");
            uploadFile.value = "";
            showPreview(uploadPreview, "");
            return;
        }
        showPreview(uploadPreview, rememberUrl(file));
    });

    uploadForm?.addEventListener("submit", async event => {
        event.preventDefault();
        const title = uploadForm.querySelector("[name='title']")?.value.trim();
        const file = uploadFile?.files?.[0];
        if (!title) {
            alert("제목을 입력해 주세요.");
            return;
        }
        if (!file) {
            alert(noun + " 파일을 선택해 주세요.");
            return;
        }
        if (!uploadForm.querySelector("[name='registeredOn']")?.value) {
            alert("등록일을 입력해 주세요.");
            return;
        }
        if (!uploadPreview || uploadPreview.hidden || uploadPreview.dataset.invalid || uploadPreview.dataset.pending) {
            alert("미리보기를 확인한 뒤 추가해 주세요.");
            return;
        }
        if (!window.confirm("이 " + noun + "을 추가하시겠습니까?")) return;
        if (!validThumbnail(uploadForm)) return;
        const body = new FormData(uploadForm);
        body.delete("artistNames");
        body.delete("extraArtists");
        selectedArtists(uploadDialog, uploadForm.querySelector("[name='extraArtists']")).forEach(name => body.append("artistNames", name));
        const button = uploadForm.querySelector("[type='submit']");
        if (button) button.disabled = true;
        try {
            const response = await fetch(uploadForm.dataset.uploadUrl || "/archive/upload", {
                method: "POST", body, headers: authHeaders(), credentials: "same-origin"
            });
            if (response.ok) {
                location.reload();
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
    const editFile = editForm?.querySelector('input[type="file"]');
    bindPicker(editDialog);
    bindThumbnail(editForm);

    function showEdit(button) {
        if (!editForm || !editDialog) return;
        editForm.reset();
        resetThumbnail(editForm, button.dataset.thumbnailUrl || '');
        editForm.dataset.id = button.dataset.id || "";
        editForm.dataset.originalUrl = button.dataset.url || '';
        editForm.querySelector("[name='title']").value = button.dataset.title || "";
        const registered = editForm.querySelector("[name='registeredOn']");
        if (registered) registered.value = button.dataset.registered || "";
        applyArtists(editDialog, editForm.querySelector("[name='extraArtists']"), button.dataset.artists || "");
        if (editFile) releaseUrl(editFile._archiveLastFile);
        showPreview(editPreview, button.dataset.url || "");
        openDialog(editDialog);
    }

    editFile?.addEventListener("change", () => {
        const file = editFile.files && editFile.files[0];
        releaseUrl(editFile._archiveLastFile);
        editFile._archiveLastFile = file || null;
        if (!file) { showPreview(editPreview, editForm.dataset.originalUrl || ''); return; }
        if (!acceptedFile(file)) {
            alert(kind === "video" ? "동영상 파일만 선택할 수 있습니다." : "이미지 파일만 선택할 수 있습니다.");
            editFile.value = "";
            showPreview(editPreview, editForm.dataset.originalUrl || '');
            return;
        }
        showPreview(editPreview, rememberUrl(file));
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
        if (remove) removeArchive(remove.dataset.id, remove.dataset.title);
    });

    editForm?.addEventListener("submit", async event => {
        event.preventDefault();
        const title = editForm.querySelector("[name='title']")?.value.trim();
        if (!title) {
            alert("제목을 입력해 주세요.");
            return;
        }
        const registeredOn = editForm.querySelector("[name='registeredOn']")?.value || "";
        if (!registeredOn) {
            alert("등록일을 입력해 주세요.");
            return;
        }
        if (!editForm.dataset.id) {
            alert("수정할 자료를 찾지 못했습니다.");
            return;
        }
        const replacing = Boolean(editFile?.files?.[0]);
        if (replacing && (editPreview?.dataset.invalid || editPreview?.dataset.pending)) {
            alert('사진을 읽지 못했습니다. 다른 이미지 파일을 선택해 주세요.');
            return;
        }
        const message = replacing
            ? "이 " + noun + "을 수정하시겠습니까? 선택한 파일로 기존 미디어가 바뀝니다."
            : "이 " + noun + "을 수정하시겠습니까?";
        if (!window.confirm(message)) return;
        if (!validThumbnail(editForm)) return;
        const body = new FormData();
        body.set("title", title);
        body.set("registeredOn", registeredOn);
        body.set("artistNames", selectedArtists(editDialog, editForm.querySelector("[name='extraArtists']")).join(","));
        if (replacing) body.set("file", editFile.files[0]);
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
