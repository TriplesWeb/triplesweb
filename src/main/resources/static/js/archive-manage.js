(function () {
    const kind = document.querySelector("[data-archive-kind]")?.dataset.archiveKind || "image";
    const noun = kind === "video" ? "영상" : "사진";
    const dateLabel = document.querySelector("[data-date-label]")?.dataset.dateLabel || (noun + " 날짜");
    const dialogTriggers = new WeakMap();
    /** 업로드 성공 후 모달을 유지할 때, 닫을 때 갱신할 Location (없으면 현재 URL). */
    let afterUploadLocation = null;
    /** 실패 후 재시도 시 동일 키로 중복 등록을 막는다. 성공하면 비운다. */
    let uploadIdempotencyKey = null;

    function authHeaders() {
        const headerMap = {};
        const csrf = document.querySelector('meta[name="_csrf"]');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
        if (csrf && csrfHeader && csrf.content && csrfHeader.content) headerMap[csrfHeader.content] = csrf.content;
        return window.AboutAdminAuth ? AboutAdminAuth.applyAuthHeaders(headerMap) : headerMap;
    }

    function newIdempotencyKey() {
        if (window.crypto && typeof window.crypto.randomUUID === "function") {
            return window.crypto.randomUUID();
        }
        return "idemp-" + Date.now().toString(36) + "-" + Math.random().toString(16).slice(2);
    }

    function resetUploadIdempotencyKey() {
        uploadIdempotencyKey = null;
    }

    function saveFailureMessage(status, fallback) {
        if (status === 403) {
            return "관리자 세션이 필요합니다. 입력값은 그대로 두었으니 다시 로그인한 뒤 저장해 주세요.";
        }
        return fallback || "요청에 실패했습니다. 입력값은 유지됩니다.";
    }

    function resolveArchivePath(locationHeader) {
        if (!locationHeader) return location.pathname + location.search;
        try {
            const url = new URL(locationHeader, window.location.href);
            return url.pathname + url.search + url.hash;
        } catch (_) {
            return locationHeader.startsWith("/") ? locationHeader : (location.pathname + location.search);
        }
    }

    /** 전체 reload 대신 목록 그리드만 다시 받아 붙인다. */
    function softRefreshArchive(locationHeader) {
        const target = resolveArchivePath(locationHeader);
        if (typeof window.refreshArchiveGrid === "function") {
            return window.refreshArchiveGrid(target).catch(() => {
                window.location.assign(target);
            });
        }
        window.location.assign(target);
        return Promise.resolve();
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
        const refreshAfterUpload = dialog.id === "uploadModal" && afterUploadLocation !== null;
        const refreshTo = afterUploadLocation;
        if (refreshAfterUpload) afterUploadLocation = null;
        dialog.hidden = true;
        dialog.querySelectorAll('input[type="file"]').forEach(input => {
            releaseUrl(input._archiveLastFile);
            (input._archiveLastFiles || []).forEach(releaseUrl);
            input._archiveLastFile = null;
            input._archiveLastFiles = null;
        });
        const edit = dialog.querySelector('#archiveEditForm');
        if (edit) {
            setSlots(edit, [], { syncInput: false });
            edit._archiveSlotPreviewIndex = -1;
            delete edit.dataset.originalImages;
        }
        dialogTriggers.get(dialog)?.focus();
        if (!document.querySelector(".ar-dialog:not([hidden])")) document.body.style.overflow = "";
        if (refreshAfterUpload) softRefreshArchive(refreshTo);
    }

    function removeCardFromGrid(id) {
        if (!id) return;
        document.querySelectorAll('#archiveGrid .ar-card[data-id="' + CSS.escape(String(id)) + '"]').forEach(card => {
            card.remove();
        });
        const detail = document.getElementById("archiveDetail");
        if (detail && !detail.hidden) {
            const editId = document.getElementById("archiveDetailEdit")?.dataset.id;
            if (String(editId || "") === String(id)) {
                detail.hidden = true;
                if (!document.querySelector(".ar-dialog:not([hidden])")) document.body.style.overflow = "";
            }
        }
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
        const badges = document.createElement('div');
        badges.className = 'ar-card-badges';
        badges.dataset.previewBadges = '';
        const source = document.createElement('div');
        source.className = 'ar-source-badge';
        source.dataset.previewSource = '';
        source.hidden = true;
        const unit = document.createElement('div');
        unit.className = 'ar-unit-badge';
        unit.dataset.previewUnit = '';
        unit.hidden = true;
        badges.append(source, unit);
        const meta = document.createElement('div');
        meta.className = 'ar-meta';
        meta.dataset.previewArtists = '';
        body.append(title, badges, meta);
        return body;
    }

    function matchUnitBadge(memberNames) {
        const selected = new Set((memberNames || []).map(name => String(name || '').trim()).filter(Boolean));
        if (selected.size < 2) return null;
        const checks = document.querySelectorAll('[data-unit-checks] input[type="checkbox"][data-members]');
        for (const cb of checks) {
            const members = window.UnitCheckboxGroup
                ? UnitCheckboxGroup.parseMembers(cb)
                : String(cb.dataset.members || '').split(',').map(part => part.trim()).filter(Boolean);
            if (!members.length || members.length !== selected.size) continue;
            if (!members.every(name => selected.has(name))) continue;
            const label = (cb.closest('label')?.querySelector('span')?.textContent || '').trim();
            if (!label) continue;
            const first = document.querySelector(`.mpp-item[data-value="${CSS.escape(members[0])}"]`);
            return { label, color: memberColor(first) };
        }
        return null;
    }

    function archiveSymbolLogo(frame) {
        const n = Number(frame);
        if (!Number.isInteger(n) || n < 1 || n > 24) return '';
        return '/images/archive/member-logos/' + n + '.png';
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

    function bumpImageEditToken(input) {
        if (!input) return 0;
        input._archiveEditToken = (input._archiveEditToken || 0) + 1;
        return input._archiveEditToken;
    }

    function isGifOrWebpFile(file) {
        if (!file) return false;
        const type = (file.type || '').toLowerCase();
        if (type === 'image/gif' || type === 'image/webp') return true;
        return /\.(gif|webp)$/i.test(file.name || '');
    }

    function findFourCC(bytes, label) {
        const a = label.charCodeAt(0);
        const b = label.charCodeAt(1);
        const c = label.charCodeAt(2);
        const d = label.charCodeAt(3);
        for (let i = 0; i < bytes.length - 3; i += 1) {
            if (bytes[i] === a && bytes[i + 1] === b && bytes[i + 2] === c && bytes[i + 3] === d) return true;
        }
        return false;
    }

    async function ensureAnimationFlag(file) {
        if (!file) return false;
        if (file._archiveAnimated != null) return file._archiveAnimated === true;
        if (!isGifOrWebpFile(file)) {
            file._archiveAnimated = false;
            return false;
        }
        try {
            const sampleSize = Math.min(file.size || 0, 2 * 1024 * 1024);
            const bytes = new Uint8Array(await file.slice(0, sampleSize).arrayBuffer());
            const type = (file.type || '').toLowerCase();
            const name = (file.name || '').toLowerCase();
            let animated = false;
            if (type === 'image/gif' || name.endsWith('.gif')) {
                let frames = 0;
                for (let i = 0; i < bytes.length; i += 1) {
                    if (bytes[i] === 0x2C) {
                        frames += 1;
                        if (frames > 1) {
                            animated = true;
                            break;
                        }
                    }
                }
            } else {
                animated = findFourCC(bytes, 'ANIM') || findFourCC(bytes, 'ANMF');
            }
            file._archiveAnimated = animated;
            return animated;
        } catch (_) {
            file._archiveAnimated = false;
            return false;
        }
    }

    function syncImageEditTools(box) {
        const tools = box?.querySelector('[data-image-edit-tools]');
        if (!tools) return;
        const source = box._previewSource;
        const sourceUrl = box._previewSourceUrl;
        const fromFile = !!(source && isImageFile(source));
        const fromUrl = !fromFile && !!sourceUrl && kind === 'image';
        const hasImage = fromFile || fromUrl;
        const animated = !!(source && source._archiveAnimated === true);
        const checking = !!(source && isGifOrWebpFile(source) && source._archiveAnimated == null);
        const editable = kind === 'image'
            && !box.hidden
            && hasImage
            && !animated
            && !checking
            && !box._archiveEditBusy;
        tools.hidden = !(kind === 'image' && !box.hidden && hasImage) || animated;
        tools.querySelectorAll('button').forEach(button => {
            button.disabled = !editable;
            if (!button.dataset.defaultTitle) button.dataset.defaultTitle = button.getAttribute('title') || '';
            button.title = animated
                ? '애니메이션 이미지는 편집할 수 없습니다.'
                : (button.dataset.defaultTitle || button.title);
        });
    }

    function showPreview(box, url, options = {}) {
        if (!box) return;
        const full = box.querySelector(".ar-preview-full");
        const card = box.querySelector(".ar-preview-card");
        const error = box.querySelector('[data-preview-error]');
        if (error) { error.hidden = true; error.textContent = ''; }
        delete box.dataset.invalid;
        delete box.dataset.pending;
        box._previewSource = options.sourceFile || null;
        box._previewSourceUrl = options.sourceFile ? null : (options.sourceUrl || url || null);
        box._previewIndex = Number.isInteger(options.index) ? options.index : 0;
        const form = box.closest('form');
        const fileInput = form?.querySelector('input[name="file"]');
        if (fileInput) fileInput._archivePreviewIndex = box._previewSource ? box._previewIndex : 0;
        if (options.sourceFile && isGifOrWebpFile(options.sourceFile)) {
            const watched = options.sourceFile;
            ensureAnimationFlag(watched).then(() => {
                if (box._previewSource === watched) syncImageEditTools(box);
            });
        }
        if (!url) {
            box.hidden = true;
            full && (full.innerHTML = "");
            card && (card.innerHTML = "");
            syncImageEditTools(box);
            return;
        }
        if (!full || !card) {
            box.hidden = false;
            syncImageEditTools(box);
            syncPreview(form);
            syncThumbnail(form);
            if (fileInput) syncFileName(fileInput);
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
        syncImageEditTools(box);
        syncPreview(form);
        syncThumbnail(form);
        if (fileInput) syncFileName(fileInput);
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

    function setInputFiles(input, files, { silent = false, previewIndex = 0, bumpEditToken = !silent } = {}) {
        if (!input) return;
        // 편집 결과로 파일을 바꿀 때는 토큰을 올리지 않는다. 올리면 finally 에서 버튼이 영구 disabled 된다.
        if (bumpEditToken) bumpImageEditToken(input);
        const transfer = new DataTransfer();
        files.forEach(file => transfer.items.add(file));
        input.files = transfer.files;
        input._archivePreviewIndex = previewIndex;
        if (silent) {
            input._archiveLastFile = files[0] || null;
            input._archiveLastFiles = files.slice();
            syncFileName(input);
            return;
        }
        input._archiveKeepPreviewIndex = true;
        syncFileName(input);
        input.dispatchEvent(new Event('change', { bubbles: true }));
    }

    function resolvePreviewIndex(input, files) {
        const keep = !!input._archiveKeepPreviewIndex;
        input._archiveKeepPreviewIndex = false;
        if (!files.length) return 0;
        if (!keep) return 0;
        const index = Number.isInteger(input._archivePreviewIndex) ? input._archivePreviewIndex : 0;
        return Math.min(Math.max(index, 0), files.length - 1);
    }

    function removeFileAt(input, index) {
        const files = Array.from(input?.files || []);
        const removed = files[index];
        if (!removed) return;
        releaseUrl(removed);
        const next = files.filter((_, i) => i !== index);
        const previewIndex = Math.min(index, Math.max(0, next.length - 1));
        setInputFiles(input, next, { previewIndex });
    }

    function outputTypeForImage(file) {
        const type = (file?.type || '').toLowerCase();
        if (type === 'image/jpeg' || type === 'image/jpg' || type === 'image/pjpeg') return 'image/jpeg';
        if (type === 'image/png') return 'image/png';
        if (type === 'image/webp') return 'image/webp';
        const ext = (file?.name || '').split('.').pop()?.toLowerCase();
        if (ext === 'jpg' || ext === 'jpeg' || ext === 'jfif') return 'image/jpeg';
        if (ext === 'png') return 'image/png';
        if (ext === 'webp') return 'image/webp';
        return 'image/png';
    }

    function renameImageFile(name, type) {
        const base = (name || 'image').replace(/\.[^.]+$/, '') || 'image';
        return base + '.' + extensionForImageType(type);
    }

    function loadImageBitmap(file) {
        if (typeof createImageBitmap === 'function') {
            return createImageBitmap(file).catch(() => null);
        }
        return Promise.resolve(null);
    }

    function loadHtmlImage(url) {
        return new Promise((resolve, reject) => {
            const image = new Image();
            image.onload = () => resolve(image);
            image.onerror = () => reject(new Error('image-load-failed'));
            image.src = url;
        });
    }

    async function transformImageFile(file, operation) {
        const bitmap = await loadImageBitmap(file);
        let width;
        let height;
        let drawSource;
        let releaseBitmap = false;
        if (bitmap) {
            width = bitmap.width;
            height = bitmap.height;
            drawSource = bitmap;
            releaseBitmap = true;
        } else {
            const url = rememberUrl(file);
            const image = await loadHtmlImage(url);
            width = image.naturalWidth || image.width;
            height = image.naturalHeight || image.height;
            drawSource = image;
        }
        if (!width || !height) {
            if (releaseBitmap && drawSource.close) drawSource.close();
            throw new Error('image-size-invalid');
        }
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        if (!ctx) {
            if (releaseBitmap && drawSource.close) drawSource.close();
            throw new Error('canvas-unavailable');
        }
        if (operation === 'rotate-cw' || operation === 'rotate-ccw') {
            canvas.width = height;
            canvas.height = width;
            ctx.translate(canvas.width / 2, canvas.height / 2);
            ctx.rotate(operation === 'rotate-cw' ? Math.PI / 2 : -Math.PI / 2);
            ctx.drawImage(drawSource, -width / 2, -height / 2);
        } else if (operation === 'flip-h') {
            canvas.width = width;
            canvas.height = height;
            ctx.translate(width, 0);
            ctx.scale(-1, 1);
            ctx.drawImage(drawSource, 0, 0);
        } else if (operation === 'flip-v') {
            canvas.width = width;
            canvas.height = height;
            ctx.translate(0, height);
            ctx.scale(1, -1);
            ctx.drawImage(drawSource, 0, 0);
        } else {
            if (releaseBitmap && drawSource.close) drawSource.close();
            throw new Error('unknown-operation');
        }
        if (releaseBitmap && drawSource.close) drawSource.close();
        const type = outputTypeForImage(file);
        const quality = type === 'image/jpeg' || type === 'image/webp' ? 0.92 : undefined;
        const blob = await new Promise((resolve, reject) => {
            canvas.toBlob(result => {
                if (result) resolve(result);
                else reject(new Error('blob-failed'));
            }, type, quality);
        });
        return new File([blob], renameImageFile(file.name, type), {
            type,
            lastModified: Date.now()
        });
    }

    async function fileFromImageUrl(url) {
        const response = await fetch(url, { credentials: 'same-origin' });
        if (!response.ok) throw new Error('fetch-failed');
        const blob = await response.blob();
        if (!(blob.type || '').startsWith('image/') && !/\.(jpe?g|jfif|png|webp|gif|bmp)(\?|$)/i.test(url)) {
            throw new Error('not-image');
        }
        const type = blob.type && blob.type.startsWith('image/') ? blob.type : 'image/jpeg';
        const name = (String(url).split('?')[0].split('/').pop() || 'image.jpg').replace(/[^\w.\-()+]+/g, '_') || 'image.jpg';
        return new File([blob], name, { type, lastModified: Date.now() });
    }

    async function applyImageEdit(form, operation) {
        if (kind !== 'image' || !form) return;
        const input = form.querySelector('input[name="file"]');
        const preview = form.querySelector('.ar-preview');
        if (!input || !preview || preview.hidden || preview._archiveEditBusy) return;
        const slots = slotsOf(form);
        const slotIndex = Number.isInteger(form._archiveSlotPreviewIndex) ? form._archiveSlotPreviewIndex : -1;
        const useSlots = slots.length > 0 && slotIndex >= 0 && slotIndex < slots.length;
        let source = null;
        let sourceSlot = null;
        let fileIndex = Number.isInteger(preview._previewIndex) ? preview._previewIndex : 0;
        if (useSlots) {
            sourceSlot = slots[slotIndex];
            if (sourceSlot.kind === 'file') {
                source = sourceSlot.file;
                fileIndex = slots.slice(0, slotIndex + 1).filter(item => item.kind === 'file').length - 1;
            }
        } else {
            source = Array.from(input.files || [])[fileIndex] || null;
        }
        if ((!source || !isImageFile(source)) && !(useSlots && sourceSlot?.kind === 'url')) {
            window.SiteFeedback.alert('편집할 사진을 먼저 선택해 주세요.');
            return;
        }
        const editToken = input._archiveEditToken || 0;
        const tools = preview.querySelector('[data-image-edit-tools]');
        const buttons = tools ? Array.from(tools.querySelectorAll('button')) : [];
        preview._archiveEditBusy = true;
        buttons.forEach(button => { button.disabled = true; });
        preview.dataset.pending = 'true';
        const finish = window.SiteFeedback.beginProgress();
        try {
            if (!source && sourceSlot?.kind === 'url') {
                source = await fileFromImageUrl(sourceSlot.url);
                if ((input._archiveEditToken || 0) !== editToken) return;
            }
            if (await ensureAnimationFlag(source)) {
                syncImageEditTools(preview);
                window.SiteFeedback.alert('애니메이션 이미지는 회전·뒤집기를 할 수 없습니다.');
                return;
            }
            const edited = await transformImageFile(source, operation);
            if ((input._archiveEditToken || 0) !== editToken) return;
            if (useSlots) {
                const nextSlots = slots.slice();
                if (sourceSlot.kind === 'file') releaseUrl(sourceSlot.file);
                nextSlots[slotIndex] = { kind: 'file', file: edited };
                setSlots(form, nextSlots);
                previewSlot(form, input, slotIndex);
                return;
            }
            const current = Array.from(input.files || [])[fileIndex];
            if (current !== source) return;
            releaseUrl(source);
            const next = Array.from(input.files || []);
            if (fileIndex >= next.length) return;
            next[fileIndex] = edited;
            setInputFiles(input, next, { silent: true, previewIndex: fileIndex, bumpEditToken: false });
            showPreview(preview, rememberUrl(edited), { sourceFile: edited, index: fileIndex });
        } catch (_) {
            if ((input._archiveEditToken || 0) === editToken) {
                delete preview.dataset.pending;
                window.SiteFeedback.alert('사진을 편집하지 못했습니다. 다른 이미지로 다시 시도해 주세요.');
            }
        } finally {
            finish();
            preview._archiveEditBusy = false;
            syncImageEditTools(preview);
        }
    }

    function bindImageEditTools(form) {
        const tools = form?.querySelector('[data-image-edit-tools]');
        if (!tools || tools.dataset.bound === 'true') return;
        tools.dataset.bound = 'true';
        tools.addEventListener('click', event => {
            const button = event.target.closest('[data-image-edit]');
            if (!button || !tools.contains(button)) return;
            event.preventDefault();
            applyImageEdit(form, button.dataset.imageEdit);
        });
    }

    function parseImageUrls(raw, fallback) {
        const single = String(fallback || '').trim();
        const text = String(raw || '').trim();
        if (text) {
            try {
                const parsed = JSON.parse(text);
                if (Array.isArray(parsed)) {
                    const urls = parsed.map(url => String(url || '').trim()).filter(Boolean);
                    if (urls.length) return urls;
                }
            } catch (_) {
                /* fall through */
            }
        }
        return single ? [single] : [];
    }

    function keptUrlsOf(form) {
        return slotsOf(form).filter(slot => slot.kind === 'url').map(slot => slot.url);
    }

    function setKeptUrls(form, urls) {
        if (!form) return;
        setSlots(form, (urls || []).map(url => ({ kind: 'url', url })), { syncInput: false });
    }

    function slotsOf(form) {
        return Array.isArray(form?._archiveMediaSlots) ? form._archiveMediaSlots : [];
    }

    function setSlots(form, slots, { syncInput = true } = {}) {
        if (!form) return;
        form._archiveMediaSlots = Array.isArray(slots) ? slots.slice() : [];
        if (!form._archiveMediaSlots.includes(form._archiveCoverSlot)) {
            form._archiveCoverSlot = form._archiveMediaSlots[0];
        }
        if (!syncInput) return;
        const input = form.querySelector('input[name="file"]');
        if (!input) return;
        const files = form._archiveMediaSlots.filter(slot => slot.kind === 'file').map(slot => slot.file);
        const transfer = new DataTransfer();
        files.forEach(file => transfer.items.add(file));
        input.files = transfer.files;
        input._archiveLastFile = files[0] || null;
        input._archiveLastFiles = files.slice();
    }

    function slotRoot(form) {
        return form?.closest('.ar-dialog') || form;
    }

    function captureSlot(form) {
        if (kind !== 'image' || !form || form._archiveApplyingSlot) return;
        const slot = slotsOf(form)[form._archiveSlotPreviewIndex];
        if (!slot) return;
        slot.artists = selectedArtists(slotRoot(form));
    }

    function applySlot(form, slot) {
        if (kind !== 'image' || !form || !slot || !Array.isArray(slot.artists)) return;
        form._archiveApplyingSlot = true;
        try {
            applyArtists(slotRoot(form), slot.artists.join(','));
        } finally {
            form._archiveApplyingSlot = false;
        }
    }

    function artistRows(form) {
        return slotsOf(form).map(slot => (Array.isArray(slot.artists) ? slot.artists : selectedArtists(slotRoot(form))).join(','));
    }

    function updatePhotoStep(form) {
        const button = form?.querySelector('[type="submit"]');
        if (!button) return;
        if (!button.dataset.defaultLabel) button.dataset.defaultLabel = button.textContent.trim();
        const slots = slotsOf(form);
        const index = Number.isInteger(form._archiveSlotPreviewIndex) ? form._archiveSlotPreviewIndex : 0;
        const pending = kind === 'image' && slots.length > 1 && slots.some((slot, i) => i !== index && !slot.configured);
        button.textContent = pending ? '다음 사진' : button.dataset.defaultLabel;
    }

    function advancePhotoSetup(form, status) {
        if (kind !== 'image') return false;
        const slots = slotsOf(form);
        if (slots.length < 2) return false;
        const index = Number.isInteger(form._archiveSlotPreviewIndex) ? form._archiveSlotPreviewIndex : 0;
        if (slots[index]) {
            slots[index].artists = selectedArtists(slotRoot(form));
            slots[index].configured = true;
        }
        const next = slots.findIndex(slot => !slot.configured);
        if (next < 0) return false;
        if (!Array.isArray(slots[next].artists)) slots[next].artists = [];
        const input = form.querySelector('input[name="file"]');
        previewSlot(form, input, next);
        status?.((next + 1) + '번째 사진 설정입니다. 앞 사진은 미리보기로 남아 있습니다.');
        return true;
    }

    function previewSlot(form, input, index) {
        const slots = slotsOf(form);
        const slot = slots[index];
        const preview = form?.querySelector('.ar-preview');
        if (!slot || !preview) return;
        if (form._archiveSlotPreviewIndex !== index) captureSlot(form);
        form._archiveSlotPreviewIndex = index;
        if (slot.kind === 'url') {
            if (input) input._archivePreviewIndex = -1;
            showPreview(preview, slot.url, { sourceUrl: slot.url, index });
        } else {
            const fileIndex = slots.slice(0, index + 1).filter(item => item.kind === 'file').length - 1;
            if (input) input._archivePreviewIndex = fileIndex;
            showPreview(preview, rememberUrl(slot.file), { sourceFile: slot.file, index: fileIndex });
        }
        applySlot(form, slot);
        syncFileName(input);
        updatePhotoStep(form);
    }

    function removeSlotAt(form, index) {
        const slots = slotsOf(form);
        if (index < 0 || index >= slots.length) return;
        const [removed] = slots.splice(index, 1);
        if (removed?.kind === 'file') releaseUrl(removed.file);
        setSlots(form, slots);
        const input = form.querySelector('input[name="file"]');
        const next = Math.min(index, Math.max(0, slots.length - 1));
        if (slots.length) previewSlot(form, input, next);
        else {
            form._archiveSlotPreviewIndex = -1;
            showPreview(form.querySelector('.ar-preview'), form.dataset.originalUrl || '');
            if (input) syncFileName(input);
        }
    }

    function moveSlot(form, from, to) {
        const slots = slotsOf(form);
        if (from === to || from < 0 || to < 0 || from >= slots.length || to >= slots.length) return;
        const next = slots.slice();
        const [item] = next.splice(from, 1);
        next.splice(to, 0, item);
        setSlots(form, next);
        const input = form.querySelector('input[name="file"]');
        previewSlot(form, input, to);
    }

    function bindTileDrag(tile, form, index) {
        tile.draggable = true;
        tile.addEventListener('dragstart', event => {
            if (event.target.closest('.ar-file-remove, .ar-file-cover')) {
                event.preventDefault();
                return;
            }
            event.dataTransfer.effectAllowed = 'move';
            event.dataTransfer.setData('text/plain', String(index));
            tile.classList.add('is-dragging');
        });
        tile.addEventListener('dragend', () => {
            tile.classList.remove('is-dragging');
            form.querySelectorAll('.ar-file-tile.is-drop-target').forEach(node => node.classList.remove('is-drop-target'));
        });
        tile.addEventListener('dragover', event => {
            event.preventDefault();
            event.dataTransfer.dropEffect = 'move';
            tile.classList.add('is-drop-target');
        });
        tile.addEventListener('dragleave', () => tile.classList.remove('is-drop-target'));
        tile.addEventListener('drop', event => {
            event.preventDefault();
            event.stopPropagation();
            tile.classList.remove('is-drop-target');
            const from = Number(event.dataTransfer.getData('text/plain'));
            if (!Number.isInteger(from)) return;
            moveSlot(form, from, index);
        });
    }

    function fileNameFromUrl(url) {
        if (!url) return '';
        try {
            const path = new URL(url, window.location.origin).pathname;
            return decodeURIComponent(path.split('/').filter(Boolean).pop() || '');
        } catch (_) {
            return String(url).split('?')[0].split('/').filter(Boolean).pop() || '';
        }
    }

    function syncFileName(input) {
        const label = input?.closest('.ar-file')?.querySelector('[data-file-name]');
        if (!label) return;
        const form = input.closest('form');
        const preview = form?.querySelector('.ar-preview');
        if (kind === 'image' && input.name === 'file' && form && !Array.isArray(form._archiveMediaSlots)) {
            const kept = [];
            const files = Array.from(input.files || []).filter(isImageFile);
            form._archiveMediaSlots = kept.concat(files.map(file => ({ kind: 'file', file })));
        }
        if (kind === 'image' && input.name === 'file' && form && Array.isArray(form._archiveMediaSlots)
            && !form._archiveMediaSlots.length && (input.files?.length || 0) > 0) {
            form._archiveMediaSlots = Array.from(input.files || []).filter(isImageFile).map(file => ({ kind: 'file', file }));
        }
        const slots = kind === 'image' && input.name === 'file' ? slotsOf(form) : [];
        const files = Array.from(input.files || []);
        const activeSlot = Number.isInteger(form?._archiveSlotPreviewIndex) ? form._archiveSlotPreviewIndex : -1;
        const originalVideo = kind === 'video' && input.name === 'file' ? (form?.dataset.originalUrl || '') : '';
        const hasMedia = slots.length > 0 || files.length > 0 || !!originalVideo;
        label.classList.toggle('has-files', hasMedia);
        label.classList.toggle('has-thumbs', slots.length > 0 || files.some(isImageFile) || !!originalVideo);
        if (!hasMedia) {
            label.replaceChildren();
            label.textContent = '선택된 파일 없음';
            return;
        }
        if (kind === 'video' && input.name === 'file' && !files.length && originalVideo) {
            const name = fileNameFromUrl(originalVideo) || '현재 영상';
            const poster = form.dataset.thumbnailUrl || form.dataset.originalThumbnail || '';
            label.replaceChildren();
            const tile = document.createElement('div');
            tile.className = 'ar-file-tile is-active is-set';
            tile.title = name;
            const thumb = document.createElement('button');
            thumb.type = 'button';
            thumb.className = 'ar-file-thumb';
            thumb.setAttribute('aria-label', name + ' 미리보기');
            if (poster) {
                const img = document.createElement('img');
                img.alt = '';
                img.decoding = 'async';
                img.src = poster;
                thumb.appendChild(img);
            } else {
                thumb.textContent = '▶';
            }
            thumb.addEventListener('click', event => {
                event.preventDefault();
                event.stopPropagation();
                if (preview) showPreview(preview, originalVideo);
            });
            const mark = document.createElement('span');
            mark.className = 'ar-file-cover';
            mark.setAttribute('aria-pressed', 'true');
            mark.textContent = '현재 영상';
            tile.append(thumb, mark);
            label.appendChild(tile);
            const caption = document.createElement('span');
            caption.className = 'ar-file-current-name';
            caption.textContent = name;
            label.appendChild(caption);
            return;
        }
        label.replaceChildren();
        if (slots.length) {
            slots.forEach((slot, index) => {
                const tile = document.createElement('div');
                tile.className = 'ar-file-tile' + (index === activeSlot ? ' is-active' : '') + (slot.configured ? ' is-set' : '');
                tile.title = slot.kind === 'url' ? '저장된 사진 ' + (index + 1) : (slot.file.name || '새 사진');
                const thumb = document.createElement('button');
                thumb.type = 'button';
                thumb.className = 'ar-file-thumb';
                thumb.setAttribute('aria-label', (slot.kind === 'url' ? '저장된 사진 ' + (index + 1) : slot.file.name) + ' 미리보기');
                const img = document.createElement('img');
                img.alt = '';
                img.decoding = 'async';
                img.src = slot.kind === 'url' ? slot.url : rememberUrl(slot.file);
                thumb.appendChild(img);
                thumb.addEventListener('click', event => {
                    event.preventDefault();
                    event.stopPropagation();
                    previewSlot(form, input, index);
                });
                const remove = document.createElement('button');
                remove.type = 'button';
                remove.className = 'ar-file-remove';
                remove.textContent = '×';
                remove.title = '사진 제거';
                remove.setAttribute('aria-label', '사진 제거');
                remove.draggable = false;
                remove.addEventListener('click', event => {
                    event.preventDefault();
                    event.stopPropagation();
                    removeSlotAt(form, index);
                });
                const cover = document.createElement('button');
                cover.type = 'button';
                cover.className = 'ar-file-cover';
                const isCover = slot === (form._archiveCoverSlot || slots[0]);
                cover.textContent = isCover ? '대표 사진' : '대표로 설정';
                cover.setAttribute('aria-pressed', String(isCover));
                cover.addEventListener('click', event => {
                    event.preventDefault();
                    event.stopPropagation();
                    form._archiveCoverSlot = slot;
                    syncFileName(input);
                    label.querySelectorAll('.ar-file-cover')[index]?.focus();
                });
                tile.append(thumb, remove, cover);
                bindTileDrag(tile, form, index);
                label.appendChild(tile);
            });
            return;
        }
        files.forEach((file, index) => {
            if (isImageFile(file)) {
                const tile = document.createElement('div');
                tile.className = 'ar-file-tile' + (index === (Number.isInteger(input._archivePreviewIndex) ? input._archivePreviewIndex : 0) ? ' is-active' : '');
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
                    if (preview && input.name === 'file') {
                        showPreview(preview, rememberUrl(file), { sourceFile: file, index });
                        syncFileName(input);
                    }
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
        const sourceBadge = preview.querySelector('[data-preview-source]');
        if (sourceBadge) {
            const select = form.querySelector('[name="source"]');
            const value = (select?.value || '').trim();
            const label = value
                ? (select.options[select.selectedIndex]?.textContent || '').trim()
                : '';
            sourceBadge.hidden = !label;
            sourceBadge.dataset.source = value.toLowerCase();
            sourceBadge.textContent = label;
        }
        const meta = preview.querySelector('[data-preview-artists]');
        const unitBadge = preview.querySelector('[data-preview-unit]');
        const badgeRow = preview.querySelector('[data-preview-badges]');
        let memberNames = [];
        if (meta) {
            const items = selectedMemberItems(form.closest('.ar-dialog'));
            meta.style.setProperty('--member-count', String(items.length || 1));
            meta.replaceChildren();
            items.forEach(button => {
                const name = (button.dataset.value || button.dataset.name || '').trim();
                if (name) {
                    memberNames.push(name);
                    meta.appendChild(memberChip(button, name));
                }
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
        if (unitBadge) {
            const matched = matchUnitBadge(memberNames);
            unitBadge.hidden = !matched;
            if (matched) {
                unitBadge.textContent = matched.label;
                unitBadge.title = matched.label;
                unitBadge.style.setProperty('--unit-color', matched.color || '#a78bfa');
            } else {
                unitBadge.textContent = '';
                unitBadge.removeAttribute('title');
            }
        }
        if (badgeRow) {
            const showSource = sourceBadge && !sourceBadge.hidden;
            const showUnit = unitBadge && !unitBadge.hidden;
            badgeRow.hidden = !showSource && !showUnit;
        }
        const info = preview.querySelector('[data-preview-file]');
        const slots = slotsOf(form);
        const files = Array.from(form.querySelector('input[name="file"]')?.files || []);
        const kept = keptUrlsOf(form);
        const newCount = slots.filter(slot => slot.kind === 'file').length || files.length;
        if (info) {
            if (!slots.length && !files.length) {
                info.textContent = kind === 'video' ? '현재 저장된 영상' : '현재 저장된 사진';
            } else if (slots.length) {
                const saved = kept.length;
                const added = slots.filter(slot => slot.kind === 'file').length;
                if (saved && added) info.textContent = `저장된 ${saved}장 + 새 사진 ${added}장`;
                else if (saved) info.textContent = saved === 1 ? '현재 저장된 사진' : `현재 저장된 사진 ${saved}장`;
                else if (added === 1) {
                    const file = slots.find(slot => slot.kind === 'file')?.file;
                    info.textContent = file
                        ? `${file.name} · ${(file.size / 1024).toLocaleString('ko', { maximumFractionDigits: 1 })} KB`
                        : '1장 선택';
                } else info.textContent = `${added}장 선택`;
            } else if (files.length === 1) {
                info.textContent = `${files[0].name} · ${(files[0].size / 1024).toLocaleString('ko', { maximumFractionDigits: 1 })} KB`;
            } else {
                info.textContent = `${newCount}장 선택`;
            }
        }
    }

    function unitChecksRoot(root) {
        return root?.querySelector?.('[data-unit-checks]') || null;
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
        window.UnitCheckboxGroup?.syncFromMembers(unitChecksRoot(root), values);
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
            window.UnitCheckboxGroup?.setChecks(unitChecksRoot(root), []);
            renderArtists(root);
        });
        window.UnitCheckboxGroup?.bindMemberHelper(unitChecksRoot(root), {
            getMembers: () => selectedArtists(root),
            applyMembers: (names) => applyArtists(root, names.join(','))
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

    const COVER_FILM_COUNT = 12;
    const COVER_SLIDER_MAX = 1000;

    function coverPicker(form) {
        return form?.querySelector('[data-cover-picker]') || null;
    }

    function formatCoverSeconds(seconds) {
        const value = Math.max(0, Number(seconds) || 0);
        return (Math.round(value * 10) / 10).toFixed(1).replace(/\.0$/, '') + '초';
    }

    function formatCoverTime(seconds, duration) {
        return formatCoverSeconds(seconds) + ' / ' + formatCoverSeconds(duration);
    }

    function waitMediaEvent(element, eventName) {
        return new Promise((resolve, reject) => {
            const onOk = () => {
                element.removeEventListener('error', onErr);
                resolve();
            };
            const onErr = () => {
                element.removeEventListener(eventName, onOk);
                reject(new Error('media-' + eventName));
            };
            element.addEventListener(eventName, onOk, { once: true });
            element.addEventListener('error', onErr, { once: true });
        });
    }

    function seekVideo(video, time) {
        return new Promise((resolve, reject) => {
            if (!video) {
                reject(new Error('no-video'));
                return;
            }
            const target = Math.max(0, time);
            if (Math.abs((video.currentTime || 0) - target) < 0.001) {
                resolve();
                return;
            }
            const onSeeked = () => {
                video.removeEventListener('error', onErr);
                resolve();
            };
            const onErr = () => {
                video.removeEventListener('seeked', onSeeked);
                reject(new Error('seek-failed'));
            };
            video.addEventListener('seeked', onSeeked, { once: true });
            video.addEventListener('error', onErr, { once: true });
            try {
                video.currentTime = target;
            } catch (error) {
                video.removeEventListener('seeked', onSeeked);
                video.removeEventListener('error', onErr);
                reject(error);
            }
        });
    }

    function blobFromVideo(video, quality = 0.86) {
        return new Promise((resolve, reject) => {
            const width = video.videoWidth || 0;
            const height = video.videoHeight || 0;
            if (!width || !height) {
                reject(new Error('empty-frame'));
                return;
            }
            const canvas = document.createElement('canvas');
            canvas.width = width;
            canvas.height = height;
            const ctx = canvas.getContext('2d');
            if (!ctx) {
                reject(new Error('canvas-unavailable'));
                return;
            }
            ctx.drawImage(video, 0, 0, width, height);
            canvas.toBlob((blob) => {
                if (!blob) {
                    reject(new Error('blob-failed'));
                    return;
                }
                resolve(blob);
            }, 'image/jpeg', quality);
        });
    }

    function captureVideoFrame(video, time, quality = 0.86) {
        return seekVideo(video, time).then(() => blobFromVideo(video, quality));
    }

    function coverTimeFromSlider(form, sliderValue) {
        const duration = form._archiveCover?.duration || 0;
        if (duration <= 0) return 0;
        const ratio = Math.min(1, Math.max(0, Number(sliderValue) / COVER_SLIDER_MAX));
        return ratio * Math.max(0, duration - 0.05);
    }

    function releaseCoverFrames(form) {
        const state = form?._archiveCover;
        if (!state) return;
        (state.filmUrls || []).forEach((url) => URL.revokeObjectURL(url));
        state.filmUrls = [];
        state.duration = 0;
        state.ready = false;
        state.time = 0;
        if (state.objectUrl) {
            URL.revokeObjectURL(state.objectUrl);
            state.objectUrl = '';
        }
        state.token = (state.token || 0) + 1;
        const scrub = form.querySelector('[data-cover-scrub]');
        const slider = form.querySelector('[data-cover-slider]');
        const film = form.querySelector('[data-cover-frames]');
        const timeLabel = form.querySelector('[data-cover-time]');
        const stageVideo = form.querySelector('[data-cover-stage-video]');
        if (film) film.replaceChildren();
        if (scrub) scrub.hidden = true;
        if (slider) {
            slider.value = '0';
            slider.disabled = true;
        }
        if (timeLabel) {
            timeLabel.hidden = true;
            timeLabel.textContent = '';
        }
        if (stageVideo) {
            stageVideo.pause?.();
            stageVideo.removeAttribute('src');
            stageVideo.load?.();
            stageVideo.hidden = true;
        }
    }

    function setCoverUiState(form, stateName) {
        const picker = coverPicker(form);
        if (!picker) return;
        picker.classList.remove('is-loading', 'is-error', 'is-success');
        if (stateName) picker.classList.add(stateName);
    }

    function updateCoverWindow(form, sliderValue) {
        const windowEl = form.querySelector('[data-cover-window]');
        const scrub = form.querySelector('[data-cover-scrub]');
        if (!windowEl || !scrub) return;
        const max = COVER_SLIDER_MAX;
        const ratio = Math.min(1, Math.max(0, Number(sliderValue) / max));
        const scrubWidth = scrub.clientWidth || 1;
        const windowWidth = windowEl.offsetWidth || scrubWidth * 0.18;
        const travel = Math.max(0, scrubWidth - windowWidth);
        windowEl.style.transform = 'translateX(' + (ratio * travel) + 'px)';
    }

    function showCoverScrubPreview(form) {
        const stageVideo = form.querySelector('[data-cover-stage-video]');
        const stageImage = form.querySelector('[data-cover-stage-image]');
        const empty = form.querySelector('[data-cover-empty]');
        if (stageImage) {
            stageImage.hidden = true;
            stageImage.removeAttribute('src');
        }
        if (stageVideo) stageVideo.hidden = false;
        if (empty) empty.hidden = true;
    }

    function renderCoverFilm(form, urls) {
        const film = form.querySelector('[data-cover-frames]');
        if (!film) return;
        film.replaceChildren();
        urls.forEach((url) => {
            const img = document.createElement('img');
            img.src = url;
            img.alt = '';
            img.draggable = false;
            film.append(img);
        });
    }

    async function commitCoverAtTime(form, time, { auto = false } = {}) {
        const state = form._archiveCover;
        const video = form.querySelector('[data-cover-stage-video]');
        const input = form.querySelector('[name="thumbnail"]');
        if (!state?.ready || !video || !input) return false;
        if (auto && form._archiveCoverSource === 'gallery') return false;
        const clear = form.querySelector('[name="clearThumbnail"]');
        if (clear) clear.value = 'false';
        form.dataset.thumbnailPending = 'true';
        try {
            const blob = await captureVideoFrame(video, time);
            if (!form._archiveCover || form._archiveCover.token !== state.token || (auto && form._archiveCoverSource === 'gallery')) {
                delete form.dataset.thumbnailPending;
                return false;
            }
            const file = new File([blob], 'cover-' + Math.round(time * 10) + '.jpg', { type: 'image/jpeg' });
            releaseUrl(input._archiveLastFile);
            setInputFiles(input, [file], { silent: true });
            input._archiveLastFile = file;
            form._archiveCoverSource = 'scrub';
            form._archiveCover.time = time;
            const url = rememberUrl(file);
            form.dataset.thumbnailUrl = url;
            delete form.dataset.thumbnailPending;
            delete form.dataset.thumbnailInvalid;
            const infoNode = form.querySelector('[data-thumbnail-info]');
            if (infoNode) {
                infoNode.textContent = auto
                    ? '첫 장면 자동 커버'
                    : formatCoverTime(time, state.duration);
            }
            showCoverScrubPreview(form);
            syncThumbnail(form);
            setCoverUiState(form, 'is-success');
            return true;
        } catch (_) {
            delete form.dataset.thumbnailPending;
            form.dataset.thumbnailInvalid = 'true';
            setCoverUiState(form, 'is-error');
            return false;
        }
    }

    async function scrubCoverTo(form, sliderValue, { commit = false, auto = false } = {}) {
        const state = form._archiveCover;
        const video = form.querySelector('[data-cover-stage-video]');
        const timeLabel = form.querySelector('[data-cover-time]');
        if (!state?.ready || !video) return;
        const time = coverTimeFromSlider(form, sliderValue);
        state.time = time;
        updateCoverWindow(form, sliderValue);
        if (timeLabel) {
            timeLabel.hidden = false;
            // 좁은 선택 창에는 현재 시각만 — 전체 길이는 아래 상태에 둔다.
            timeLabel.textContent = formatCoverSeconds(time);
        }
        showCoverScrubPreview(form);
        const infoNode = form.querySelector('[data-thumbnail-info]');
        if (infoNode && !commit) infoNode.textContent = formatCoverTime(time, state.duration);
        try {
            await seekVideo(video, time);
        } catch (_) {
            return;
        }
        if (commit) await commitCoverAtTime(form, time, { auto });
    }

    async function ensureAutoCover(form) {
        if (kind !== 'video' || !form) return true;
        const input = form.querySelector('[name="thumbnail"]');
        if (!input) return true;
        if (input.files?.[0]) return true;
        const clear = form.querySelector('[name="clearThumbnail"]');
        // 커버를 지운 상태이거나, 저장된 커버를 그대로 두는 메타데이터 수정이면 자동 생성하지 않는다.
        if (clear?.value === 'true') return true;
        if (form.dataset.originalThumbnail
            && form._archiveCoverSource !== 'scrub'
            && form._archiveCoverSource !== 'gallery') {
            return true;
        }
        if (form._archiveCoverSource === 'gallery' || form._archiveCoverSource === 'saved') return true;
        if (form._archiveCover?.ready) {
            const slider = form.querySelector('[data-cover-slider]');
            if (slider) slider.value = '0';
            return scrubCoverTo(form, 0, { commit: true, auto: true });
        }
        return true;
    }

    function coverChosenByUser(form) {
        const input = form?.querySelector('[name="thumbnail"]');
        return Boolean(input?.files?.[0])
            || form?._archiveCoverSource === 'gallery'
            || form?._archiveCoverSource === 'saved';
    }

    async function loadCoverFromVideo(form, source, { autoSelect = true } = {}) {
        if (kind !== 'video' || !form || !source) return;
        const picker = coverPicker(form);
        const scrub = form.querySelector('[data-cover-scrub]');
        const slider = form.querySelector('[data-cover-slider]');
        const stageVideo = form.querySelector('[data-cover-stage-video]');
        if (!picker || !scrub || !slider || !stageVideo) return;
        if (!form._archiveCover) form._archiveCover = { filmUrls: [], duration: 0, token: 0 };
        releaseCoverFrames(form);
        const token = form._archiveCover.token;
        scrub.hidden = false;
        slider.disabled = true;
        slider.classList.add('is-loading');
        setCoverUiState(form, 'is-loading');
        const info = form.querySelector('[data-thumbnail-info]');
        if (info) info.textContent = '장면을 준비하는 중…';
        const objectUrl = typeof source === 'string' ? '' : URL.createObjectURL(source);
        if (objectUrl) form._archiveCover.objectUrl = objectUrl;
        if (typeof source === 'string') stageVideo.crossOrigin = 'anonymous';
        else stageVideo.removeAttribute('crossorigin');
        stageVideo.muted = true;
        stageVideo.playsInline = true;
        stageVideo.preload = 'auto';
        stageVideo.src = typeof source === 'string' ? source : objectUrl;
        try {
            await waitMediaEvent(stageVideo, 'loadedmetadata');
            if (token !== form._archiveCover.token) return;
            const duration = Number.isFinite(stageVideo.duration) && stageVideo.duration > 0 ? stageVideo.duration : 0;
            form._archiveCover.duration = duration;
            const count = duration > 0 ? COVER_FILM_COUNT : 1;
            const filmUrls = [];
            for (let i = 0; i < count; i += 1) {
                const time = count === 1 || duration <= 0
                    ? 0
                    : (i / (count - 1)) * Math.max(0, duration - 0.05);
                const blob = await captureVideoFrame(stageVideo, time);
                if (token !== form._archiveCover.token) return;
                filmUrls.push(URL.createObjectURL(blob));
            }
            form._archiveCover.filmUrls = filmUrls;
            form._archiveCover.ready = true;
            renderCoverFilm(form, filmUrls);
            slider.disabled = false;
            slider.classList.remove('is-loading');
            slider.value = '0';
            updateCoverWindow(form, 0);
            if (token !== form._archiveCover.token) return;
            // 프레임 준비 중 갤러리/저장 커버를 고르면 첫 장면 자동 커밋으로 덮지 않는다.
            if (autoSelect && !coverChosenByUser(form)) {
                await scrubCoverTo(form, 0, { commit: true, auto: true });
            } else {
                await scrubCoverTo(form, 0, { commit: false });
                if (coverChosenByUser(form)) setCoverUiState(form, 'is-success');
                else setCoverUiState(form, '');
            }
        } catch (_) {
            if (token !== form._archiveCover.token) return;
            setCoverUiState(form, 'is-error');
            slider.classList.add('is-error');
            if (info) info.textContent = '장면을 읽지 못했습니다. 갤러리에서 커버를 선택해 주세요.';
            const error = form.querySelector('[data-thumbnail-error]');
            if (error) {
                error.textContent = '영상 장면을 준비하지 못했습니다. 갤러리 이미지로 커버를 넣을 수 있습니다.';
                error.hidden = false;
            }
        } finally {
            slider.classList.remove('is-loading');
        }
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

    function showThumbnail(form, url, { info } = {}) {
        if (!form) return;
        const box = form.querySelector('[data-thumbnail-preview]');
        const input = form.querySelector('[name="thumbnail"]');
        const error = form.querySelector('[data-thumbnail-error]');
        const stage = form.querySelector('[data-cover-stage-image]');
        const stageVideo = form.querySelector('[data-cover-stage-video]');
        const empty = form.querySelector('[data-cover-empty]');
        const infoNode = form.querySelector('[data-thumbnail-info]');
        const legacyImage = box?.querySelector('img');
        form.dataset.thumbnailUrl = url || '';
        delete form.dataset.thumbnailInvalid;
        delete form.dataset.thumbnailPending;
        if (error) { error.hidden = true; error.textContent = ''; }
        if (box) box.hidden = true;
        if (url) {
            const file = input?.files?.[0];
            if (infoNode) {
                infoNode.textContent = info
                    || (form._archiveCoverSource === 'gallery' && file ? file.name
                        : (form._archiveCoverSource === 'scrub'
                            ? formatCoverTime(form._archiveCover?.time || 0, form._archiveCover?.duration || 0)
                            : (file ? file.name : '현재 저장된 썸네일')));
            }
            if (form._archiveCoverSource === 'scrub' && stageVideo?.src) {
                if (stage) {
                    stage.hidden = true;
                    stage.removeAttribute('src');
                }
                stageVideo.hidden = false;
                if (empty) empty.hidden = true;
                delete form.dataset.thumbnailPending;
            } else {
                if (stageVideo) stageVideo.hidden = true;
                if (file) form.dataset.thumbnailPending = 'true';
                const onLoad = () => { delete form.dataset.thumbnailPending; };
                const onError = () => {
                    delete form.dataset.thumbnailPending;
                    if (input?.files?.[0]) form.dataset.thumbnailInvalid = 'true';
                    if (error) {
                        error.textContent = '커버 이미지를 읽지 못했습니다. 다른 사진을 선택하거나 커버를 초기화해 주세요.';
                        error.hidden = false;
                    }
                    setCoverUiState(form, 'is-error');
                };
                if (stage) {
                    stage.hidden = false;
                    stage.onload = onLoad;
                    stage.onerror = onError;
                    stage.src = url;
                }
                if (legacyImage) {
                    legacyImage.onload = onLoad;
                    legacyImage.onerror = onError;
                    legacyImage.src = url;
                }
                if (empty) empty.hidden = true;
            }
        } else {
            if (stage) {
                stage.removeAttribute('src');
                stage.hidden = true;
            }
            if (stageVideo) stageVideo.hidden = true;
            if (legacyImage) legacyImage.removeAttribute('src');
            if (empty) empty.hidden = false;
            if (infoNode) infoNode.textContent = '';
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
        form._archiveCoverSource = original ? 'saved' : '';
        const clear = form.querySelector('[name="clearThumbnail"]');
        if (clear) clear.value = 'false';
        showThumbnail(form, original, { info: original ? '현재 저장된 썸네일' : '' });
        if (original) setCoverUiState(form, 'is-success');
        else setCoverUiState(form, '');
    }

    function bindThumbnail(form) {
        const input = form?.querySelector('[name="thumbnail"]');
        if (!input) return;
        const clear = form.querySelector('[name="clearThumbnail"]');
        const slider = form.querySelector('[data-cover-slider]');
        form.querySelector('[data-cover-gallery]')?.addEventListener('click', () => {
            if (input.disabled) return;
            input.click();
        });
        if (slider && !slider.dataset.coverBound) {
            slider.dataset.coverBound = 'true';
            const scrub = form.querySelector('[data-cover-scrub]');
            const valueFromClientX = (clientX) => {
                const rect = scrub?.getBoundingClientRect();
                if (!rect || rect.width <= 0) return Number(slider.value) || 0;
                const ratio = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
                return Math.round(ratio * COVER_SLIDER_MAX);
            };
            const onScrub = () => {
                void scrubCoverTo(form, slider.value, { commit: false });
            };
            const onCommit = () => {
                void scrubCoverTo(form, slider.value, { commit: true });
            };
            slider.addEventListener('input', onScrub);
            slider.addEventListener('change', onCommit);
            slider.addEventListener('pointerup', onCommit);
            slider.addEventListener('touchend', onCommit);
            if (scrub && !scrub.dataset.coverDragBound) {
                scrub.dataset.coverDragBound = 'true';
                let dragging = false;
                const move = (event) => {
                    if (!dragging || slider.disabled) return;
                    slider.value = String(valueFromClientX(event.clientX));
                    onScrub();
                };
                const end = () => {
                    if (!dragging) return;
                    dragging = false;
                    window.removeEventListener('pointermove', move);
                    window.removeEventListener('pointerup', end);
                    onCommit();
                };
                scrub.addEventListener('pointerdown', (event) => {
                    if (slider.disabled || event.target === slider) return;
                    event.preventDefault();
                    dragging = true;
                    slider.value = String(valueFromClientX(event.clientX));
                    onScrub();
                    window.addEventListener('pointermove', move);
                    window.addEventListener('pointerup', end);
                });
            }
            window.addEventListener('resize', () => {
                if (form._archiveCover?.ready) updateCoverWindow(form, slider.value);
            });
        }
        input.addEventListener('change', () => {
            releaseUrl(input._archiveLastFile);
            input._archiveLastFile = null;
            const file = input.files?.[0];
            if (!file) {
                showThumbnail(form, clear?.value === 'true' ? '' : form.dataset.originalThumbnail || '');
                return;
            }
            if (!/\.(jpe?g|jfif|png|webp|gif|bmp)$/i.test(file.name)) {
                window.SiteFeedback.alert('커버는 JPG, JFIF, PNG, WebP, GIF, BMP 이미지 파일로 선택해 주세요.');
                input.value = '';
                syncFileName(input);
                showThumbnail(form, clear?.value === 'true' ? '' : form.dataset.originalThumbnail || '');
                return;
            }
            if (clear) clear.value = 'false';
            input._archiveLastFile = file;
            form._archiveCoverSource = 'gallery';
            showThumbnail(form, rememberUrl(file), { info: file.name });
            setCoverUiState(form, 'is-success');
        });
        form.querySelector('[data-thumbnail-clear]')?.addEventListener('click', () => {
            releaseUrl(input._archiveLastFile);
            input._archiveLastFile = null;
            input.value = '';
            syncFileName(input);
            form._archiveCoverSource = '';
            if (form._archiveCover?.ready) {
                if (clear) clear.value = 'false';
                if (slider) slider.value = '0';
                void scrubCoverTo(form, 0, { commit: true, auto: true });
                return;
            }
            if (clear) clear.value = 'true';
            showThumbnail(form, '');
            setCoverUiState(form, '');
        });
    }

    function validThumbnail(form) {
        if (form.dataset.thumbnailInvalid || form.dataset.thumbnailPending) {
            window.SiteFeedback.alert('커버 미리보기를 확인하거나 다른 이미지 파일을 선택해 주세요.');
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
        // 케미 모달이 위에 있으면 Escape는 케미만 닫고 아카이브 상세는 유지한다.
        const chemistry = document.getElementById("chemistryModal");
        if (chemistry && !chemistry.classList.contains("hidden")) return;
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
    bindImageEditTools(uploadForm);

    function warmArchiveMemberPickers() {
        window.MemberPortraitPicker?.preloadFrames?.();
        document.querySelectorAll(".ar-dialog .member-portrait-picker").forEach((picker) => {
            window.MemberPortraitPicker?.warmPicker?.(picker);
        });
    }
    const scheduleIdle = window.requestIdleCallback
        ? (cb) => window.requestIdleCallback(cb, { timeout: 1200 })
        : (cb) => setTimeout(cb, 180);
    scheduleIdle(warmArchiveMemberPickers);

    function defaultUploadArtists() {
        const fromPage = document.querySelector("[data-archive-kind]")?.dataset.defaultArtists;
        if (fromPage != null && fromPage.trim()) return fromPage.trim();
        const params = new URLSearchParams(window.location.search);
        return [...new Set(params.getAll("members").map(name => name.trim()).filter(Boolean))].join(",");
    }

    function setUploadStatus(message) {
        if (!uploadForm) return;
        let status = uploadForm.querySelector("[data-upload-status]");
        if (!status) {
            status = document.createElement("p");
            status.className = "ar-field-hint";
            status.dataset.uploadStatus = "";
            status.setAttribute("role", "status");
            status.style.color = "#8decd9";
            status.style.margin = "0";
            uploadForm.querySelector(".ar-dialog-foot")?.prepend(status);
        }
        status.textContent = message || "";
        status.hidden = !message;
    }

    function prepareUploadFormForNext() {
        if (!uploadForm) return;
        if (uploadFile) {
            releaseUnusedFiles(uploadFile._archiveLastFiles, []);
            uploadFile.value = "";
            uploadFile._archiveLastFile = null;
            uploadFile._archiveLastFiles = null;
            uploadFile._archivePreviewIndex = 0;
            setSlots(uploadForm, [], { syncInput: false });
            uploadForm._archiveSlotPreviewIndex = -1;
            syncFileName(uploadFile);
        }
        releaseCoverFrames(uploadForm);
        resetThumbnail(uploadForm);
        const titleInput = uploadForm.querySelector("[name='title']");
        if (titleInput) titleInput.value = "";
        const descriptionInput = uploadForm.querySelector("[name='description']");
        if (descriptionInput) descriptionInput.value = "";
        showPreview(uploadPreview, "");
        syncPreview(uploadForm);
        uploadForm.querySelectorAll('.ar-file input[type="file"]').forEach(syncFileName);
    }

    window.showAddModal = function () {
        if (!uploadForm || !uploadDialog) return;
        afterUploadLocation = null;
        resetUploadIdempotencyKey();
        uploadForm.reset();
        releaseCoverFrames(uploadForm);
        resetThumbnail(uploadForm);
        setUploadStatus("");
        if (uploadFile) {
            releaseUrl(uploadFile._archiveLastFile);
            (uploadFile._archiveLastFiles || []).forEach(releaseUrl);
            uploadFile._archiveLastFile = null;
            uploadFile._archiveLastFiles = null;
            uploadFile._archivePreviewIndex = 0;
        }
        setSlots(uploadForm, [], { syncInput: false });
        uploadForm._archiveSlotPreviewIndex = -1;
        showPreview(uploadPreview, "");
        // 모달을 먼저 연 뒤 멤버/날짜를 채워 첫 페인트가 막히지 않게 한다.
        openDialog(uploadDialog);
        requestAnimationFrame(() => {
            applyArtists(uploadDialog, defaultUploadArtists());
            window.bindArchiveDateFields?.(uploadForm);
            uploadForm.querySelectorAll('.ar-file input[type="file"]').forEach(syncFileName);
        });
    };

    uploadFile?.addEventListener("change", () => {
        resetUploadIdempotencyKey();
        bumpImageEditToken(uploadFile);
        const files = Array.from(uploadFile.files || []);
        releaseUnusedFiles(uploadFile._archiveLastFiles, files);
        uploadFile._archiveLastFile = files[0] || null;
        uploadFile._archiveLastFiles = files;
        if (!files.length) {
            setSlots(uploadForm, [], { syncInput: false });
            uploadForm._archiveSlotPreviewIndex = -1;
            showPreview(uploadPreview, "");
            syncFileName(uploadFile);
            if (kind === 'video') {
                releaseCoverFrames(uploadForm);
                resetThumbnail(uploadForm, '');
            }
            return;
        }
        const invalid = files.find(file => !acceptedFile(file));
        if (invalid) {
            window.SiteFeedback.alert(kind === "video" ? "동영상 파일만 선택할 수 있습니다." : "이미지 파일만 선택할 수 있습니다.");
            (uploadFile._archiveLastFiles || []).forEach(releaseUrl);
            uploadFile.value = "";
            uploadFile._archiveLastFile = null;
            uploadFile._archiveLastFiles = null;
            setSlots(uploadForm, [], { syncInput: false });
            syncFileName(uploadFile);
            showPreview(uploadPreview, "");
            if (kind === 'video') {
                releaseCoverFrames(uploadForm);
                resetThumbnail(uploadForm, '');
            }
            return;
        }
        const imageFiles = files.filter(isImageFile);
        if (kind === 'image') {
            setSlots(uploadForm, imageFiles.map(file => ({ kind: 'file', file })), { syncInput: false });
            const index = resolvePreviewIndex(uploadFile, imageFiles);
            imageFiles.forEach(rememberUrl);
            previewSlot(uploadForm, uploadFile, index);
            return;
        }
        const index = resolvePreviewIndex(uploadFile, files);
        uploadFile._archivePreviewIndex = index;
        showPreview(uploadPreview, rememberUrl(files[index]), { sourceFile: files[index], index });
        files.forEach((file, i) => { if (i !== index) rememberUrl(file); });
        syncFileName(uploadFile);
        void loadCoverFromVideo(uploadForm, files[index], { autoSelect: true });
    });

    uploadForm?.addEventListener("submit", async event => {
        event.preventDefault();
        const title = uploadForm.querySelector("[name='title']")?.value.trim();
        const files = Array.from(uploadFile?.files || []);
        if (kind === "video" && !title) {
            window.SiteFeedback.alert("제목을 입력해 주세요.");
            return;
        }
        if (!files.length) {
            window.SiteFeedback.alert(noun + " 파일을 선택해 주세요.");
            return;
        }
        if (!uploadForm.querySelector("[name='registeredOn']")?.value) {
            window.SiteFeedback.alert(dateLabel + "를 선택해 주세요.");
            return;
        }
        if (!uploadForm.querySelector("[name='source']")?.value) {
            window.SiteFeedback.alert("출처를 선택해 주세요.");
            return;
        }
        if (!uploadPreview || uploadPreview.hidden || uploadPreview.dataset.invalid || uploadPreview.dataset.pending) {
            window.SiteFeedback.alert("미리보기를 확인한 뒤 추가해 주세요.");
            return;
        }
        if (advancePhotoSetup(uploadForm, setUploadStatus)) return;
        const confirmMessage = kind === "image" && files.length > 1
            ? "사진 " + files.length + "장을 한 게시글로 추가하시겠습니까?"
            : "이 " + noun + "을 추가하시겠습니까?";
        if (!await window.SiteFeedback.confirm(confirmMessage)) return;
        if (kind === 'video') await ensureAutoCover(uploadForm);
        if (!validThumbnail(uploadForm)) return;
        const body = new FormData(uploadForm);
        if (kind === 'image') {
            body.set('coverIndex', String(Math.max(0, slotsOf(uploadForm).indexOf(uploadForm._archiveCoverSlot))));
        }
        body.delete("artistNames");
        body.delete("extraArtists");
        if (kind === 'image' && slotsOf(uploadForm).length > 1) {
            const names = [];
            artistRows(uploadForm).forEach(row => row.split(',').forEach(name => {
                const trimmed = name.trim();
                if (trimmed && !names.includes(trimmed)) names.push(trimmed);
            }));
            names.forEach(name => body.append("artistNames", name));
            body.set("imageArtistsJson", JSON.stringify(artistRows(uploadForm)));
        } else {
            selectedArtists(uploadDialog).forEach(name => body.append("artistNames", name));
        }
        const button = uploadForm.querySelector("[type='submit']");
        if (button) button.disabled = true;
        if (!uploadIdempotencyKey) uploadIdempotencyKey = newIdempotencyKey();
        const doneBusy = window.SiteFeedback.beginBusy();
        try {
            const headers = authHeaders();
            headers["Idempotency-Key"] = uploadIdempotencyKey;
            const response = await fetch(uploadForm.dataset.uploadUrl || "/archive/upload", {
                method: "POST",
                body,
                headers,
                credentials: "same-origin",
                // 프록시 뒤 절대 http Location 을 fetch 가 따라가면 Mixed Content 로 실패한다.
                redirect: "manual"
            });
            if (uploadSucceeded(response)) {
                resetUploadIdempotencyKey();
                afterUploadLocation = response.headers.get("Location") || "";
                prepareUploadFormForNext();
                setUploadStatus(noun + "을 추가했습니다. 이어서 더 추가할 수 있습니다.");
                softRefreshArchive(afterUploadLocation);
                return;
            }
            const message = (await response.text()).replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
            const detail = message.slice(0, 180) || "추가에 실패했습니다.";
            const failMsg = response.status === 409
                ? (detail || "같은 업로드가 아직 처리 중입니다. 잠시 후 다시 시도해 주세요.")
                : saveFailureMessage(response.status, detail);
            window.SiteFeedback.alert(failMsg);
            setUploadStatus(failMsg);
        } catch (_) {
            const networkMsg = "추가 요청에 실패했습니다. 네트워크를 확인한 뒤 같은 내용으로 다시 시도해 주세요. 입력값은 유지됩니다.";
            window.SiteFeedback.alert(networkMsg);
            setUploadStatus(networkMsg);
        } finally {
            doneBusy();
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
    bindImageEditTools(editForm);

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

    function setEditStatus(message) {
        if (!editForm) return;
        let status = editForm.querySelector("[data-edit-status]");
        if (!status) {
            status = document.createElement("p");
            status.className = "ar-field-hint";
            status.dataset.editStatus = "";
            status.setAttribute("role", "status");
            status.style.color = "#8decd9";
            status.style.margin = "0";
            editForm.querySelector(".ar-dialog-foot")?.prepend(status);
        }
        status.textContent = message || "";
        status.hidden = !message;
    }

    function populateEditForm(button) {
        if (!editForm || !button) return;
        editForm.reset();
        releaseCoverFrames(editForm);
        resetThumbnail(editForm, button.dataset.thumbnailUrl || '');
        editForm.dataset.id = button.dataset.id || "";
        const existingUrls = kind === 'image'
            ? parseImageUrls(button.dataset.images, button.dataset.url)
            : [];
        let perImage = [];
        try {
            const parsed = JSON.parse(button.dataset.imageArtists || '[]');
            if (Array.isArray(parsed)) perImage = parsed.map(row => String(row || ''));
        } catch (_) { perImage = []; }
        const coverUrl = button.dataset.url || existingUrls[0] || '';
        editForm.dataset.originalUrl = coverUrl;
        editForm.dataset.originalImages = JSON.stringify(existingUrls);
        const shared = (button.dataset.artists || '').split(',').map(name => name.trim()).filter(Boolean);
        setSlots(editForm, existingUrls.map((url, imageIndex) => ({
            kind: 'url',
            url,
            configured: true,
            artists: (perImage[imageIndex] || button.dataset.artists || '').split(',').map(name => name.trim()).filter(Boolean).length
                ? (perImage[imageIndex] || button.dataset.artists || '').split(',').map(name => name.trim()).filter(Boolean)
                : shared.slice()
        })), { syncInput: false });
        editForm._archiveCoverSlot = slotsOf(editForm).find(slot => slot.url === coverUrl) || slotsOf(editForm)[0];
        editForm._archiveSlotPreviewIndex = -1;
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
            editFile._archivePreviewIndex = existingUrls.length ? -1 : 0;
            editFile.value = "";
        }
        if (existingUrls.length) previewSlot(editForm, editFile, 0);
        else showPreview(editPreview, coverUrl);
        window.bindArchiveDateFields?.(editForm);
        editForm.querySelectorAll('.ar-file input[type="file"]').forEach(syncFileName);
        if (kind === 'video' && coverUrl) {
            void loadCoverFromVideo(editForm, coverUrl, { autoSelect: !button.dataset.thumbnailUrl });
        }
    }

    /**
     * soft-refresh 후 수정 폼에 넣을 소스.
     * 사진 카드는 그리드에 data-archive-edit가 없고 상세 버튼만 있어,
     * 상세 버튼의 낡은 data-images로 폼을 되돌리지 않도록 카드 메타를 우선한다.
     */
    function findRefreshedEditSource(id) {
        if (!id) return null;
        const key = CSS.escape(String(id));
        const card = document.querySelector('#archiveGrid .ar-card[data-id="' + key + '"]');
        if (card) {
            window.syncArchiveDetailCard?.(card);
            const gridEdit = card.querySelector('[data-archive-edit]');
            if (gridEdit) return gridEdit;
            const detailEdit = document.getElementById('archiveDetailEdit');
            if (detailEdit && String(detailEdit.dataset.id || '') === String(id)) return detailEdit;
            return card;
        }
        return document.querySelector('[data-archive-edit][data-id="' + key + '"]');
    }

    function showEdit(button) {
        if (!editForm || !editDialog) return;
        setEditStatus("");
        // 멤버 초상·프레임 디코드보다 모달 표시를 우선한다.
        openDialog(editDialog);
        requestAnimationFrame(() => populateEditForm(button));
    }

    editFile?.addEventListener("change", () => {
        bumpImageEditToken(editFile);
        const files = Array.from(editFile.files || []);
        const keptSlots = slotsOf(editForm).filter(slot => slot.kind === 'url');
        releaseUnusedFiles(editFile._archiveLastFiles, files);
        if (!files.length) {
            setSlots(editForm, keptSlots, { syncInput: false });
            editFile._archiveLastFile = null;
            editFile._archiveLastFiles = null;
            if (keptSlots.length) previewSlot(editForm, editFile, 0);
            else {
                editForm._archiveSlotPreviewIndex = -1;
                showPreview(editPreview, editForm.dataset.originalUrl || '');
                syncFileName(editFile);
            }
            if (kind === 'video' && editForm.dataset.originalUrl) {
                void loadCoverFromVideo(editForm, editForm.dataset.originalUrl, {
                    autoSelect: !editForm.dataset.originalThumbnail
                });
            }
            return;
        }
        const invalid = files.find(file => !acceptedFile(file));
        if (invalid) {
            window.SiteFeedback.alert(kind === "video" ? "동영상 파일만 선택할 수 있습니다." : "이미지 파일만 선택할 수 있습니다.");
            files.forEach(releaseUrl);
            setSlots(editForm, keptSlots, { syncInput: false });
            editFile.value = "";
            editFile._archiveLastFile = null;
            editFile._archiveLastFiles = null;
            if (keptSlots.length) previewSlot(editForm, editFile, 0);
            else {
                editForm._archiveSlotPreviewIndex = -1;
                syncFileName(editFile);
                showPreview(editPreview, editForm.dataset.originalUrl || '');
            }
            return;
        }
        if (kind === 'video') {
            const videoFile = files[0];
            editFile._archiveLastFile = videoFile;
            editFile._archiveLastFiles = [videoFile];
            showPreview(editPreview, rememberUrl(videoFile), { sourceFile: videoFile, index: 0 });
            syncFileName(editFile);
            void loadCoverFromVideo(editForm, videoFile, { autoSelect: true });
            return;
        }
        const nextFiles = files.filter(isImageFile);
        const nextSlots = keptSlots.concat(nextFiles.map(file => ({ kind: 'file', file })));
        setSlots(editForm, nextSlots, { syncInput: false });
        editFile._archiveLastFile = nextFiles[0] || null;
        editFile._archiveLastFiles = nextFiles.slice();
        const transfer = new DataTransfer();
        nextFiles.forEach(file => transfer.items.add(file));
        editFile.files = transfer.files;
        nextFiles.forEach(rememberUrl);
        previewSlot(editForm, editFile, keptSlots.length);
    });

    async function removeArchive(id, title) {
        if (!id) {
            window.SiteFeedback.alert("삭제할 자료를 찾지 못했습니다.");
            return;
        }
        const label = title ? "‘" + title + "’ " : "이 ";
        if (!await window.SiteFeedback.confirm(label + noun + "을 삭제하시겠습니까? 삭제하면 되돌릴 수 없습니다.")) return;
        const doneBusy = window.SiteFeedback.beginBusy();
        try {
            const response = await fetch("/archive/" + encodeURIComponent(id), {
                method: "DELETE", headers: authHeaders(), credentials: "same-origin"
            });
            if (response.ok) {
                removeCardFromGrid(id);
                document.dispatchEvent(new CustomEvent('archive:deleted', { detail: id }));
                softRefreshArchive();
                return;
            }
            window.SiteFeedback.alert(response.status === 403
                ? saveFailureMessage(403)
                : (response.status === 404 ? "이미 삭제된 자료입니다." : "삭제에 실패했습니다."));
        } catch (_) {
            window.SiteFeedback.alert("삭제 요청에 실패했습니다. 네트워크 연결을 확인해 주세요.");
        } finally {
            doneBusy();
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
            window.SiteFeedback.alert("제목을 입력해 주세요.");
            return;
        }
        const registeredOn = editForm.querySelector("[name='registeredOn']")?.value || "";
        if (!registeredOn) {
            window.SiteFeedback.alert(dateLabel + "를 선택해 주세요.");
            return;
        }
        if (!editForm.dataset.id) {
            window.SiteFeedback.alert("수정할 자료를 찾지 못했습니다.");
            return;
        }
        const slots = kind === 'image' ? slotsOf(editForm) : [];
        const replaceFiles = slots.filter(slot => slot.kind === 'file').map(slot => slot.file);
        const keptUrls = slots.filter(slot => slot.kind === 'url').map(slot => slot.url);
        const replacing = replaceFiles.length > 0;
        const originalUrls = parseImageUrls(editForm.dataset.originalImages, editForm.dataset.originalUrl);
        const orderChanged = kind === 'image' && JSON.stringify(slots.map(slot => slot.kind === 'url' ? slot.url : ''))
            !== JSON.stringify(originalUrls);
        const mediaChanged = kind === 'image' && (replacing || orderChanged || JSON.stringify(keptUrls) !== JSON.stringify(originalUrls));
        if (kind === 'image' && !slots.length) {
            window.SiteFeedback.alert('사진은 최소 1장이 필요합니다.');
            return;
        }
        if (advancePhotoSetup(editForm, setEditStatus)) return;
        if (replacing && (editPreview?.dataset.invalid || editPreview?.dataset.pending)) {
            window.SiteFeedback.alert('사진을 읽지 못했습니다. 다른 이미지 파일을 선택해 주세요.');
            return;
        }
        const message = kind === 'image' && mediaChanged
            ? (replacing
                ? (keptUrls.length
                    ? "기존 사진 " + keptUrls.length + "장을 유지하고 새 사진 " + replaceFiles.length + "장을 추가할까요?"
                    : "이 게시글 사진을 " + replaceFiles.length + "장으로 바꿀까요?")
                : "기존 사진 구성을 수정할까요?")
            : replacing
                ? "이 " + noun + "을 수정하시겠습니까? 선택한 파일로 기존 미디어가 바뀝니다."
                : "이 " + noun + "을 수정하시겠습니까?";
        if (!await window.SiteFeedback.confirm(message)) return;
        if (kind === 'video') await ensureAutoCover(editForm);
        if (!validThumbnail(editForm)) return;
        const body = new FormData();
        if (kind === "video") body.set("title", title);
        else body.set("description", description);
        body.set("registeredOn", registeredOn);
        const editRows = kind === 'image' ? artistRows(editForm) : [];
        const editNames = [];
        (editRows.length ? editRows : [selectedArtists(editDialog).join(',')]).forEach(row => {
            row.split(',').forEach(name => {
                const trimmed = name.trim();
                if (trimmed && !editNames.includes(trimmed)) editNames.push(trimmed);
            });
        });
        body.set("artistNames", editNames.join(","));
        if (kind === 'image' && slots.length > 1) body.set("imageArtistsJson", JSON.stringify(editRows));
        const source = editForm.querySelector("[name='source']")?.value || "";
        if (source) body.set("source", source);
        if (kind === 'image') {
            body.set('coverIndex', String(Math.max(0, slots.indexOf(editForm._archiveCoverSlot))));
            body.set('keepImageUrlsJson', JSON.stringify(keptUrls));
            body.set('imageOrderJson', JSON.stringify(slots.map(slot => slot.kind === 'url' ? slot.url : '')));
        }
        if (replacing) replaceFiles.forEach(file => body.append("file", file));
        if (kind === 'video' && editFile?.files?.[0]) body.set('file', editFile.files[0]);
        const thumbnail = editForm.querySelector('[name="thumbnail"]')?.files?.[0];
        if (thumbnail) body.set('thumbnail', thumbnail);
        const clearThumbnail = editForm.querySelector('[name="clearThumbnail"]');
        if (clearThumbnail) body.set('clearThumbnail', clearThumbnail.value);
        const button = editForm.querySelector("[type='submit']");
        if (button) button.disabled = true;
        const doneBusy = window.SiteFeedback.beginBusy();
        try {
            const response = await fetch("/archive/" + encodeURIComponent(editForm.dataset.id), {
                method: "POST", body, headers: authHeaders(), credentials: "same-origin"
            });
            if (response.ok) {
                const id = editForm.dataset.id;
                setEditStatus(noun + "을 저장했습니다. 모달을 닫지 않고 이어서 수정할 수 있습니다.");
                await softRefreshArchive();
                const refreshed = findRefreshedEditSource(id);
                if (refreshed) populateEditForm(refreshed);
                return;
            }
            const text = (await response.text()).replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
            const detail = text.slice(0, 180) || "수정에 실패했습니다.";
            const msg = saveFailureMessage(response.status, detail);
            window.SiteFeedback.alert(msg);
            setEditStatus(msg);
        } catch (_) {
            const networkMsg = "수정 요청에 실패했습니다. 네트워크를 확인한 뒤 다시 저장해 주세요. 입력값은 유지됩니다.";
            window.SiteFeedback.alert(networkMsg);
            setEditStatus(networkMsg);
        } finally {
            doneBusy();
            if (button) button.disabled = false;
        }
    });
})();
