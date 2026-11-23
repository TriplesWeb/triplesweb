'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const script = fs.readFileSync(path.join(root, 'src/main/resources/static/js/site-video-volume.js'), 'utf8');
const applied = [];
const player = {
    setVolume(volume) { applied.push(volume); },
    playVideo() {}
};
const iframe = { src: 'https://www.youtube.com/embed/example0001?rel=0&enablejsapi=1' };
const window = {
    YT: {
        Player: class {
            constructor(element, config) {
                assert.equal(element, iframe);
                config.events.onReady({ target: player });
            }
        }
    }
};
const document = { querySelectorAll: () => [iframe] };

vm.runInNewContext(script, { window, document, Promise });

window.SiteVideoVolume.attachYouTube(iframe).then((result) => {
    assert.equal(result, player);
    assert.deepEqual(applied, [30, 30], '각 신규 플레이어는 기본 음량이 30%여야 합니다.');
    player.setVolume(65);
    assert.equal(applied.at(-1), 65, '이용자가 바꾼 음량을 다시 강제하지 않아야 합니다.');
    console.log('기본 영상 음량 30% 테스트 통과');
}).catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
