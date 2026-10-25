(() => {
  /** 서버 TripleSMembers 와 동일한 멤버 메타. chemistry.js 등이 참조한다. */
  const NAMES_KO = [
    "윤서연", "정혜린", "이지우", "김채연", "김유연", "김수민", "김나경", "공유빈",
    "카에데", "서다현", "코토네", "곽연지", "니엔", "박소현", "신위", "마유",
    "린", "주빈", "정하연", "박시온", "김채원", "설린", "서아", "지연"
  ];
  const NAME_EN_BY_KO = {
    "윤서연": "SeoYeon", "정혜린": "HyeRin", "이지우": "JiWoo", "김채연": "ChaeYeon",
    "김유연": "YooYeon", "김수민": "SooMin", "김나경": "NaKyoung", "공유빈": "YuBin",
    "카에데": "Kaede", "서다현": "DaHyun", "코토네": "Kotone", "곽연지": "YeonJi",
    "니엔": "Nien", "박소현": "SoHyun", "신위": "XinYu", "마유": "Mayu",
    "린": "Lynn", "주빈": "JooBin", "정하연": "HaYeon", "박시온": "ShiOn",
    "김채원": "ChaeWon", "설린": "Sullin", "서아": "SeoAh", "지연": "JiYeon"
  };
  const COLORS = [
    "#22aeff", "#9200ff", "#fff800", "#98f21d", "#db0c74", "#fc83a4", "#6799a0", "#ffe3e2",
    "#ffc935", "#ff9ad6", "#ffde00", "#5974ff", "#ff953f", "#1222b5", "#d51313", "#fe8e76",
    "#ac62b7", "#b7f54c", "#52d9bb", "#ff428a", "#c7a3e0", "#7bba8d", "#cff3ff", "#ffab62"
  ];
  window.TripleSMembers = { NAMES_KO, NAME_EN_BY_KO, COLORS };
})();
