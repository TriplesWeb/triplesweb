(function () {
    'use strict';

    const DEFAULT_YOUTUBE_VOLUME = 30;
    let apiPromise = null;

    function loadYouTubeApi() {
        if (window.YT && typeof window.YT.Player === 'function') return Promise.resolve(window.YT);
        if (!apiPromise) {
            apiPromise = new Promise((resolve, reject) => {
                const previousCallback = window.onYouTubeIframeAPIReady;
                function onReady() {
                    resolve(window.YT);
                    if (typeof previousCallback === 'function') previousCallback();
                }
                window.onYouTubeIframeAPIReady = onReady;
                const script = document.createElement('script');
                script.src = 'https://www.youtube.com/iframe_api';
                script.async = true;
                script.onerror = () => {
                    apiPromise = null;
                    if (window.onYouTubeIframeAPIReady === onReady) {
                        window.onYouTubeIframeAPIReady = previousCallback;
                    }
                    script.remove();
                    reject(new Error('YouTube IFrame API를 불러오지 못했습니다.'));
                };
                document.head.appendChild(script);
            });
        }
        return apiPromise;
    }

    // The YouTube embed must use enablejsapi=1. Set the default once per
    // player; subsequent changes from the built-in volume control are preserved.
    function attachYouTube(iframe) {
        if (!iframe || !iframe.src) return Promise.reject(new Error('YouTube iframe이 없습니다.'));
        return loadYouTubeApi().then(() => new Promise((resolve, reject) => {
            if (iframe.isConnected === false) {
                reject(new Error('YouTube iframe이 화면에서 제거되었습니다.'));
                return;
            }
            new window.YT.Player(iframe, {
                events: {
                    onReady(event) {
                        event.target.setVolume(DEFAULT_YOUTUBE_VOLUME);
                        resolve(event.target);
                    }
                }
            });
        }));
    }

    window.SiteVideoVolume = { attachYouTube };

    document.querySelectorAll('iframe[data-site-youtube]').forEach((iframe) => {
        attachYouTube(iframe).catch(() => {
            // An embed remains usable via its own controls if the API is blocked.
        });
    });
})();
