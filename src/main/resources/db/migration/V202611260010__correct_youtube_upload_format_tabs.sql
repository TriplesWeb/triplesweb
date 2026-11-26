-- YouTube 쇼츠 및 일반 업로드 목록으로 확인한 영상의 탭만 보정한다.
-- 기존 영상만 갱신하며 제목, 재생목록과 멤버 연결은 보존한다.

UPDATE media SET tab = 'contents'
WHERE tab IN ('shorts')
  AND CAST(youtube_id AS BINARY) IN (
    'TVrdkrqpBV4', 'HJncmkjxOEs', 'n5LSPawWFpU', '9MmAfgGp5kY', '0JH-lL7KyzY', 'Zcu5LPo-ZgI',
    'YKt455LK6MQ', '5FrMMmE-Wn8', 'Np_o-6fRY08', 'nEakpf3FPd8', 'MdU7A0ZY0e0', 'P-QW19-Jrws',
    'HSH8TvkQiuw', 'WztXsK6KfSQ', 'g_emhorQYmA', 'GYBz4vbdcTM', 'wjpKW8G9Zys', 'u8ppSyqVifM',
    'YWSflj6Xxgo', 'IZULEdloGhA', 'KOkzosVA2no', '4JgI0pJIZHc', '6vsulOSEvRc', '7-E5arl3g30',
    '2cBCfPxN9Rw', 'q2Udw6yxec0', 'tuGbExoxJEk', 'rTMwtXXDwmE', 'sgofk7_3Auc', 'zm0RcfeLn5E',
    'HZ0rJeijQXM', '9aPXoc9FriM', 'YXQIFvY46tI', 'PJfVOFLyClQ', 'n0qzU8MLy6o', 'wEYqWLA9E1o',
    'oVPmdXXld0c', 'cw2MwQ4p9FA', '1h1RFsXRC6Y', 'ZzS7L59XGUg', 'sMCPltPmSQk', 'NgBRWk9wEtQ',
    '-YNkqDlBkMY', 'P4m4OwgHutk', 'tsuyF4z7Kns', 'YoyGBpcAVns', 'kPDuOqtnivk', 'MY_dOUdLwN4',
    '5AYgmG7YYEs', 'ijm_3BgxKdg', 'ug1RmN55SGg', 'r9wjxEmglHo', 'RTnl4TnW-Ts', 'B-Q7X5OzuLI',
    'aWt3YPZyKU8', 'F-Ev3Hg-VOQ', 'S33GV97KXGk', 'Tig5Lt8N12Y', 'bpVG7JttVVM', '_OTK4AZ0uHc',
    'wRqt5Y2qprc', 'FTaS1MWvjPY', '_Ea_Lf5X41s', 'C1xmnuSnSV0', 'pHxloKdCXfw', 'Fupxq-2hwV4',
    'aLutGaE2Puc', 'y8VttycEC4U', 'lNSMMWGFkPA', 'igERFBQzbko', 'L8DZ-qZZeoI', 'jpFdhUK9iW8',
    'BqtKrYKOPd4', 'FhgCueX2QSc', 'i6ZBVTYatSk', 'e2i1kBtTspg', 'zarkUnFOGF8', 'rLXTIuBFttU',
    'w1t7OeD1kzU', 'nm2Q2mrHlcg', '9N60--pXehw', 'UycNH4d2C4s', 'ko3Zp807lF8', 'mpCBp79Iu1Y',
    'oNIxDqSlaoE', 'LwKucPvCcIE', 'Fi4RuqUXlnU', '_dw8M7O74WY', 'aMZtdP66fAg', 'KkeVMHoD1n8',
    'bWuk4D6JXVs', 'NxQs8Ei9wrc', '8gaDqsia-0Q', 'e1RBCj6bvX4', 'aASqQDPFNmA', '6dUGLfqrrOc',
    'sI9kOcc-SSM', 'h7facbYOAB0', 'naTenZrxU6A', '11Ct4lmlPmY', 'aASproKOefY', 'AYQ2taQ6FBo',
    'ruX9y4w4Mdg', 'mBoo-ukfRgE', 'LbMtDb4y9yk', 'fSzKweJ9uGk', 'mhNXgve-npQ', 'UQNA6d37-KM',
    'tM0w3aXM_a0', 'XsJ2aB1fKWA', '6oMTpycseL4', 'ntllm2RqNzk', '4SuXuh4R_qo', 'dC7D9pKgcH4',
    'DhZG0CIVUNE', 'NXWlpwKB2og', 'R9mu79rbv3s', '3VjusY2bH5M', 'WfoXTczUF8Y', 'QKprzE7TAmo',
    '-DZMEiSFB4U', 'eFlmq53m8Ek', '9miEBwV_1vM', '9M4LAoION0g', 'TzgKvBxG-hY', 'y9YgUU-hD-A',
    'y4Tq5gU44zc', 'fPQcD4FhCec', 'AAf0b4MyrJY', '8SRzEOiaqxg', 'HJ421z2YnBc'
  );

UPDATE media SET tab = 'external'
WHERE tab IN ('shorts')
  AND CAST(youtube_id AS BINARY) IN (
    'i-E5y2VUFzA', 'AfrindYdVW8', 'psG-GgO-t9I', 'xhUWyLf5NbA', '57YgQheBv48', 'OE4-Rs20jrY',
    'xvPLsQtSkIQ', 'DAwHUAP0Ts8'
  );

UPDATE media SET tab = 'music'
WHERE tab IN ('shorts')
  AND CAST(youtube_id AS BINARY) IN (
    'k0zXTeK5rwU', 'mzwjFNTXTfI', 'hFdzTdz3eUI'
  );

UPDATE media SET tab = 'shorts'
WHERE tab IN ('contents', 'external', 'fancam')
  AND CAST(youtube_id AS BINARY) IN (
    'jHOFJxRXSU4', 'd_G8ZvsaCRk', 'nZp9FYoDdiY', '4KiVd59imUs', 'OOYVsxa-bzo', '7OyGq5m4L7g',
    'C4qmExbqYUY', 'die67leKAUA', '2XEyODJvWeI', 'S6j3mhLarRE', '1xWAbd0APq4', 'XjfKWMwenkY',
    'Xa6rGf6EVa0', '__gF8cMI74g', 'FT36rIxOoAM', 'do14FO4BZUE', '1sEke-BlKMs', '1IN5qn8ddx8',
    'qe61Ghh4GhE', 'wvTT252AU_Y', 't_v-D2LnzAU', 'KxF3hknf7hc', 'NGxvxGdRqDM', '9qfbnQZkm2U',
    'ZuJ4hhMR82w'
  );

UPDATE media SET tab = 'signal'
WHERE tab IN ('shorts')
  AND CAST(youtube_id AS BINARY) IN (
    'c2c8QDNaOa8', 'TB3Ad88WuBY', 'fMwQyfT963s', 'DUXdc7bxx0g', 'SU51KsLh9tQ'
  );
