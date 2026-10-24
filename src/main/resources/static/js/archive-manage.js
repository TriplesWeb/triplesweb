(function () {
    const kind = document.querySelector("[data-archive-kind]")?.dataset.archiveKind || "image";
    const noun = kind === "video" ? "영상" : "사진";

    function authHeaders() {
        const headerMap = {};
        const csrf = document.querySelector('meta[name="_csrf"]');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
        if (csrf && csrfHeader && csrf.content && csrfHeader.content) headerMap[csrfHeader.content] = csrf.content;
        return window.AboutAdminAuth ? AboutAdminAuth.applyAuthHeaders(headerMap) : headerMap;
    }

    function openDialog(dialog) {
        if (!dialog) return;
        dialog.hidden = false;
        document.body.style.overflow = "hidden";
        dialog.querySelector("input, button")?.focus();
    }

    function closeDialog(dialog) {
        if (!dialog) return;
        dialog.hidden = true;
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
        }
        return node;
    }

    function showPreview(box, url) {
        if (!box) return;
        const full = box.querySelector(".ar-preview-full");
        const card = box.querySelector(".ar-preview-card");
        if (!url || !full || !card) {
            box.hidden = true;
            full && (full.innerHTML = "");
            card && (card.innerHTML = "");
            return;
        }
        full.replaceChildren(mediaNode(url, false));
        card.replaceChildren(mediaNode(url, true));
        box.hidden = false;
    }

    function rememberUrl(file) {
        if (!file) return "";
        const url = URL.createObjectURL(file);
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
        (extra?.value || "").split(",").map(part => part.trim()).filter(Boolean).forEach(name => {
            if (!values.includes(name)) values.push(name);
        });
        return values;
    }

    function applyArtists(root, extra, raw) {
        if (window.MemberPortraitPicker) MemberPortraitPicker.clear(root);
        const names = (raw || "").split(",").map(part => part.trim()).filter(Boolean);
        const known = new Set();
        root.querySelectorAll(".mpp-item").forEach(button => {
            const value = (button.dataset.value || "").trim();
            if (names.includes(value)) {
                MemberPortraitPicker.setActive(button, true);
                known.add(value);
            }
        });
        if (extra) extra.value = names.filter(name => !known.has(name)).join(", ");
        const count = root.querySelector("[data-picker-count]");
        if (count) count.textContent = String(root.querySelectorAll(".mpp-item.is-active").length);
    }

    function bindPicker(root) {
        if (!root || !window.MemberPortraitPicker) return;
        const count = root.querySelector("[data-picker-count]");
        MemberPortraitPicker.bindMultiSelect(root, {
            onChange: values => { if (count) count.textContent = String(values.length); }
        });
        root.querySelector("[data-picker-clear]")?.addEventListener("click", () => {
            MemberPortraitPicker.clear(root);
            if (count) count.textContent = "0";
        });
        MemberPortraitPicker.clear(root);
        if (count) count.textContent = "0";
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

    window.showAddModal = function () {
        if (!uploadForm || !uploadDialog) return;
        uploadForm.reset();
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
        const accepted = kind === "video" ? file.type.startsWith("video/") : file.type.startsWith("image/");
        if (file.type && !accepted) {
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
        if (!uploadPreview || uploadPreview.hidden) {
            alert("미리보기를 확인한 뒤 추가해 주세요.");
            return;
        }
        if (!window.confirm("이 " + noun + "을 추가하시겠습니까?")) return;
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

    function showEdit(button) {
        if (!editForm || !editDialog) return;
        editForm.reset();
        editForm.dataset.id = button.dataset.id || "";
        editForm.querySelector("[name='title']").value = button.dataset.title || "";
        applyArtists(editDialog, editForm.querySelector("[name='extraArtists']"), button.dataset.artists || "");
        if (editFile) releaseUrl(editFile._archiveLastFile);
        showPreview(editPreview, button.dataset.url || "");
        openDialog(editDialog);
    }

    editFile?.addEventListener("change", () => {
        const file = editFile.files && editFile.files[0];
        releaseUrl(editFile._archiveLastFile);
        editFile._archiveLastFile = file || null;
        if (!file) return;
        const accepted = kind === "video" ? file.type.startsWith("video/") : file.type.startsWith("image/");
        if (file.type && !accepted) {
            alert(kind === "video" ? "동영상 파일만 선택할 수 있습니다." : "이미지 파일만 선택할 수 있습니다.");
            editFile.value = "";
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
        if (!editForm.dataset.id) {
            alert("수정할 자료를 찾지 못했습니다.");
            return;
        }
        const replacing = Boolean(editFile?.files?.[0]);
        const message = replacing
            ? "이 " + noun + "을 수정하시겠습니까? 선택한 파일로 기존 미디어가 바뀝니다."
            : "이 " + noun + "을 수정하시겠습니까?";
        if (!window.confirm(message)) return;
        const body = new FormData();
        body.set("title", title);
        body.set("artistNames", selectedArtists(editDialog, editForm.querySelector("[name='extraArtists']")).join(","));
        if (replacing) body.set("file", editFile.files[0]);
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
