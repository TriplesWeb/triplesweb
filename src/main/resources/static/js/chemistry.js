/**
 * tripleS 케미명 매트릭스
 * 데이터: chemistry-matrix.json + chemistryDescriptions
 */
(function () {
  const BASE = "/uploads/chemistry";

  const MEMBERS = [
    "윤서연","정혜린","이지우","김채연","김유연","김수민","김나경","공유빈",
    "카에데","서다현","코토네","곽연지","니엔","박소현","신위","마유",
    "린","주빈","정하연","박시온","김채원","설린","서아","지연"
  ];

  const MEMBER_COLORS = [
    "#22aeff","#9200ff","#fff800","#98f21d","#db0c74","#fc83a4","#6799a0","#ffe3e2",
    "#ffc935","#ff9ad6","#ffde00","#5974ff","#ff953f","#1222b5","#d51313","#fe8e76",
    "#ac62b7","#b7f54c","#52d9bb","#ff428a","#c7a3e0","#7bba8d","#cff3ff","#ffab62"
  ];

  const chemistryDescriptions = {
  "윤서연": {
    "윤서연과 정혜린의 케미명": {
      description: "가평하우스에 살던 시절 제일 먼저 공개된 둘이고 다른 S들이 공개되기 전 키가 작은 두 명이라 붙여진 귀여운 케미명이다.",
      image: "/uploads/chemistry/chem/S1/꼬꼬마즈.png"
    },
   "윤서연과 이지우의 케미명": {
      description: "둘은 트리플에스에서 유일한 퀸덤퍼즐(서바이벌) 출신이라 <br> 퀸덤즈로 팬들은 부르고 있었으나 <br> 어느 날 **(240902) 아돌라 MC**를 하면서 서로를 계란즈라고 지칭했다. <br>왜 계란즈인가 했더니 (240614) 시그널 라이브에서 <br> 서연이는 노란색 옷이고 지우는 하얀색 옷을 입어서 이날 계란즈라고 정했다고 한다. <br> (240614 라이브를 찾아보니 서로 케미명을 정하다가 정하지 못하고 <br> 두루뭉술하게 넘어갔지만 둘은 계란즈로 기억하는 듯하다)",
      image: "/uploads/chemistry/chem/S1/계란즈.png",
      links: {
       "(240902) 아돌라 MC":"https://www.youtube.com/live/bhzjyuSxuzg?si=J1DJhWpOdKTOXyAp"
     }
    },
   "윤서연과 김채연의 케미명": {
      description: "둘 다 강아지상 멤버이기 때문에 붙은 케미명. <br> 아기 강아지 울음소리로, 김채연 뭉뭉,<br> 윤서연이 망망하고 울게 생겼다는 의미.<br>photo by. @posh1204",
      image: "/uploads/chemistry/chem/S1/뭉망즈.png"
    },
     "윤서연과 김유연의 케미명": {
      description: "**220901 라이브** 에서 케미명 후보로 '뽀얀즈, 토끼즈'등등이 언급되다가 서연이가 자기도 토끼 하고 싶다고 해서 둘 다 피부가 뽀얗고 토끼니까 두 개를 합쳐 '뽀토즈'를 하자고 하였다.",
      image: "/uploads/chemistry/chem/S1/뽀토즈.png",
      links:{
       "220901 라이브":"https://youtu.be/cCAHv6vLW9Q?si=i2GoDuyXxxv5AuUV"
     }
    },
    "윤서연과 김나경의 케미명": {
      description: "뽀토즈가 생기면서 자연스럽게 고양이인 김나경과 케미명으로 뽀냥즈가되었다. <br> **241226 SIGNAL LIVE**에서 직접 언급하며 케미명이되었다.",
      image: "/uploads/chemistry/chem/S1/뽀냥즈.png",
      links:{
       "241226 SIGNAL LIVE":"https://youtu.be/Qt6lHRBcg3U?si=T5uidkpRSlfEVLbZ"
     }
    },
     "윤서연과 공유빈의 케미명": {
      description: "뽀토즈의 세 번째 파생 케미명으로 밤은 유빈이의 별명인 유밤이에서 따온 것이고 <br> 뽀는 '뽀'얗다가 될 수도 있고 '뽀'차코가 될 수도 있다.",
      image: "/uploads/chemistry/chem/S1/뽀밤즈.png"
    },
   "윤서연과 카에데의 케미명": {
      description: "물떡처럼 말랑말랑하고 동글둥글하게 생겼다 하여 팬들이 만들어준 케미명이다 .<br> photo by. @MapleSyrup_1220",
      image: "/uploads/chemistry/chem/S1/물떡즈.png"
    },
     "윤서연과 서다현의 케미명":{
      description:"햄햄다즈는 서연이의 별명인 '햄햄'과 다현의 '다'를 하겐다즈 아이스크림에 빗대 만들어진 귀여운 케미명이다.<br> 열일즈는 다현의 고유 번호인 S10을 한글식으로 부르면 '열'이 되고 <br> 서연의 고유 번호인 S1을 한글식으로 부르면 '일'이 된 <br> 그 둘을 합쳐 열일즈라 부르며 '열심히 일을 한다'의 준말이기도 하다. <br> 둘은 열심히 일을 하므로 서연이가 굉장히 맘에 들어 했고<br> **250909 시그널 라이브**에서 햄햄다즈와 열일즈 다 사용하자고 정했다. <br> 하지만 팬들은 햄햄다즈를 더 많이 사용할 것으로 보이긴 한다.",
      image:"/uploads/chemistry/chem/S1/햄햄다즈.png",
      links:{
       "250909 시그널 라이브":"https://youtu.be/Fx6P06K9zwk?si=n4M358B0RTkTQm29"
      }
    },

     "윤서연과 코토네의 케미명": {
      description: "공개 숫자가 1로만 구성된 S1과 S11이기에 1이 빼빼로를 닮아 빼빼로즈로 정하게 되었다.",
      image: "/uploads/chemistry/chem/S1/빼빼로즈.png"
    },
     "윤서연과 니엔의 케미명": {
      description: "250330 서연의 fromm에서 정해짐.<br>윤서연이 닮은 동물 햄스터의 햄과 니엔의 닮은 동물 강아지(멍멍이)를 합쳐 만든 케미명이다, 하지만 해외의 웨이브들은 햄멍즈 보다 Sunnyz를 더  많이 사용한다",
      image: "/uploads/chemistry/chem/S1/햄멍즈.png"
    },
    "윤서연과 박소현의 케미명": {
      description: "서로가 서로의 최애라서 생긴 케미명.<br> 웨이브들이 둘이 친해진 계기를 묻는 질문에도 윤서연은 서로가 최애이기 때문에 금방 친해졌다고 말하였다.",
      image: "/uploads/chemistry/chem/S1/최애즈.png"
    },
    "윤서연과 신위의 케미명": {
      description: "윤서연과 저우신위의 초성이'ㅇㅅㅇ'로 같다는 점에서 230913 SIGNAL LIVE에서 케미명을 정함.",
      image: "/uploads/chemistry/chem/S1/ㅇㅅㅇ즈.png"
    },
     "윤서연과 박시온의 케미명": {
      description: "서연과 시온은 둘 다 대전출신이고빵을 좋아해서 지어진 케미명으로 '빵을 냠냠먹다'를 줄여만든 케미명이다.",
      image: "/uploads/chemistry/chem/S1/빵냠즈.png"
    },
   "윤서연과 김채원의 케미명": {
      description: "**250210 SIGNAL**에서 만든 케미명이자 유닛명.천연기념물 1호 리유저블 비닐 팩(윤서연)과 갈리다만 선녀(김채원)로 구성된 2인조 유튜버(?)라고 한다.",
      image: "/uploads/chemistry/chem/S1/자몽샤베트.png",
      links:{
       "250210 SIGNAL":"https://youtu.be/-68vLGYzCbE?si=hpNCdOo7MuuceRQo"
      }
    },
    "윤서연과 지연의 케미명":{
      description: "트리플에스에서 둘은 S1과 S24이면서 둘의 본명은 윤서연, 지서연으로 같아 둘의 케미명은 서연즈가 되었다.",
      image: "/uploads/chemistry/chem/S1/서연즈.png"
      }
 },
  "정혜린":{
  "정혜린과 윤서연의 케미명":{
    description: "가평하우스에 살던 시절 제일 먼저 공개된 둘이고 다른 S들이 공개되기 전 키가 작은 두 명이라 붙여진 귀여운 케미명이다.",
     image: "/uploads/chemistry/chem/S1/꼬꼬마즈.png"
     },
   "정혜린과 김유연의 케미명":{
    description: "220726 시그널에서 혜린과 유연은 서로 일본어 수업을 받으며 유연이 '수고 많았다'를 '스고이'로 착각하며 그 뒤로 모든 대답을 스고이로 대답하며 둘이 수업받은 날을 기점으로 둘이 스고이즈가 되었다.<br>250929 COSMO 보이스 온리 라이브에서 혜린이가 스고이즈의 존재를 모르는듯해서 새로 케미명을 지을 예정인 듯 하다 현재 유력 후보는 (25즈와 심장이 유혜)다.<br> photo by. @triplesbburing",
     image: "/uploads/chemistry/chem/S2/스고이즈.png"
     },
  "정혜린과 김수민의 케미명":{
   description: "서로 같은 지역인 대구출생으로 대구즈라 부르게 되었다",
   image: "/uploads/chemistry/chem/S2/대구즈.png"
     },
  "정혜린과 공유빈의 케미명":{
    description: "남산 HAUS 시절 룸메이트였으며, 이때 둘 다 눈이 크다며 '왕눈즈'라는 케미명을 만들었다",
     image: "/uploads/chemistry/chem/S2/왕눈즈.png"
     },
   "정혜린과 카에데의 케미명":{
     description:"카에데의 한국 뜻인 단풍의 '단'과 혜린의 별명인 리네의 '네'를 내로 바꾸며 한국식 사투리인 단내 단다는 '달달한 냄새가 난다'라는 뜻을 담고 있는 유머형 케미명이다 250811 시그널에서 혜린이가 직접 정한 케미명이다",
     image:"/uploads/chemistry/chem/S2/단내즈.png"
    },
   "정혜린과 서다현의 케미명":{
    description: "동네 놀이터에서 어떤 어린아이가 둘을 이모들이라 불렀다는 썰을 230405 SIGNAL LIVE에서 풀었는데, 이때 만들어지게 됐다.",
     image: "/uploads/chemistry/chem/S2/이모즈.png"
     },
  "정혜린과 코토네의 케미명":{
    description: "250317 공식 SNS에 혜린이가 토네가 찍은 셀카를 올리며 사용. 혜린의 별명 리네와 코토네에서 따온 케미명으로 추측된다. ",
     image: "/uploads/chemistry/chem/S2/네네즈.png"
     },
  "정혜린과 박소현의 케미명":{
    description: "<b>소</b>현과 <b>리</b>네를 합쳐 만든 케미명으로, 231117 박소현의 fromm에서 만들었다.",
     image: "/uploads/chemistry/chem/S2/소리꾼즈.png"
     },
  "정혜린과 신위의 케미명":{
    description:"250210 SIGNAL에서 자몽 샤베트가 만들어준 케미명. 둘이 쇼핑을 종종 같이가고, 취향도 잘 맞는다고 밝힘.",
     image:"/uploads/chemistry/chem/S2/청담동 사모님즈.png"
  },
"정혜린과 린의 케미명":{
    description:"260412 혜린과 린의 생일 시그널에서 정했으며<br>상어를 일본어로 뜻하는 샤메의 '샤'와 고양이를 귀엽게 부르는 냥냥이의 '냥'이 합쳐져서 만들어진 케미명이다<br> 웨이브를 사냥하겠다는 날카로운 느낌이 들어서 좋다고한다. (임시 케미명)",
     image:"/uploads/chemistry/chem/S2/사냥즈.png"
  },
  "정혜린과 정하연의 케미명":{
    description: "서아의 공개 전까지 팀 내에 단 둘뿐인 정씨 멤버들이었기 때문에 지었던 케미명. ",
     image: "/uploads/chemistry/chem/S2/정자매즈.png"
     }
},
 "이지우": {
    "이지우와 윤서연의 케미명": {
      description: "둘은 트리플에스에서 유일한 퀸덤퍼즐(서바이벌) 출신이라 <br> 퀸덤즈로 팬들은 부르고 있었으나 <br> 어느 날 **(240902) 아돌라 MC**를 하면서 서로를 계란즈라고 지칭했다. <br>왜 계란즈인가 했더니 (240614) 시그널 라이브에서 <br> 서연이는 노란색 옷이고 지우는 하얀색 옷을 입어서 이날 계란즈라고 정했다고 한다. <br> (240614 라이브를 찾아보니 서로 케미명을 정하다가 정하지 못하고 <br> 두루뭉술하게 넘어갔지만 둘은 계란즈로 기억하는 듯하다)",
      image: "/uploads/chemistry/chem/S1/계란즈.png",
      links: {
       "(240902) 아돌라 MC":"https://www.youtube.com/live/bhzjyuSxuzg?si=J1DJhWpOdKTOXyAp"
     }
    },
   "이지우와 김채연의 케미명": {
      description: "1st 가평 HAUS에서 민트방을 같이 쓰던 룸메이트였던 계기로 친해졌으며, 이후 민트방즈로도 불렸었다.",
      image: "/uploads/chemistry/chem/S3/민트즈.png"
    },
   "이지우와 김유연의 케미명": {
      description: "<b>방과후 설렘</b> 출연으로 인해 그룹 합류 전부터 이미 서로 알던 사이로, 해당 프로그램 **세미파이널 무대**에서 경쟁자로 만나 레옹을 같이 부름",
      image: "/uploads/chemistry/chem/S3/방설즈.png",
      links:{
       "세미파이널 무대":"https://youtu.be/cndo60XXVvo?si=o_1gm-q7WRPRQvfX"
       }
    },
   "이지우와 김수민의 케미명": {
      description: "김수민의 말실수였던 '데니스트'와 이지우가 뉴욕에서 못 샀던 '데스노트' 목걸이를 합쳐 '데니즈'로 정하였다.",
      image: "/uploads/chemistry/chem/S3/데니즈.png"
    },
   "이지우와 공유빈의 케미명": {
      description: "모드하우스 입사 전 JYP에서 함께 배우 연습생을 하였으며, **어느 SIGNAL**에서 이지우 '주변에 새로운 S가 될만한 친구 있나요?'라는 질문에 '있다'고 답했는데, 이때 떠올린 사람이 공유빈이었다고 나중에 밝혔다. 트리플에스 내에서 가장 유명한 케미 중 하나다.",
      image: "/uploads/chemistry/chem/S3/찐친즈.png",
      links:{ "어느 SIGNAL": "https://youtu.be/gWMq9RJC4ZA?si=VcU1opY6yPSZMnCt&t=488"
      }
    },
 "이지우와 코토네의 케미명":{
   description:"260322코토네 프롬을 통해 쥬(지우)랑 또네(토네)라는 뜻을 가지고 있고<br> 일본어로 약간 계속 ずっと (즛토)와 비슷해서 정하게 되었다고 했다.",
   image:"/uploads/chemistry/chem/S3/쥬또즈.png"
},
 "이지우와 곽연지의 케미명":{
    description:"둘의 상징색을 섞으면 연두색임 둘 다 연두 같은 풋풋함이 있고 <br> 12(연지)3(지우) 123 번호 '연'결되는 '두'명이라는 뜻을 담고 있다. <br>(오피셜 케미명은 아닌 연지 팬계정의 투표를 통해서 정해진 케미명이다.)",
    image:"/uploads/chemistry/chem/S3/연두즈.png"
  },
  "이지우와 마유의 케미명": {
      description: "지<b>우</b>와 마<b>유</b>의 이름을 합쳐 만든 케미명.",
      image: "/uploads/chemistry/chem/S3/우유즈.png"
    },
  "이지우와 지연의 케미명":{
    description:"250511 지연 fromm 에서 얘기하다가 비밀이라고 지크릿을 얘기하고 한 웨이브가 치링치링치리링 지크릿 쥬쥬를 언급.\n 맘에 들게 되어 지우에게 얘기 후 지크릿쥬쥬즈 케미명이 됨.",
    image:"/uploads/chemistry/chem/S3/지크릿쥬쥬즈.png"
   }
  },
 "김채연": {
   "김채연과 윤서연의 케미명": {
       description: "둘 다 강아지상 멤버이기 때문에 붙은 케미명. <br> 아기 강아지 울음소리로, 김채연 뭉뭉,<br> 윤서연이 망망하고 울게 생겼다는 의미.<br>photo by. @posh1204",
      image: "/uploads/chemistry/chem/S1/뭉망즈.png"
    },
   "김채연과 이지우의 케미명": {
      description: "1st 가평 HAUS에서 민트방을 같이 쓰던 룸메이트였던 계기로 친해졌으며, 이후 민트방즈로도 불렸었다.",
      image: "/uploads/chemistry/chem/S3/민트즈.png"
    },
    "김채연과 김유연의 케미명": {
      description: "둘 다 복숭아 상이라고 하여 붙여진 케미이다. 김채연을 상징하는 이모지가 복숭아이기도 합니다.<br> [25년 기준 채연이가 복숭아를 싫어하게 되며 사슴 이모지로 변경되었다.]",
      image: "/uploads/chemistry/chem/S4/피치즈.png"
    },
   "김채연과 김나경의 케미명": {
      description: "김채연이 마이멜로디 닮고, 김나경이 쿠로미 닮았다하여 둘의 케미명이 마멜즈가 되었다.",
      image: "/uploads/chemistry/chem/S4/마멜즈.png"
    },
  "김채연과 공유빈의 케미명": {
      description: "드림센터 스터디룸의 MC를 맡아서 생긴 케미명.",
      image: "/uploads/chemistry/chem/S4/MC즈.png"
    },
    "김채연과 카에데의 케미명": {
      description: "과거 김채연과 카에데가 디즈니 공주 팬싸인회때 김채연이 엘사, 카에데는 안나가 되어 oo즈는 식상하니 프로즌으로 하게 됐다고 한다.<br> photo by. annyeongsubsubs",
      image: "/uploads/chemistry/chem/S4/프로즌.png"
    },
    "김채연과 코토네의 케미명": {
      description: "과거 채연이 진행했던 보니하니에 <b>코</b>토네의 이름을 합친 것으로, 코토네가 fromm으로 후보를 받은 후 채연에게 물어봐 고도록 했다고 한다.",
      image: "/uploads/chemistry/chem/S4/코니하니.png"
    },
    "김채연과 니엔의 케미명": {
      description: "김채연의 '연'과 니엔의 '엔'을 합쳐서 만든 케미명이자, 연예인이라는 중의적 의미를 담고 있는 케미명이다.",
      image: "/uploads/chemistry/chem/S4/연엔즈.png"
    },
    "김채연과 마유의 케미명": {
      description: "채<b>연</b>과 마<b>유</b>를 합쳐 만든 케미명",
      image: "/uploads/chemistry/chem/S4/연유즈.png"
    },
    "김채연과 주빈의 케미명": {
      description: "251224 코스모 라이브에서 서로는 덮은 머리보다 이마가 보여야 이쁘다며 이마즈로 부르기 시작했다.<br> 원래는 241031 SIGNAL LIVE에서 서로 어색하고 비즈니스 사이여서 비스니즈로 사용하였었지만 1년 사이 많이 친해지고 새롭게 케미명을 만든 듯하다.",
      image: "/uploads/chemistry/chem/S4/비스니즈.png"
    },
  "김채연과 정하연의 케미명": {
      description: "Girls Never Die 활동 당시, 더쇼 대기실에서 하연이 채연에게 뽀뽀하는 시늉을 하였는데  채연이 피하지 않아 의도치 않게 뽀뽀를 하게 된 적이 있음.\n 240716 SIGNAL LIVE에서 하연이 당시의 일을 회상하며, 눈을 감고 뽀뽀하는 시늉을 했는데 촉촉한 느낌이 들어 놀랐다고 밝혀 촉촉즈라는 케미명을 제안.",
      image: "/uploads/chemistry/chem/S4/촉촉즈.png"
    },
  "김채연과 김채원의 케미명": {
      description: "원래 케미는 둘 다 김채가 들어간다 하여 '김채즈'였지만 둘이 말차 메이트라고 하며 말차즈로 둘이서 새롭게 변경하였다. **250621 시그널**에서 언급된다.",
      image: "/uploads/chemistry/chem/S4/김채즈.png",
      links:{
       "250621 시그널":"https://youtu.be/64im_Cc0Gtk?si=RNOPLPdySrqMU9FV"
      }
    },
  "김채연과 설린의 케미명": {
      description: "240814 라이브에서 채연의 '<b>연</b>'과 설린의 태국 본명인 피라다 분락사의 '<b>락</b>'이 합쳐져 만들어진 케미명이다",
      image: "/uploads/chemistry/chem/S4/연락즈.png"
    }
},
"김유연":{
  "김유연과 윤서연의 케미명": {
      description: "**220901 라이브** 에서 케미명 후보로 '뽀얀즈, 토끼즈'등등이 언급되다가 서연이가 자기도 토끼 하고 싶다고 해서 둘 다 피부가 뽀얗고 토끼니까 두 개를 합쳐 '뽀토즈'를 하자고 하였다.",
      image: "/uploads/chemistry/chem/S1/뽀토즈.png",
      links:{
       "220901 라이브":"https://youtu.be/cCAHv6vLW9Q?si=i2GoDuyXxxv5AuUV"
     }
    },
  "김유연과 정혜린의 케미명": {
      description: "220726 시그널에서 혜린과 유연은 서로 일본어 수업을 받으며 유연이 '수고 많았다'를 '스고이'로 착각하며 그 뒤로 모든 대답을 스고이로 대답하며 둘이 수업받은 날을 기점으로 둘이 스고이즈가 되었다.<br>250929 COSMO 보이스 온리 라이브에서 혜린이가 스고이즈의 존재를 모르는듯해서 새로 케미명을 지을 예정인 듯 하다 현재 유력 후보는 (25즈와 심장이 유혜)다.<br> photo by. @triplesbburing",
     image: "/uploads/chemistry/chem/S2/스고이즈.png"
    },
  "김유연과 이지우의 케미명": {
      description: "<b>방과후 설렘</b> 출연으로 인해 그룹 합류 전부터 이미 서로 알던 사이로, 해당 프로그램 **세미파이널 무대**에서 경쟁자로 만나 레옹을 같이 부름",
      image: "/uploads/chemistry/chem/S3/방설즈.png",
      links:{
       "세미파이널 무대":"https://youtu.be/cndo60XXVvo?si=o_1gm-q7WRPRQvfX"
       }
    },
  "김유연과 김채연의 케미명": {
     description: "둘 다 복숭아 상이라고 하여 붙여진 케미이다. 김채연을 상징하는 이모지가 복숭아이기도 합니다.<br> [25년 기준 채연이가 복숭아를 싫어하게 되며 사슴 이모지로 변경되었다.]",
      image: "/uploads/chemistry/chem/S4/피치즈.png"
    },
  "김유연과 김수민의 케미명": {
      description: "둘 다 딸기라떼를 좋아하여 붙여진 케미명. 둘이 쌍방으로 좋아하다는 일화가 숨겨져있다.",
      image: "/uploads/chemistry/chem/S5/딸기즈.png"
    },
  "김유연과 김나경의 케미명": {
      description: "<b>2월생</b> 김유연과 <b>10월생</b> 김나경의 조합, <br> 2024년 10월 둘이 함께 IDOL RADIO의 스페셜 DJ로 활동할 때도 투텐즈라는 케미명을 사용하였다. 트리플에스 내에서 가장 유명한 케미 중 하나다.",
      image: "/uploads/chemistry/chem/S5/투텐즈.png"
    },
   "김유연과 공유빈의 케미명": {
      description: "<b>유</b>연, <b>유</b>빈으로 두명다 이름에 '유'가 들어가 생겨난 조합명.",
      image: "/uploads/chemistry/chem/S5/두유즈.png"
    },
   "김유연과 카에데의 케미명": {
      description: "Hit the Floor 안무에는 2절 후렴 직후에 김유연과 카에데가 모두 무대의 왼쪽에 빠져 있는 구간이 존재하는데,<br> 팬사인회에서 해당 무대를 할 때에 이 구간에서 두 멤버가 늘 포옹을 하여 둘이 '나안아~' 하면서 케미명이 만들어졌다.<br> 이후 김유연이 fromm을 통해 해당 케미명을 공식화하였다. <br>Photo by @Sharon_0209",
      image: "/uploads/chemistry/chem/S5/나안아즈.png"
    },
  "김유연과 코토네의 케미명": {
      description: "케미명 정하기를 미루다 보니 250418 코토네 Fromm에서 팬들이 미룬즈를 추천했다.<br> 김유연에게 확인받은 결과 좋다고 하여 공식화됐다.<br> photo by. @yume040310",
      image: "/uploads/chemistry/chem/S5/미룬즈.png"
    },
   "김유연과 박소현의 케미명": {
      description: "첫인상으로 박소현이 냉하게 생기고 김유연이 온하게 생겨서 냉온즈로 케미명이 지어짐.",
      image: "/uploads/chemistry/chem/S5/냉온즈.png"
    },
   "김유연과 신위의 케미명": {
      description: "Girls Never Die 뮤비 촬영 비하인드 영상에서 서로 같은 옷을 입고 비슷한 헤어스타일을 하니<br> 쌍둥이 같다는 이야기를 하였고, 자막으로 쌍둥이즈라는 케미명이 나왔다.",
      image: "/uploads/chemistry/chem/S5/쌍둥이즈.png"
    },
   "김유연과 마유의 케미명": {
      description: "유연과 마유는 트리플에스에서 대표 토끼상으로 유연이는 큰 토끼 마유는 작은 토끼를 담당하고 있어서 토끼즈라 부른다 일부 팬들은 마유연을 사용하기도 한다.",
      image: "/uploads/chemistry/chem/S5/토끼즈.png"
    },
   "김유연과 서아의 케미명": {
      description: "트리플에스의 첫째(유연)와 막내(서아)로 맏막즈가 되었다. <br>(맏막즈는 막내 포지션의 멤버는 연지 -> 주빈-> 서아 총 세 번 바뀌었다.) <br> Photo by. @s23_Seah",
      image: "/uploads/chemistry/chem/S5/맏막즈.png"
    }
},
  "김수민": {
   "김수민과 정혜린의 케미명":{
   description: "서로 같은 지역인 대구출생으로 대구즈라 부르게 되었다",
   image: "/uploads/chemistry/chem/S2/대구즈.png"
     },
    "김수민과 이지우의 케미명": {
      description: "김수민의 말실수였던 '데니스트'와 이지우가 뉴욕에서 못 샀던 '데스노트' 목걸이를 합쳐 '데니즈'로 정하였다.",
      image: "/uploads/chemistry/chem/S3/데니즈.png"
    },
   "김수민과 김유연의 케미명": {
      description: "둘 다 딸기라떼를 좋아하여 붙여진 케미명. 둘이 쌍방으로 좋아하다는 일화가 숨겨져있다. photo by @soomsoomsoom103",
     image: "/uploads/chemistry/chem/S6/딸기즈.png"
    },
    "김수민과 공유빈의 케미명": {
      description: "웨이브들이 정한 케미로 둘 다 말랑말랑하게 생겨서 붙게 됐다.",
      image: "/uploads/chemistry/chem/S6/말랑즈.png"
    },
    "김수민과 서다현의 케미명": {
      description: "김수민의 '수'와 서다현의 '다'를 이용한 케미명. 둘이 말이 많다하여 SIGNAL LIVE에서 정해지게 됐다.<br> photo by @soomsoomsoom103",
      image: "/uploads/chemistry/chem/S6/수다즈.png"
    },
    "김수민과 코토네의 케미명": {
       description:"251129 하찌 행사에서 WAV가 수민이에게 둘의 케미명을 물었고 수민이는 타코야끼즈라 대답했다. <br> 둘다 얼굴이 동글동글해서다 서로 담당하고 있는 부분이 있냐 물었더니 토네가 타코야끼고 수민이는 즈라한다.<br> photo by. Kotoworld0310",
       image:"/uploads/chemistry/chem/S6/타코야끼즈.png"
    },
    "김수민과 카에데의 케미명":{
   description:"성수하우스 시절 둘이 라이브를 하다가 어떤 케미명을 하고 싶냐는 WAV들의 질문에 수민이가 라이브 전에 <br> 감자탕 먹고 왔으니까 감자탕즈로 하겠다며 감자탕즈가 되었다.<br>(221129 시그널 라이브 참고)",
   image:"/uploads/chemistry/chem/S6/감자탕즈.png"
   },
    "김수민과 곽연지의 케미명": {
      description: "둘이 뭉치면 레전드라 레전드로 케미명 짓는다고 수민이 프롬에서 언급.",
      image: "/uploads/chemistry/chem/S6/레전드.png"
    },
   "김수민과 주빈의 케미명":{
     description:" 240807 주빈이 fromm에서 수민이와 케미명이 있냐는 질문에 잭과콩나뭉즈라고 언급했으며 수민이가 '잭'이고 주빈이가 '콩나뭉'이라고 한다 왜 '뭉'인지는 모르겠다고  수민이가 직접 정했으나 1년 후 수민이가 까먹고 주빈이만 기억하는 짝사랑 케미명이다(?) 250913 주빈 fromm에서 확인할 수 있다.<br> Photo by. DoNot_S6",
     image:"/uploads/chemistry/chem/S6/잭과콩나뭉즈.png"
    },
   "김수민과 김채원의 케미명":{
    description:"채원이의 별명인 외계인을 줄여 부르는 '왹'과 수민이를 줄여 부르는 '숨'이 붙어져 만들어진 케미명이다. <br>처음에는 팬들끼리 부르는 케미명이였으나 지금은 많은 팬들이 사용하며 굳어지고 공식이나 다름없게 사용하는 케미명이다.",
    image:"/uploads/chemistry/chem/S6/왹숨즈.png"
   }
  }, 
 "김나경":{
   "김나경과 윤서연의 케미명": {
     description: "뽀토즈가 생기면서 자연스럽게 고양이인 김나경과 케미명으로 뽀냥즈가되었다. <br> **241226 SIGNAL LIVE**에서 직접 언급하며 케미명이되었다.",
      image: "/uploads/chemistry/chem/S1/뽀냥즈.png",
      links:{
       "241226 SIGNAL LIVE":"https://youtu.be/Qt6lHRBcg3U?si=T5uidkpRSlfEVLbZ"
     }
  },
 "김나경과 김채연의 케미명": {
     description: "김채연이 마이멜로디 닮고, 김나경이 쿠로미 닮았다하여 둘의 케미명이 마멜즈가 되었다.",
     image: "/uploads/chemistry/chem/S4/마멜즈.png"
  },
  "김나경과 김유연의 케미명": {
     description: "<b>2월생</b> 김유연과 <b>10월생</b> 김나경의 조합, <br> 2024년 10월 둘이 함께 IDOL RADIO의 스페셜 DJ로 활동할 때도 투텐즈라는 케미명을 사용하였다. 트리플에스 내에서 가장 유명한 케미 중 하나다.",
      image: "/uploads/chemistry/chem/S5/투텐즈.png"
  },
  "김나경과 공유빈의 케미명": {
     description: "230316 시그널에서 비교적 인원수가 적은 여의도 HAUS에 둘이 배정되는 것을 보고<br> 김유연이 둘을 격리 시켜놓는 게 아니냐는 드립을 쳤고, <br>그 이후로 당사자인 둘만 빼고 모든 멤버들과 팬들이 둘을 격리즈로 부르게되었다.",
     image: "/uploads/chemistry/chem/S7/냥냥즈(격리즈).png"
  },
 "김나경과 서다현의 케미명": {
    description: "김나경의 '<b>나</b>'와 서다현의 '<b>다</b>'를 이용한 케미명. <br> 둘은 롯데 자이언츠 시구와 시타를 두 번이나 눈앞에서 실패한 경험이 있다. (우천 취소, 폭염 취소), <br>그러나 250816 드디어 시구와 시타를 해냈다. <br> **250816 롯데vs삼성**",
     image: "/uploads/chemistry/chem/S7/나다즈.png",
     links:{
      "250816 롯데vs삼성":"https://naver.me/FYqrH6ZP"}
  },
 "김나경과 코토네의 케미명": {
     description: "<b>나(낫)</b>경과 코<b>토</b>네를 합쳐 만든 케미명, 코토네가 fromm을 통해 밝혔다. ",
     image: "/uploads/chemistry/chem/S7/낫토즈.png"
  },
 "김나경과 니엔의 케미명": {
     description: "밤양갱+고냥이의 '냥'+갱얼쥐의'갱',<br> 2024년 9월 둘이 함께 IDOL RADIO의 스페셜 DJ로 활동할 때에도 밤냥갱즈라는 케미명을 사용. ",
     image: "/uploads/chemistry/chem/S7/밤냥갱즈.png"
  },
  "김나경과 박소현의 케미명": {
     description: "태어난 년도,월,일까지 모두 똑같은 멤버이다.<br> 241226 SIGNAL LIVE에서 둘 다 무언가를 잘 깜빡한다며 케미명이 만들어졌다. <br> 그러나 둘을 애써 포장하기 위해 깜냥이의 '깜'과 박쌤의 '박'이라고 말했다. ",
     image: "/uploads/chemistry/chem/S7/깜빡즈.png"
  },
  "김나경과 신위의 케미명": {
     description: "나경의 별명인 나키에서 '키'와 신위의 '위'를 합쳐서 만든 케미명이다. ",
     image: "/uploads/chemistry/chem/S7/키위즈.png"
  },
  "김나경과 린의 케미명": {
     description: "김나경의 S번호 7과 린의  S번호 17번이여서 칠칠즈로 케미명이 만들어지게 됐다.<br> photo by. april_s17 ",
     image: "/uploads/chemistry/chem/S7/칠칠즈.png"
  },
   "김나경과 정하연의 케미명": {
     description: "하연이의 프롬에서 첫 언급됐으며 서로 예상치 못한 상황에 우연히 겹치는 일이 많았다고 하여 '우리는 필연이다!'<br> 해서 생긴 케미명이다. ",
     image: "/uploads/chemistry/chem/S7/필연즈.png"
  },
  "김나경과 지연의 케미명":{
    description:" 나경의 별명인 '나키'와 지연의 '지'를 합쳐서 만든 케미명으로. 250124 나경 프롬을 통해서 언급이 됐었고 <br> 지연이한테 물어보겠다며 마무리됐었지만 260208 팬사인회에서 지연이가 언급함으로써 공식 케미가 되었다. <br> photo by. wavv57 ",
    image:"/uploads/chemistry/chem/S7/낰지즈.png"
 }
},
  "공유빈":{
  "공유빈과 윤서연의 케미명":{
    description: "뽀토즈의 세 번째 파생 케미명으로 밤은 유빈이의 별명인 유밤이에서 따온 것이고 <br> 뽀는 '뽀'얗다가 될 수도 있고 '뽀'차코가 될 수도 있다.",
    image: "/uploads/chemistry/chem/S1/뽀밤즈.png"
  },
  "공유빈과 정혜린의 케미명":{
   description: "남산 HAUS 시절 룸메이트였으며, 이때 둘 다 눈이 크다며 '왕눈즈'라는 케미명을 만들었다",
     image: "/uploads/chemistry/chem/S2/왕눈즈.png"
  },
  "공유빈과 이지우의 케미명":{
   description: "모드하우스 입사 전 JYP에서 함께 배우 연습생을 하였으며, **어느 SIGNAL**에서 이지우 '주변에 새로운 S가 될만한 친구 있나요?'라는 질문에 '있다'고 답했는데, 이때 떠올린 사람이 공유빈이었다고 나중에 밝혔다. 트리플에스 내에서 가장 유명한 케미 중 하나다.",
      image: "/uploads/chemistry/chem/S3/찐친즈.png",
    links:{ "어느 SIGNAL": "https://youtu.be/gWMq9RJC4ZA?si=VcU1opY6yPSZMnCt&t=488" }
  },
  "공유빈과 김채연의 케미명":{
   description: "드림센터 스터디룸의 MC를 맡아서 생긴 케미명.",
   image: "/uploads/chemistry/chem/S4/MC즈.png"
  },
  "공유빈과 김유연의 케미명":{
    description: "<b>유</b>연, <b>유</b>빈으로 두명다 이름에 '유'가 들어가 생겨난 조합명.",
     image: "/uploads/chemistry/chem/S5/두유즈.png"
  },
  "공유빈과 김수민의 케미명":{
  description: "웨이브들이 정한 케미로 둘 다 말랑말랑하게 생겨서 붙게 됐다.",
   image: "/uploads/chemistry/chem/S6/말랑즈.png"
 }, 
 "공유빈과 김나경의 케미명":{
     description: "230316 시그널에서 비교적 인원수가 적은 여의도 HAUS에 둘이 배정되는 것을 보고 <br>김유연이 둘을 격리 시켜놓는 게 아니냐는 드립을 쳤고,<br> 그 이후로 당사자인 둘만 빼고 모든 멤버들과 팬들이 둘을 격리즈로 부르게되었다.",
     image: "/uploads/chemistry/chem/S7/냥냥즈(격리즈).png"
 },
 "공유빈과 카에데의 케미명":{
 description:"카에데의 뜻이 단풍이여서 단풍의 '단'과 유빈의 별명인 유밤에서 '밤'이 합쳐진 케미명이다.<br> photo by.OvO_ple",
 image:"/uploads/chemistry/chem/S8/단밤즈.png"
 },
"공유빈과 곽연지의 케미명":{
  description:"아기호랑이 유빈이 아기 늑냥이 연지 둘 다 캬옹하는 아기맹수들이다라는 뜻을 가지고 있다. <br>(오피셜 케미명은 아닌 연지 팬계정의 투표를 통해서 정해진 케미명이다.) <br> photo by. Compexity_12",
  image:"/uploads/chemistry/chem/S8/캬옹즈.png"
 },
 "공유빈과 주빈의 케미명": {
      description:"240229 유빈이 프롬을 통해 팬이 주빈이랑 케미명이 뭐냐는 질문에 주빈이는 공주즈가 좋다고 하여 정해진 케미명이다. photo by. Bin_C1ndy_u",
       image:"/uploads/chemistry/chem/S8/공주즈.png"
    },
 "공유빈과 정하연의 케미명":{
 description:"250612 SIGNAL LIVE에서 정한 케미명으로, <b>공</b>유빈과 정하<b>연</b>의 이름에서 한 글자씩 가져왔따.",
 image:"/uploads/chemistry/chem/S8/공연즈.png"
 },
 "공유빈과 설린의 케미명":{
 description:"설린의 '설'과 유빈이의 별명인 유밤의 '밤'이 합쳐져 만든 케미명이다. 250813 콘서트 비하인드 시그널에서 유빈이가 직접 언급했으며 추후 유빈이 프롬을 통해서 한 번 더 언급한다.",
 image:"/uploads/chemistry/chem/S8/설밤즈.png"
 },
 "공유빈과 지연의 케미명":{
 description:"좋은 일이든 나쁜 일이든 매번 똑같이 함께 당하고 인원을 나눌 때에도 계속해서 붙어있음을 언급하며 <br> '원 플러스 원'을 줄인 '원플원즈가 되었다",
 image:"/uploads/chemistry/chem/S8/원플원즈.png"
 }
},
  "카에데":{
  "카에데와 윤서연의 케미명":{
   description: "물떡처럼 말랑말랑하고 동글둥글하게 생겼다 하여 팬들이 만들어준 케미명이다 .<br> photo by. @MapleSyrup_1220",
      image: "/uploads/chemistry/chem/S1/물떡즈.png"
  },
 "카에데와 정혜린의 케미명":{
      description:"카에데의 한국 뜻인 단풍의 '단'과 혜린의 별명인 리네의 '네'를 내로 바꾸며 한국식 사투리인 단내 단다는 '달달한 냄새가 난다'라는 뜻을 담고 있는 유머형 케미명이다 250811 시그널에서 혜린이가 직접 정한 케미명이다",
     image:"/uploads/chemistry/chem/S2/단내즈.png"
    },
  "카에데와 김채연의 케미명":{
      description: "과거 김채연과 카에데가 디즈니 공주 팬싸인회때 김채연이 엘사, 카에데는 안나가 되어 oo즈는 식상하니 프로즌으로 하게 됐다고 한다.<br> photo by. annyeongsubsubs",
      image: "/uploads/chemistry/chem/S4/프로즌.png"
  },
  "카에데와 김유연의 케미명":{
      description: "Hit the Floor 안무에는 2절 후렴 직후에 김유연과 카에데가 모두 무대의 왼쪽에 빠져 있는 구간이 존재하는데,<br> 팬사인회에서 해당 무대를 할 때에 이 구간에서 두 멤버가 늘 포옹을 하여 둘이 '나안아~' 하면서 케미명이 만들어졌다.<br> 이후 김유연이 fromm을 통해 해당 케미명을 공식화하였다. <br>Photo by @Sharon_0209",
      image: "/uploads/chemistry/chem/S5/나안아즈.png"
  },
  "카에데와 김수민의 케미명":{
   description:"성수하우스 시절 둘이 라이브를 하다가 어떤 케미명을 하고 싶냐는 WAV들의 질문에 수민이가 라이브 전에 <br> 감자탕 먹고 왔으니까 감자탕즈로 하겠다며 감자탕즈가 되었다.<br>(221129 시그널 라이브 참고) ",
   image:"/uploads/chemistry/chem/S6/감자탕즈.png"
   },
  "카에데와 공유빈의 케미명":{
   description:"카에데의 뜻이 단풍이여서 단풍의 '단'과 유빈의 별명인 유밤에서 '밤'이 합쳐진 케미명이다.<br> photo by.OvO_ple",
   image:"/uploads/chemistry/chem/S8/단밤즈.png"
  },
  "카에데와 서다현의 케미명":{
   description: "성수 HAUS의 블루방에서 약 5개월 간 룸메이트로 지내면서 매우 친밀한 관계.<br> **공식 영상**에 보면 케미 모음이 있는 것을 확인.",
    image:"/uploads/chemistry/chem/S9/블루방즈 (카현즈).png",
   links:{
    "공식 영상":"https://youtu.be/QP-aAIj8XEE?si=0ePOv1DVEonLs_Ho"
    }
  },
 "카에데와 코토네의 케미명":{
   description: "'카토네'라는 이름으로 공식에서 붙여졌지만, **230412 시그널 라이브**에서 카토네라는 이름이 마음에 안 든다며 둘 이름에서 'ㅋ'을 하나씩 따와 'ㅋㅋ즈'라는 이름을 만들었다. <br> 코토네는 240909 fromm에서 회사가 카토네라는 부르는 것에 불만을 표하며<br> 케미명이'ㅋㅋ즈'임을 다시 한번 분명히 말했다.",
    image:"/uploads/chemistry/chem/S9/ㅋㅋ즈.png",
    links:{
     "230412 시그널 라이브":"https://youtu.be/xuBXJvVugvM?si=49tXAFvEDc8Yq07d" }
  },
 "카에데와 곽연지의 케미명":{
   description:"한 WAV가 팬 사인회에 가서 연지에게 카에데와 어떤 케미명이 하고 싶냐는 포스트잇에 동글즈를 하고 싶다고 기록하였다.",
   image:"/uploads/chemistry/chem/S9/동글즈.png"
  },
  "카에데와 니엔의 케미명":{
   description: "니엔의 '니'와 카에데의 '카'를 따서 만들었으며, 두 사람의 국적에 맞게 중국어(你)와 일본어(可愛い)가 섞여있는 케미명.",
    image:"/uploads/chemistry/chem/S9/니카와이즈.png"
  },
  "카에데와 박소현의 케미명":{
   description: "소현의 '소'와 카에데의 뜻인 단풍의 '풍'을 합쳐진 케미명이다. Photo by. kaede_noway",
    image:"/uploads/chemistry/chem/S9/소풍즈.png"
  },
  "카에데와 마유의 케미명":{
   description: "마유의 '마'와 카에데의 '카'를 따서 지어진 케미명으로 **230726 SIGNAL LIVE**에서 만들었다.<br> photo by. manimanihus_o0",
    image:"/uploads/chemistry/chem/S9/마카롱즈.png",
    links: {
     "230726 SIGNAL LIVE":"https://youtu.be/-_PDWFnW5_w?si=KWQfmTE7qmfNtOBK" }
  },
   "카에데와 박시온의 케미명":{
   description: "배지전쟁 2에서 카에데가 한 명대사'**안 봤냐?**'와 박시온이 했던 명대사'**날겠냐?**'에서 각각'냐'를 따와 만든 케미명으로 250218 SIGNAL LIVE에서 만들었다",
    image:"/uploads/chemistry/chem/S9/냐냐즈.png",
    links:{
    "안 봤냐?":"https://youtube.com/shorts/rJLbClE-pF8?si=sRcA9HSZdQzfz2Kp",
    "날겠냐?": "https://youtube.com/shorts/fk5KbvMMYAg?si=5qKOTA3J0AiirLU7"
  }
 }
},
  "서다현":{
  "서다현과 윤서연의 케미명":{
      description:"햄햄다즈는 서연이의 별명인 '햄햄'과 다현의 '다'를 하겐다즈 아이스크림에 빗대 만들어진 귀여운 케미명이다.<br> 열일즈는 다현의 고유 번호인 S10을 한글식으로 부르면 '열'이 되고 <br> 서연의 고유 번호인 S1을 한글식으로 부르면 '일'이 된 <br> 그 둘을 합쳐 열일즈라 부르며 '열심히 일을 한다'의 준말이기도 하다. <br> 둘은 열심히 일을 하므로 서연이가 굉장히 맘에 들어 했고<br> **250909 시그널 라이브**에서 햄햄다즈와 열일즈 다 사용하자고 정했다. <br> 하지만 팬들은 햄햄다즈를 더 많이 사용할 것으로 보이긴 한다.",
      image:"/uploads/chemistry/chem/S1/햄햄다즈.png",
      links:{
       "250909 시그널 라이브":"https://youtu.be/Fx6P06K9zwk?si=n4M358B0RTkTQm29"
      }
  },
  "서다현과 정혜린의 케미명":{
    description: "동네 놀이터에서 어떤 어린아이가 둘을 이모들이라 불렀다는 썰을 230405 SIGNAL LIVE에서 풀었는데, 이때 만들어지게 됐다.",
     image: "/uploads/chemistry/chem/S2/이모즈.png"
  },
  "서다현과 김수민의 케미명":{
   description: "김수민의 '수'와 서다현의 '다'를 이용한 케미명. 둘이 말이 많다하여 SIGNAL LIVE에서 정해지게 됐다.<br> photo by @soomsoomsoom103",
      image: "/uploads/chemistry/chem/S6/수다즈.png"
  },
  "서다현과 김나경의 케미명":{
     description: "김나경의 '<b>나</b>'와 서다현의 '<b>다</b>'를 이용한 케미명. <br> 둘은 롯데 자이언츠 시구와 시타를 두 번이나 눈앞에서 실패한 경험이 있다. (우천 취소, 폭염 취소), <br>그러나 250816 드디어 시구와 시타를 해냈다. <br> **250816 롯데vs삼성**",
     image: "/uploads/chemistry/chem/S7/나다즈.png",
     links:{
      "250816 롯데vs삼성":"https://naver.me/FYqrH6ZP"}
  },
 "서다현과 카에데의 케미명":{
   description: "성수 HAUS의 블루방에서 약 5개월 간 룸메이트로 지내면서 매우 친밀한 관계. <br>**공식 영상**에 보면 케미 모음이 있는 것을 확인.",
    image:"/uploads/chemistry/chem/S9/블루방즈 (카현즈).png",
   links:{
    "공식 영상":"https://youtu.be/QP-aAIj8XEE?si=0ePOv1DVEonLs_Ho"
    }
  },
  "서다현과 코토네의 케미명":{
  description:"두 멤버의 개인 팬덤명 이름이 각각'소다단'과 '콜라단'이기 때문에 지어진 케미명으로, 코토네가 만들었다.<br> photo by. lovewav",
  image:"/uploads/chemistry/chem/S10/탄산즈.png"
  },
  "서다현과 박소현의 케미명":{
  description:"서다현의 대표 별명'소다'와 박소현의 이름'소현'을 합쳐서 만든 케미명.",
  image:"/uploads/chemistry/chem/S10/소다현즈.png"
  },
  "서다현과 정하연의 케미명":{
  description:"2024 추석 SIGNAL에서 설린과 함께 팀 이름을 '야무지'로 지은 것에서 따와 케미명을 '무지즈'로 지으려다가<br> 너무 무지 해보일 것 같다며 반대되는 뜻인 '지능즈'로 241025 SIGNAL LIVE에서 만들었다.",
  image:"/uploads/chemistry/chem/S10/지능즈.png"
  },
  "서다현과 박시온의 케미명":{
  description:"공식적인 <b>메인보컬</b>들의 조합, 둘다 공개 후 첫 활동에서 모두 메인보컬을 맡아서 지어지게된 케미명<br> photo by. Tiramisu0403",
  image:"/uploads/chemistry/chem/S10/보컬즈.png"
  },
  "서다현과 김채원의 케미명":{
  description:"김채원의 상징 이모지'딸기'와 서다현의 별명인 '소다'를 합친 케미명으로 **240830 SIGNAL LIVE**에서 정해졌다.",
  image:"/uploads/chemistry/chem/S10/딸기소다즈.png",
  links:{
   "240830 SIGNAL LIVE":"https://youtu.be/rEdeeRSGpaI?si=8hmPXEndS1afmxA_"
  }
 },
 "서다현과 설린의 케미명":{
  description:"다현 어린이, 설린 어린이에서 착안한 케미명으로 서로 어린이 같은 면모가 있어 정한 케미명 이라고 한다.<br> **260119 시그널**에서 무조건 '닿' 이라고 해야한다고 다현이가 당부했다.",
  image:"/uploads/chemistry/chem/S10/닿리니즈.png",
  links:{
   "260119 시그널":"https://youtu.be/IbGn0PnzNe0?si=rDOBTFGGlAutRr4k"
  }
},
 "서다현과 서아의 케미명":{
   description:"250423 시그널을 통해서 정해진 케미명이다. 서아의 '서'와 다현의 별명인 소다의 '소'를 조합하며 만든 케미명이다.",
   image:"/uploads/chemistry/chem/S10/서소즈.png"
   }
},
 "코토네":{
 "코토네와 윤서연의 케미명":{
  description: "공개 숫자가 1로만 구성된 S1과 S11이기에 1이 빼빼로를 닮아 빼빼로즈로 정하게 되었다.",
  image: "/uploads/chemistry/chem/S1/빼빼로즈.png"
  },
  "코토네와 정혜린의 케미명":{
   description: "250317 공식 SNS에 혜린이가 토네가 찍은 셀카를 올리며 사용. 혜린의 별명 리<b>네</b>와 코토<b>네</b>에서 따온 케미명으로 추측된다. ",
     image: "/uploads/chemistry/chem/S2/네네즈.png"
  },
 "코토네와 이지우의 케미명":{
  description:"260322코토네 프롬을 통해 쥬(지우)랑 또네(토네)라는 뜻을 가지고 있고<br> 일본어로 약간 계속 ずっと (즛토)와 비슷해서 정하게 되었다고 했다.",
   image:"/uploads/chemistry/chem/S3/쥬또즈.png"
},
 "코토네와 김채연의 케미명":{
  description: "과거 채연이 진행했던 보니하니에 <b>코</b>토네의 이름을 합친 것으로, 코토네가 fromm으로 후보를 받은 후 채연에게 물어봐 고도록 했다고 한다.",
      image: "/uploads/chemistry/chem/S4/코니하니.png"
  },
 "코토네와 김유연의 케미명":{
      description: "케미명 정하기를 미루다 보니 250418 코토네 Fromm에서 팬들이 미룬즈를 추천했다.<br> 김유연에게 확인받은 결과 좋다고 하여 공식화됐다.<br> photo by. @yume040310",
      image: "/uploads/chemistry/chem/S5/미룬즈.png"
  },
 "코토네와 김수민의 케미명": {
      description:"251129 하찌 행사에서 WAV가 수민이에게 둘의 케미명을 물었고 수민이는 타코야끼즈라 대답했다. <br> 둘다 얼굴이 동글동글해서다 서로 담당하고 있는 부분이 있냐 물었더니 토네가 타코야끼고 수민이는 즈라한다.<br> photo by. Kotoworld0310",
       image:"/uploads/chemistry/chem/S6/타코야끼즈.png"
    },
 "코토네와 김나경의 케미명":{
  description: "<b>나(낫)</b>경과 코<b>토</b>네를 합쳐 만든 케미명, 코토네가 fromm을 통해 밝혔다. ",
     image: "/uploads/chemistry/chem/S7/낫토즈.png"
  },
 "코토네와 카에데의 케미명":{
    description: "'카토네'라는 이름으로 공식에서 붙여졌지만, **230412 시그널 라이브**에서 카토네라는 이름이 마음에 안 든다며 둘 이름에서 'ㅋ'을 하나씩 따와 'ㅋㅋ즈'라는 이름을 만들었다.<br> 코토네는 240909 fromm에서 회사가 카토네라는 부르는 것에 불만을 표하며 <br> 케미명이'ㅋㅋ즈'임을 다시 한번 분명히 말했다.",
    image:"/uploads/chemistry/chem/S9/ㅋㅋ즈.png",
    links:{
     "230412 시그널 라이브":"https://youtu.be/xuBXJvVugvM?si=49tXAFvEDc8Yq07d" }
  },
  "코토네와 서다현의 케미명":{
   description:"두 멤버의 개인 팬덤명 이름이 각각'소다단'과 '콜라단'이기 때문에 지어진 케미명으로, 코토네가 만들었다.<br> photo by. lovewav",
  image:"/uploads/chemistry/chem/S10/탄산즈.png"
  },
 "코토네와 곽연지의 케미명":{
   description: "도쿄 HAUS에서 생활했던 두 명이라서 붙여진 케미명.도쿄즈가 그동안 해온 챌린지 <br> **2023**,**2024**,**2025**, **2026**.(노래는 Love On Top)",
    image:"/uploads/chemistry/chem/S11/도쿄즈.png",
    links:{
    "2023":"https://youtube.com/shorts/Gd5rp2LzQnI?si=7fukGHQqAPDafHf5",
    "2024":"https://youtube.com/shorts/VddfJ8OMU8k?si=MT9B79BCchk33zUe",
    "2025":"https://youtube.com/shorts/YpVCQ_ZyinI?si=CPYMH0BOaAjAEkM1" , 
    "2026":"https://youtube.com/shorts/-9TV9vE7zss?si=mSIATzY605I1JxE5"
    }
  },
   "코토네와 니엔의 케미명":{
   description: "일본어 인사말 중 하나인 곤니치와에서 따온 케미명으로 코토네의 코와 니엔의 니를 합쳐 인사하듯이 만든 케미명이다 230526 시그널 앞부분에 코니치와 하면서 인사하는 장면이 나온다.니엔 직접 만든 케미명이다.",
    image:"/uploads/chemistry/chem/S11/코니치와즈.png"
  },
 "코토네와 박소현의 케미명":{
   description: "박소현이 혼자 진행하던 230515 SIGNAL LIVE에 코토네가 게스트로 등장하며 같이 라이브를 진행.<br> 케미명은 말그대로 목소리가 저음인 두 멤버라서 지어졌다.",
    image:"/uploads/chemistry/chem/S11/저음즈.png"
  },
 "코토네와 마유의 케미명":{
   description: "공차를 즐겨 마시던 둘이었으나 마유가 공차를 졸업하면서 공차즈를 결성하지 못하고 해체했지만<br> 버블티는 자주 마신다 하여 버블티에 들어가는 주 원료인 타피오카를 좋아해 '타피즈'로 정했다. ",
    image:"/uploads/chemistry/chem/S11/토마토즈.png"
  },
 "코토네와 린의 케미명":{
  description:"260322코토네 프롬에서 다시금 언급해준 케미로 둘의 성씨인 <b>'카미'</b>모토와 <b>'카'</b>와카<b>'미'</b>에서 따온 케명 이라고 한다.", 
  image:"/uploads/chemistry/chem/S11/카미즈.png"
 },
 "코토네와 주빈의 케미명":{
   description: "본인들 피셜 만두상이라며 240709 SIGNAL LIVE에서 만들었다. 코토네는 물만두, 주빈은 찐만두이다.<br> 매번 프롬에서 코토네는 주빈이가 멤버중 본인의 최애라고 밝혔다.<br>photo by. Kotoworld0310 ",
    image:"/uploads/chemistry/chem/S11/만두즈.png"
  },
 "코토네와 박시온의 케미명": {
       description:"251116 코스모 라이브에서 정한 케미명이다.<br> 시온이의 별명인 숀과 코토네의 상징중 하나인 콜라를 합쳐 숀콜라로 정하게 되었다. <br> photo by. tipleS_S14_fan",
       image:"/uploads/chemistry/chem/S11/숀콜라.png"
 },
 "코토네와 김채원의 케미명":{
   description:"코토네의 '코'와 채원의 '채'를 조합하면서 만들어진 케미명이다 ~즈를 포함하지않은 '코챌라'가 케미명이라고 한다.",
   image:"/uploads/chemistry/chem/S11/코챌라.png"
 },
 "코토네와 지연의 케미명":{
   description: "핑구의 목소리를 따라 하는 소리. 지연이 이 소리를 잘 따라 하는데, <br> 코토네가 핑구 따라 하기를 굉장히 좋아해서 케미명으로 정함.<br> '눕눕'의 앞뒤 글자가 똑같은 것처럼 두 사람도 취향이 매우 비슷하여 코토네가 눕1, 지연이 눕2를 맡고있다.<br> **(케미 참고 영상)**",
    image:"/uploads/chemistry/chem/S11/눕눕즈.png",
    links:{
     "(케미 참고 영상)":"https://youtu.be/6zRg8x27D5Y?si=7hqdV7lcuwAFe5I7&t=181"
   }
  }
},
  "곽연지": {
    "곽연지와 이지우의 케미명":{
     description:"둘의 상징색을 섞으면 연두색임 둘 다 연두 같은 풋풋함이 있고 <br> 12(연지)3(지우) 123 번호 '연'결되는 '두'명이라는 뜻을 담고 있다. <br>(오피셜 케미명은 아닌 연지 팬계정의 투표를 통해서 정해진 케미명이다.)",
    image:"/uploads/chemistry/chem/S3/연두즈.png"
  },
    "곽연지와 김수민의 케미명": {
      description: "둘이 뭉치면 레전드라 레전드로 케미명 짓는다고 수민이 프롬에서 언급.",
      image: "/uploads/chemistry/chem/S6/레전드.png"
    },
   "곽연지와 공유빈의 케미명":{
     description:"아기호랑이 유빈이 아기 늑냥이 연지 둘 다 캬옹하는 아기맹수들이다라는 뜻을 가지고 있다. <br>(오피셜 케미명은 아닌 연지 팬계정의 투표를 통해서 정해진 케미명이다.) <br> photo by. Compexity_12",
      image:"/uploads/chemistry/chem/S8/캬옹즈.png"
},
   "곽연지와 카에데의 케미명":{
   description:"한 WAV가 팬 사인회에 가서 연지에게 카에데와 어떤 케미명이 하고 싶냐는 포스트잇에 동글즈를 하고 싶다고 기록하였다.",
   image:"/uploads/chemistry/chem/S9/동글즈.png"
   },
    "곽연지와 코토네의 케미명":{
    description: "도쿄 HAUS에서 생활했던 두 명이라서 붙여진 케미명.도쿄즈가 그동안 해온 챌린지 <br> **2023**,**2024**,**2025**, **2026**.(노래는 Love On Top)",
    image:"/uploads/chemistry/chem/S11/도쿄즈.png",
    links:{
    "2023":"https://youtube.com/shorts/Gd5rp2LzQnI?si=7fukGHQqAPDafHf5",
    "2024":"https://youtube.com/shorts/VddfJ8OMU8k?si=MT9B79BCchk33zUe",
    "2025":"https://youtube.com/shorts/YpVCQ_ZyinI?si=CPYMH0BOaAjAEkM1" , 
    "2026":"https://youtube.com/shorts/-9TV9vE7zss?si=mSIATzY605I1JxE5"
    }
  },
 "곽연지와 니엔의 케미명":{
    description:"니엔의 '엔'과 연지의 '지'를 합쳐 만든 케미명으로<br> 260320 시그널에서 소현의 추천으로 소현과 니엔이 직접 정한 케미명이다. photo by. @0108_biscuit",
    image:"/uploads/chemistry/chem/S12/엔지즈.png"
   },
   "곽연지와 주빈의 케미명":{
    description:"주빈이의 애칭인 콩(bean)과 연지의 오리(duck 덕)이 만나 만들어진 케미명이라고 한다. <br>(오피셜 케미명은 아닌 연지 팬계정의 투표를 통해서 정해진 케미명이다.)",
    image:"/uploads/chemistry/chem/S12/콩덕즈.png"
   },
   "곽연지와 정하연의 케미명":{
     description:"고슴도'치' 하연이와 '초'월소녀 연지의 만남에 귀엽다의 ㄱ 을 추가 하여 만들어진 케미명이라고 한다. <br>(오피셜 케미명은 아닌 연지 팬계정의 투표를 통해서 정해진 케미명이다.) <br> photo by. YH_S12S19 ",
     image:"/uploads/chemistry/chem/S12/칙촉즈.png"
  },
    "곽연지와 김채원의 케미명": {
      description: "240803 팬싸에서 둘의 팬싸템이 슈가슈가룬의 바닐라 쇼콜라여서 슈가즈라고 언급.",
      image: "/uploads/chemistry/chem/S12/슈가즈.png"
    },
    "곽연지와 지연의 케미명": {
      description: "둘의 고유 번호가 S12와 S24로 12월24일 (크리스마스 이브)가 떠올라 이브즈로 언급되어 케미명이 되었다. ",
      image: "/uploads/chemistry/chem/S12/이브즈.png"
    }    
},
 "니엔":{
  "니엔과 윤서연의 케미명":{
      description: "250330 서연의 fromm에서 정해짐.<br>윤서연이 닮은 동물 햄스터의 햄과 니엔의 닮은 동물 강아지(멍멍이)를 합쳐 만든 케미명이다, 하지만 해외의 웨이브들은 햄멍즈 보다 Sunnyz를 더  많이 사용한다",
      image: "/uploads/chemistry/chem/S1/햄멍즈.png"
   },
   "니엔과 김채연의 케미명":{
    description: "김채연의 '연'과 니엔의 '엔'을 합쳐서 만든 케미명이자, 연예인이라는 중의적 의미를 담고 있는 케미명이다. ",
    image: "/uploads/chemistry/chem/S4/연엔즈.png"
   },
   "니엔과 김나경의 케미명":{
    description: "밤양갱+고냥이의 '냥'+갱얼쥐의'갱',<br> 2024년 9월 둘이 함께 IDOL RADIO의 스페셜 DJ로 활동할 때에도 밤냥갱즈라는 케미명을 사용. ",
     image: "/uploads/chemistry/chem/S7/밤냥갱즈.png"
   },
   "니엔과 카에데의 케미명":{
    description: "니엔의 '니'와 카에데의 '카'를 따서 만들었으며, 두 사람의 국적에 맞게 중국어(你)와 일본어(可愛い)가 섞여있는 케미명.",
    image:"/uploads/chemistry/chem/S9/니카와이즈.png"
    },
    "니엔과 코토네의 케미명":{
     description: "일본어 인사말 중 하나인 곤니치와에서 따온 케미명으로 코토네의 코와 니엔의 니를 합쳐 인사하듯이 만든 케미명이다 230526 시그널 앞부분에 코니치와 하면서 인사하는 장면이 나온다.니엔 직접 만든 케미명이다.",
    image:"/uploads/chemistry/chem/S11/코니치와즈.png"
    },
   "니엔과 곽연지의 케미명":{
    description:"니엔의 '엔'과 연지의 '지'를 합쳐 만든 케미명으로<br> 260320 시그널에서 소현의 추천으로 소현과 니엔이 직접 정한 케미명이다. photo by. @0108_biscuit",
    image:"/uploads/chemistry/chem/S12/엔지즈.png"
   },
    "니엔과 박소현의 케미명":{
    description:"**230425 SIGNAL**에서 니엔이 직접 정하게 된 케미명으로<br> 박소현의 '박'과 니엔의 본명인 쉬니엔츠에서 '쉬'를 따온 케미명이다. ",
     image:"/uploads/chemistry/chem/S13/박쉬즈.png",
     links:{
     "230425 SIGNAL":"https://youtu.be/kYsHpzfkJfI?si=QxYonq74ZowpWTPY"
     }
    },
      "니엔과 신위의 케미명":{
     description:"그룹 내에서 둘뿐인 중화권 출신 멤버이다. 걸스플래닛999: 소녀대전 출연으로 인해 이미 서로 알던 사이였으며,<br>  서로가 작은s라는걸 모른 채로 한국에서 만난적도 있다.  ",
     image:"/uploads/chemistry/chem/S13/쉬니엔즈.png"
    },
    "니엔과 정하연의 케미명":{
     description:"하연의 '하'와 니엔의 '니'가 합쳐져 만들어진 케미명이다. <br> 250429 촠촠 블소드 퍼퍼먼스 촬영 비하인드 시그널에서 언급된다.  ",
     image:"/uploads/chemistry/chem/S13/니하오즈.png"
    },
    "니엔과 박시온의 케미명":{
     description:"니엔의 '니'와 시온의 '온', 그리고 둘다 빵을 좋아한다는 특징을 합쳐 만들어진 케미명이다.",
     image:"/uploads/chemistry/chem/S13/어니온빵즈.png"
    },
    "니엔과 서아의 케미명":{
     description:"**250514** 깨어 쇼케이스 비하인드 시그널에서 언급되었으며 <br> 둘다 똥머리를 하고 있어서 똥자매라고 부르다가 서로 똥을 좋아하냐고.. 물으며 똥똥즈가 결성 되었다.<br> photo by. madebykenny ",
     image:"/uploads/chemistry/chem/S13/똥똥즈.png",
     links:{
      "250514":"https://youtu.be/jmEPtvBSn44?si=8CVBHuNYx82lf-5L&t=370"
    }
   }
  },
 "박소현":{
  "박소현과 윤서연의 케미명":{
      description: "서로가 서로의 최애라서 생긴 케미명.<br> 웨이브들이 둘이 친해진 계기를 묻는 질문에도 윤서연은 서로가 최애이기 때문에 금방 친해졌다고 말하였다.",
      image: "/uploads/chemistry/chem/S1/최애즈.png"
  },
  "박소현과 정혜린의 케미명":{
   description: "<b>소</b>현과 <b>리</b>네를 합쳐 만든 케미명으로, 231117 박소현의 fromm에서 만들었다.",
   image: "/uploads/chemistry/chem/S2/소리꾼즈.png"
  },
  "박소현과 김유연의 케미명":{
   description: "첫인상으로 박소현이 냉하게 생기고 김유연이 온하게 생겨서 냉온즈로 케미명이 지어짐.",
   image: "/uploads/chemistry/chem/S5/냉온즈.png"
  },
 "박소현과 김나경의 케미명":{
  description: "태어난 년도,월,일까지 모두 똑같은 멤버이다. <br> 241226 SIGNAL LIVE에서 둘 다 무언가를 잘 깜빡한다며 케미명이 만들어졌다.<br> 그러나 둘을 애써 포장하기 위해 깜냥이의 '깜'과 박쌤의 '박'이라고 말했다. ",
     image: "/uploads/chemistry/chem/S7/깜빡즈.png"
  },
  "박소현과 카에데의 케미명":{
  description: "소현의 '소'와 카에데의 뜻인 단풍의 '풍'을 합쳐진 케미명이다. Photo by. kaede_noway",
   image:"/uploads/chemistry/chem/S9/소풍즈.png"
 },
 "박소현과 서다현의 케미명":{
   description:"서다현의 대표 별명'소다'와 박소현의 이름'소현'을 합쳐서 만든 케미명.",
  image:"/uploads/chemistry/chem/S10/소다현즈.png"
  },
  "박소현과 코토네의 케미명":{
   description: "박소현이 혼자 진행하던 230515 SIGNAL LIVE에 코토네가 게스트로 등장하며 같이 라이브를 진행.<br> 케미명은 말그대로 목소리가 저음인 두 멤버라서 지어졌다.",
    image:"/uploads/chemistry/chem/S11/저음즈.png"
  },
  "박소현과 니엔의 케미명":{
    description:"**230425 SIGNAL**에서 니엔이 직접 정하게 된 케미명으로<br> 박소현의 '박'과 니엔의 본명인 쉬니엔츠에서 '쉬'를 따온 케미명이다. ",
     image:"/uploads/chemistry/chem/S13/박쉬즈.png",
     links:{
     "230425 SIGNAL":"https://youtu.be/kYsHpzfkJfI?si=QxYonq74ZowpWTPY"
    }
  },
 "박소현과 신위의 케미명":{
  description:"소현 <b>'소'</b>와 신위<b>'신'</b>을 합쳐서 만든 케미명이다.<br> 별 의미 없이 만들어진 케미명이지만 트리플에스 내에서 가장 유명한 케미 중 하나다.",
   image:"/uploads/chemistry/chem/S14/소신즈.png"
 },
 "박소현과 린의 케미명":{
  description:"린이 소현을 '소쨩'이라고 부른 것에서 착안해,<br> 포뇨를 닮은 린과 이름에 '소'가 들어가는 소스케를 소현이 담당하며 만들어진 케미명.",
  image:"/uploads/chemistry/chem/S14/포뇨즈.png"
  },
 "박소현과 박시온의 케미명":{
  description:"성이 '박'인 S들의 조합, 흥부전에서 제비가'박씨'를 물어온 것에서 착안해 만들어낸 케미명. photo by. boang_9424",
  image:"/uploads/chemistry/chem/S14/제비즈.png"
  },
 "박소현과 설린의 케미명":{
  description:"소현의 '<b>소</b>'와 설린의 '<b>설</b>'을 합쳐 만들어진 케미명이다.<br>25.10.25 소현 Fromm에서 언급이 나왔으며 설린이가 맘에 들어 하면 이렇게 하기로 했는데<br> 설린이도 마음에 들어 한다며 소설즈로 하게 되었다.<br>  photo by. Plumver1130",
  image:"/uploads/chemistry/chem/S14/소설즈.png"
  },
  "박소현과 서아의 케미명":{
  description:"서아의 fromm을 통해 만들어진 케미명으로, 햇님은 서아, 달님은 박소현을 뜻한다.",
  image:"/uploads/chemistry/chem/S14/햇님달님즈.png"
  }
},
  "신위":{
   "신위와 윤서연의 케미명":{
    description: "윤서연과 저우신위의 초성이'ㅇㅅㅇ'로 같다는 점에서 230913 SIGNAL LIVE에서 케미명을 정함.",
     image: "/uploads/chemistry/chem/S1/ㅇㅅㅇ즈.png"
   },
   "신위와 정혜린의 케미명":{
    description:"250210 SIGNAL에서 자몽 샤베트가 만들어준 케미명. 둘이 쇼핑을 종종 같이가고, 취향도 잘 맞는다고 밝힘.",
     image:"/uploads/chemistry/chem/S2/청담동 사모님즈.png"
   },
  "신위와 김유연의 케미명":{
  description: "Girls Never Die 뮤비 촬영 비하인드 영상에서 서로 같은 옷을 입고 비슷한 헤어스타일을 하니<br> 쌍둥이 같다는 이야기를 하였고, 자막으로 쌍둥이즈라는 케미명이 나왔다.",
   image: "/uploads/chemistry/chem/S5/쌍둥이즈.png"
  },
  "신위와 김나경의 케미명":{
   description: "나경의 별명인 나키에서 '키'와 신위의 '위'를 합쳐서 만든 케미명이다. ",
   image: "/uploads/chemistry/chem/S7/키위즈.png"
   },
  "신위와 니엔의 케미명":{
   description:"그룹 내에서 둘뿐인 중화권 출신 멤버이다. 걸스플래닛999: 소녀대전 출연으로 인해 이미 서로 알던 사이였으며, <br> 서로가 작은s라는걸 모른 채로 한국에서 만난적도 있다. ",
   image:"/uploads/chemistry/chem/S13/쉬니엔즈.png"
  },
  "신위와 박소현의 케미명":{
  description:"소현 <b>'소'</b>와 신위<b>'신'</b>을 합쳐서 만든 케미명이다.<br> 별 의미 없이 만들어진 케미명이지만 트리플에스 내에서 가장 유명한 케미 중 하나다.",
   image:"/uploads/chemistry/chem/S14/소신즈.png"
   },
  "신위와 김채원의 케미명":{
   description:"240724 SIGANAL LIVE에서 착안됐으며 숨기는 게 많은 신위와 채원을 의미하는 케미명이다. <br>(서로 유명한 파트너 케미가 있음에도 불구하고 다른 멤버들에서 치근덕 대서 생긴 케미명이기도하다)",
   image:"/uploads/chemistry/chem/S15/비밀즈.png"
   }
},
  "마유":{
   "마유와 이지우의 케미명":{
   description: "지<b>우</b>와 마<b>유</b>의 이름을 합쳐 만든 케미명.",
   image: "/uploads/chemistry/chem/S3/우유즈.png"
  },
 "마유와 김채연의 케미명":{
  description: "채<b>연</b>과 마<b>유</b>를 합쳐 만든 케미명",
      image: "/uploads/chemistry/chem/S4/연유즈.png"
  },
 "마유와 김유연의 케미명":{
  description: "유연과 마유는 트리플에스에서 대표 토끼상으로 유연이는 큰 토끼 마유는 작은 토끼를 담당하고 있어서 토끼즈라 부른다 일부 팬들은 마유연을 사용하기도 한다.",
   image: "/uploads/chemistry/chem/S5/토끼즈.png"
  },
 "마유와 카에데의 케미명":{
  description: "마유의 '마'와 카에데의 '카'를 따서 지어진 케미명으로 **230726 SIGNAL LIVE**에서 만들었다.<br> photo by. manimanihus_o0",
  image:"/uploads/chemistry/chem/S9/마카롱즈.png",
  links: {
     "230726 SIGNAL LIVE":"https://youtu.be/-_PDWFnW5_w?si=KWQfmTE7qmfNtOBK" }
 },
 "마유와 코토네의 케미명":{
   description: "공차를 즐겨 마시던 둘이었으나 마유가 공차를 졸업하면서 공차즈를 결성하지 못하고 해체했지만<br> 버블티는 자주 마신다 하여 버블티에 들어가는 주 원료인 타피오카를 좋아해 '타피즈'로 정했다. ",
    image:"/uploads/chemistry/chem/S11/토마토즈.png"
  },
 "마유와 박시온의 케미명":{
  description:"<b>마</b>유와 <b>시</b>온의 이름을 합쳐 만든 케미명으로 240806 SIGNAL LIVE에서 정해진 케미명.",
  image:"/uploads/chemistry/chem/S16/마시멜로우즈.png"
  },
 "마유와 김채원의 케미명":{
  description:"머리색이 산리오 캐릭인 키키와 라라의 색과 비슷한 파랑색 분홍색이기 때문이다.",
  image:"/uploads/chemistry/chem/S16/키키라라즈.png"
 }
},
 "린":{
  "린과 정혜린의 케미명":{
  description: "260412 혜린과 린의 생일 시그널에서 정했으며<br> 상어를 일본어로 뜻하는 샤메의 '샤'와 고양이를 귀엽게 부르는 냥냥이의 '냥'이 합쳐져서 만들어진 케미명이다<br> 웨이브를 사냥하겠다는 날카로운 느낌이 들어서 좋다고한다.<br> (임시 케미명) ",
   image: "/uploads/chemistry/chem/S2/사냥즈.png"
 },
  "린과 김나경의 케미명":{
  description: "김나경의 S번호 7과 린의  S번호 17번이여서 칠칠즈로 케미명이 만들어지게 됐다.<br> photo by. april_s17 ",
   image: "/uploads/chemistry/chem/S7/칠칠즈.png"
 },
 "린과 코토네의 케미명":{
  description:"260322코토네 프롬에서 다시금 언급해준 케미로 둘의 성씨인 <b>'카미'</b>모토와 <b>'카'</b>와카<b>'미'</b>에서 따온 케명 이라고 한다.", 
  image:"/uploads/chemistry/chem/S11/카미즈.png"
 },
 "린과 박소현의 케미명":{
   description:"린이 소현을 '소쨩'이라고 부른 것에서 착안해,<br> 포뇨를 닮은 린과 이름에 '소'가 들어가는 소스케를 소현이 담당하며 만들어진 케미명.",
  image:"/uploads/chemistry/chem/S14/포뇨즈.png"
  },
 "린과 주빈의 케미명":{
  description:"그룹 내에서 이름이 외자인 단 둘뿐인 멤버라서 생긴 케미명.",
  image:"/uploads/chemistry/chem/S17/외자즈.png"
 },
 "린과 정하연의 케미명":{
  description:"하<b>연</b>과 <b>린(인)</b>의 이름을 합쳐 만든 케미명.",
  image:"/uploads/chemistry/chem/S17/연인즈.png"
 },
 "린과 박시온의 케미명":{
  description:"아무런 피해를 끼치지 않을것 같고 무해한 둘이기 때문이다. <br> 둘중 한명이라도 피해를 끼치게 된다면 유해즈(?)가 될수도 있다. ",
  image:"/uploads/chemistry/chem/S17/무해즈.png"
 },
 "린과 서아의 케미명":{
  description:"도쿄 디즈니씨의 놀이기구인 '소아링'과 비슷한 발음을 이용해 만들어진 케미명, <br>서아의 fromm에서 팬들이 추천해 주었던 케미명이며,<br> 린이 이를 받아들여 공식 케미명이 되었다. ",
  image:"/uploads/chemistry/chem/S17/서아린즈.png"
  },
 "린과 지연의 케미명":{
   description:"251114 린의 코스모 라이브에서 얼마 전 지연 언니와 케미명을 정해왔다고 말했었다.<br> 잠미즈는 ＇잠이 많은 미녀들이라는 뜻을 가지고 있다.＇ 공식은 아니고 당시에는 임시 케미명이라고 하였다. <br> 그리고 **260105 시그널**을 통해 잠미즈를 결정하는 장면이 나왔고<br> 그 이후 임시라고 했지만 둘이 만나면 잠미즈를 사용하면서 공식으로 굳어진 듯하다",
   image:"/uploads/chemistry/chem/S17/잠미즈.png",
   links:{
            "260105 시그널":"https://youtu.be/Ijn-yOD1rCI?si=sAkzcHYPQe3jKoNo"
    }
  }
},
  "주빈":{
   "주빈과 김채연의 케미명":{
   description: "251224 코스모 라이브에서 서로는 덮은 머리보다 이마가 보여야 이쁘다며 이마즈로 부르기 시작했다. 원래는 241031 SIGNAL LIVE에서 서로 어색하고 비즈니스 사이여서 비스니즈로 사용하였었지만 1년 사이 많이 친해지고 새롭게 케미명을 만든 듯하다.",
   image: "/uploads/chemistry/chem/S4/비스니즈.png"
  },
  "주빈과 김수민의 케미명":{
    description:" 240807 주빈이 fromm에서 수민이와 케미명이 있냐는 질문에 잭과콩나뭉즈라고 언급했으며 수민이가 '잭'이고 주빈이가 '콩나뭉'이라고 한다 왜 '뭉'인지는 모르겠다고  수민이가 직접 정했으나 1년 후 수민이가 까먹고 주빈이만 기억하는 짝사랑 케미명이다(?) 250913 주빈 fromm에서 확인할 수 있다. <br> Photo by. DoNot_S6",
     image:"/uploads/chemistry/chem/S6/잭과콩나뭉즈.png"
    },
  "주빈과 공유빈의 케미명": {
     description:"240229 유빈이 프롬을 통해 팬이 주빈이랑 케미명이 뭐냐는 질문에 주빈이는 공주즈가 좋다고 하여 정해진 케미명이다. photo by. Bin_C1ndy_u",
       image:"/uploads/chemistry/chem/S8/공주즈.png"
    },
  "주빈과 코토네의 케미명":{
   description: "본인들 피셜 만두상이라며 240709 SIGNAL LIVE에서 만들었다. 코토네는 물만두, 주빈은 찐만두이다.<br> 매번 프롬에서 코토네는 주빈이가 멤버중 본인의 최애라고 밝혔다.<br>photo by. Kotoworld0310 ",
    image:"/uploads/chemistry/chem/S11/만두즈.png"
  },
  "주빈과 곽연지의 케미명":{
   description:"주빈이의 애칭인 콩(bean)과 연지의 오리(duck 덕)이 만나 만들어진 케미명이라고 한다. <br>(오피셜 케미명은 아닌 연지 팬계정의 투표를 통해서 정해진 케미명이다.)",
    image:"/uploads/chemistry/chem/S12/콩덕즈.png"
 },
  "주빈과 린의 케미명":{
  description:"그룹 내에서 이름이 외자인 단 둘뿐인 멤버라서 생긴 케미명.",
  image:"/uploads/chemistry/chem/S17/외자즈.png"
  },
  "주빈과 정하연의 케미명":{
  description:"SIGNAL LIVE에서 둘의 관계성이 톰과제리같다고 하여 케미명을 톰과제리즈로 정했다.<br> 톰은 정하연이고, 제리는 주빈이다.",
  image:"/uploads/chemistry/chem/S18/톰과제리즈.png"
 },
 "주빈과 박시온의 케미명":{
  description:"주빈의 '주'와 박시온의 '시온'을 합쳐서 만들어진 케미명, 240112 SIGNAL LIVE에서 직접 만들었다.",
  image:"/uploads/chemistry/chem/S18/밥주시온즈.png"
  },
  "주빈과 김채원의 케미명":{
  description:"260316 채원 프롬을 통해 첫 언급이 된 후, 260320 주빈의 코스모 라이브에서 또 한번 언급이 되었다. <br> 주빈 이름의 빈을 ＇콩＇과 채원이의 상징인 ＇갸루＇를 합쳐서 콩가루를 둘만의  조합명인 콩갸루즈로 만든 듯하다.",
  image:"/uploads/chemistry/chem/S18/콩갸루즈.png"
  },
 "주빈과 설린의 케미명":{
  description:"<b>설</b>린+ 주<b>빈(빙)</b> 주빈이 직접 fromm을 통해 만든 케미명이며, 이후 설린 또한 이를 받아들여 공식 케미명이 되었다.",
  image:"/uploads/chemistry/chem/S18/설빙즈.png"
 },
 "주빈과 서아의 케미명":{
  description:"서아의 상징인 해와 주빈의 빈이 콩을 뜻하여 둘을 합쳐 햇콩즈로 케미명을 만들었다.<br> 나이순으로 막내와 그 바로 앞에 위치한 S의 조합으로,<br> 막내즈 조합임에 불구하고 케미를 찾기 어려웠는데 월드투어를 함께한 이후로 둘의 케미가 많이 보이기 시작.",
  image:"/uploads/chemistry/chem/S18/햇콩즈.png"
 }
},
 "정하연":{
  "정하연과 정혜린의 케미명":{
   description: "서아의 공개 전까지 팀 내에 단 둘뿐인 정씨 멤버들이었기 때문에 지었던 케미명.",
    image: "/uploads/chemistry/chem/S2/정자매즈.png"
  },
  "정하연과 김채연의 케미명":{
  description: "Girls Never Die 활동 당시, 더쇼 대기실에서 하연이 채연에게 뽀뽀하는 시늉을 하였는데  채연이 피하지 않아 의도치 않게 뽀뽀를 하게 된 적이 있음.\n 240716 SIGNAL LIVE에서 하연이 당시의 일을 회상하며, 눈을 감고 뽀뽀하는 시늉을 했는데 촉촉한 느낌이 들어 놀랐다고 밝혀 촉촉즈라는 케미명을 제안.",
      image: "/uploads/chemistry/chem/S4/촉촉즈.png"
 }, 
  "정하연과 김나경의 케미명":{
    description: "하연이의 프롬에서 첫 언급됐으며 서로 예상치 못한 상황에 우연히 겹치는 일이 많았다고 하여 '우리는 필연이다!'<br> 해서 생긴 케미명이다. ",
     image: "/uploads/chemistry/chem/S7/필연즈.png"
  },
  "정하연과 공유빈의 케미명":{
   description:"250612 SIGNAL LIVE에서 정한 케미명으로, <b>공</b>유빈과 정하<b>연</b>의 이름에서 한 글자씩 가져왔따.",
    image:"/uploads/chemistry/chem/S8/공연즈.png"
 },
 "정하연과 서다현의 케미명":{
  description:"2024 추석 SIGNAL에서 설린과 함께 팀 이름을 '야무지'로 지은 것에서 따와 케미명을 '무지즈'로 지으려다가<br> 너무 무지 해보일 것 같다며 반대되는 뜻인 '지능즈'로 241025 SIGNAL LIVE에서 만들었다.",
  image:"/uploads/chemistry/chem/S10/지능즈.png"
  },
 "정하연과 곽연지의 케미명":{
     description:"고슴도'치' 하연이와 '초'월소녀 연지의 만남에 귀엽다의 ㄱ 을 추가 하여 만들어진 케미명이라고 한다. <br>(오피셜 케미명은 아닌 연지 팬계정의 투표를 통해서 정해진 케미명이다.) <br> photo by. YH_S12S19 ",
     image:"/uploads/chemistry/chem/S12/칙촉즈.png"
  },
 "정하연과 니엔의 케미명":{
   description:"하연의 '하'와 니엔의 '니'가 합쳐져 만들어진 케미명이다. <br> 250429 촠촠 블소드 퍼퍼먼스 촬영 비하인드 시그널에서 언급된다.  ",
     image:"/uploads/chemistry/chem/S13/니하오즈.png"
  },
 "정하연과 린의 케미명":{
   description:"하<b>연</b>과 <b>린(인)</b>의 이름을 합쳐 만든 케미명.",
  image:"/uploads/chemistry/chem/S17/연인즈.png"
 },
 "정하연과 주빈의 케미명":{
 description:"SIGNAL LIVE에서 둘의 관계성이 톰과제리같다고 하여 케미명을 톰과제리즈로 정했다.<br> 톰은 정하연이고, 제리는 주빈이다.",
  image:"/uploads/chemistry/chem/S18/톰과제리즈.png"
 },
 "정하연과 김채원의 케미명":{
  description:"둘다 볼이 모찌처럼 잘늘어난다는 의미와 둘을'못찢'어놓는다는 의미도 담겨져있다. <br>**240409** SIGNAL LIVE에서 정해진 케미명.<br>트리플에스 내에서 가장 유명한 케미 중 하나다.",
  image:"/uploads/chemistry/chem/S19/모찌즈.png",
  links:{
  "240409":"https://youtu.be/BDgXKDeQ7QE?si=iz0kgdQiD6YHacvQ"
  }
 }
},
   "박시온": {
    "박시온과 윤서연의 케미명": {
      description: "서연과 시온은 둘 다 대전출신이고빵을 좋아해서 지어진 케미명으로 '빵을 냠냠먹다'를 줄여만든 케미명이다.",
      image: "/uploads/chemistry/chem/S1/빵냠즈.png"
    },
    "박시온과 카에데의 케미명": {
      description: "배지전쟁 2에서 카에데가 한 명대사 '**안 봤냐?**'와 박시온이 했던 명대사 '**날겠냐?**'에서 각각 '냐'를 따와 만든 케미명으로 250218 SIGNAL LIVE에서 만들었다",
      image: "/uploads/chemistry/chem/S9/냐냐즈.png",
      links: {
        "안 봤냐?": "https://youtube.com/shorts/rJLbClE-pF8?si=sRcA9HSZdQzfz2Kp",
        "날겠냐?": "https://youtube.com/shorts/fk5KbvMMYAg?si=5qKOTA3J0AiirLU7"
      }
    },
    "박시온과 서다현의 케미명": {
      description:"공식적인 <b>메인보컬</b>들의 조합, 둘다 공개 후 첫 활동에서 모두 메인보컬을 맡아서 지어지게된 케미명<br> photo by. Tiramisu0403",
       image:"/uploads/chemistry/chem/S10/보컬즈.png"
    },
     "박시온과 코토네의 케미명": {
       description:"251116 코스모 라이브에서 정한 케미명이다.<br> 시온이의 별명인 숀과 코토네의 상징중 하나인 콜라를 합쳐 숀콜라로 정하게 되었다. <br> photo by. tipleS_S14_fan",
       image:"/uploads/chemistry/chem/S11/숀콜라.png"
    },
    "박시온과 니엔의 케미명": {
      description: "니엔의 '니'와 시온의 '온', 그리고 둘다 빵을 좋아한다는 특징을 합쳐 만들어진 케미명이다.",
      image: "/uploads/chemistry/chem/S13/어니온빵즈.png"
    },
    "박시온과 박소현의 케미명": {
      description: "성이 '박'인 S들의 조합, 흥부전에서 제비가 '박씨'를 물어온 것에서 착안해 만들어낸 케미명. photo by. boang_9424",
      image: "/uploads/chemistry/chem/S14/제비즈.png"
    },
    "박시온과 마유의 케미명": {
      description: "<b>마</b>유와 <b>시</b>온의 이름을 합쳐 만든 케미명으로 240806 SIGNAL LIVE에서 정해진 케미명.",
      image: "/uploads/chemistry/chem/S16/마시멜로우즈.png"
    },
    "박시온과 린의 케미명": {
      description:"아무런 피해를 끼치지 않을것 같고 무해한 둘이기 때문이다. <br> 둘중 한명이라도 피해를 끼치게 된다면 유해즈(?)가 될수도 있다. ",
       image:"/uploads/chemistry/chem/S17/무해즈.png"
    },
    "박시온과 주빈의 케미명": {
      description: "주빈의 '주'와 박시온의 '시온'을 합쳐서 만들어진 케미명, 240112 SIGNAL LIVE에서 직접 만들었다.",
      image: "/uploads/chemistry/chem/S18/밥주시온즈.png"
    },
  "박시온과 설린의 케미명": {
      description: "설린의 츠렌이 '눈'과 시온의 '온'을 합쳐 만든 케미명 눈이 온다는 뜻을 가지고 있고,<br> 다현이까지 모이면 눈온다즈가 된다고 한다",
      image: "/uploads/chemistry/chem/S18/밥주시온즈.png"
    }
  }, 
 "김채원":{
  "김채원과 윤서연의 케미명":{
      description: "**250210 SIGNAL**에서 만든 케미명이자 유닛명.천연기념물 1호 리유저블 비닐 팩(윤서연)과 갈리다만 선녀(김채원)로 구성된 2인조 유튜버(?)라고 한다.",
      image: "/uploads/chemistry/chem/S1/자몽샤베트.png",
      links:{
       "250210 SIGNAL":"https://youtu.be/-68vLGYzCbE?si=hpNCdOo7MuuceRQo"
      }
  },
  "김채원과 김채연의 케미명":{
    description: "원래 케미는 둘 다 김채가 들어간다 하여 '김채즈'였지만 둘이 말차 메이트라고 하며 말차즈로 둘이서 새롭게 변경하였다. **250621 시그널**에서 언급된다.",
      image: "/uploads/chemistry/chem/S4/김채즈.png",
      links:{
       "250621 시그널":"https://youtu.be/64im_Cc0Gtk?si=RNOPLPdySrqMU9FV"
      }
  },
  "김채원과 김수민의 케미명":{
   description:"채원이의 별명인 외계인을 줄여 부르는 '왹'과 수민이를 줄여 부르는 '숨'이 붙어져 만들어진 케미명이다. <br> 처음에는 팬들끼리 부르는 케미명이였으나 지금은 많은 팬들이 사용하며 굳어지고 공식이나 다름없게 사용하는 케미명이다.",
    image:"/uploads/chemistry/chem/S6/왹숨즈.png"
  },
  "김채원과 서다현의 케미명":{
   description:"김채원의 상징 이모지'딸기'와 서다현의 별명인 '소다'를 합친 케미명으로 **240830 SIGNAL LIVE**에서 정해졌다.",
  image:"/uploads/chemistry/chem/S10/딸기소다즈.png",
  links:{
   "240830 SIGNAL LIVE":"https://youtu.be/rEdeeRSGpaI?si=8hmPXEndS1afmxA_"
   }
  },
  "김채원과 코토네의 케미명":{
   description:"코토네의 '코'와 채원의 '채'를 조합하면서 만들어진 케미명이다 ~즈를 포함하지않은 '코챌라'가 케미명이라고 한다.",
   image:"/uploads/chemistry/chem/S11/코챌라.png"
 },
  "김채원과 곽연지의 케미명":{
   description: "240803 팬싸에서 둘의 팬싸템이 슈가슈가룬의 바닐라 쇼콜라여서 슈가즈라고 언급.",
      image: "/uploads/chemistry/chem/S12/슈가즈.png"
  },
 "김채원과 신위의 케미명":{
  description:"240724 SIGANAL LIVE에서 착안됐으며 숨기는 게 많은 신위와 채원을 의미하는 케미명이다. <br>(서로 유명한 파트너 케미가 있음에도 불구하고 다른 멤버들에서 치근덕 대서 생긴 케미명이기도하다)",
   image:"/uploads/chemistry/chem/S15/비밀즈.png"
  },
 "김채원과 마유의 케미명":{
  description:"머리색이 산리오 캐릭인 키키와 라라의 색과 비슷한 파랑색 분홍색이기 때문이다.",
  image:"/uploads/chemistry/chem/S16/키키라라즈.png"
},
  "김채원과 주빈의 케미명":{
  description:"260316 채원 프롬을 통해 첫 언급이 된 후, 260320 주빈의 코스모 라이브에서 또 한번 언급이 되었다. <br> 주빈 이름의 빈을 ＇콩＇과 채원이의 상징인 ＇갸루＇를 합쳐서 콩가루를 둘만의  조합명인 콩갸루즈로 만든 듯하다.",
  image:"/uploads/chemistry/chem/S18/콩갸루즈.png"
  },
 "김채원과 정하연의 케미명":{
   description:"둘다 볼이 모찌처럼 잘늘어난다는 의미와 둘을'못찢'어놓는다는 의미도 담겨져있다. <br>**240409** SIGNAL LIVE에서 정해진 케미명.<br>트리플에스 내에서 가장 유명한 케미 중 하나다.",
  image:"/uploads/chemistry/chem/S19/모찌즈.png",
  links:{
  "240409":"https://youtu.be/BDgXKDeQ7QE?si=iz0kgdQiD6YHacvQ"
   }
  },
  "김채원과 설린의 케미명":{
    description:"설린의 '설'과 채원의 '원'을 합쳐 만들어진 케미명이다. <br>(오피셜 케미명은 아닌 설린 팬계정의 투표를 통해서 정해진 케미명이다.)<br> photo by. @Plumver1130",
    image:"/uploads/chemistry/chem/S21/설원즈.png"
   }
},
"설린":{
 "설린과 김채연의 케미명":{
   description: "김채<b>연</b>과 설린의 본명인 피라다 분<b>락</b>사를 합쳐 만든 이름으로, 240814 SIGNAL LIVE에서 팬들의 추천을 통해 만들었음.",
   image: "/uploads/chemistry/chem/S4/연락즈.png"
 },
 "설린과 공유빈의 케미명":{
  description:"설린의 '설'과 유빈의 별명인 유밤의 '밤'을 이용하여 만들어진 케미명.",
 image:"/uploads/chemistry/chem/S8/설밤즈.png"
 },
 "설린과 서다현의 케미명":{
 description:"다현 어린이, 설린 어린이에서 착안한 케미명으로 서로 어린이 같은 면모가 있어 정한 케미명 이라고 한다.<br> **260119 시그널**에서 무조건 '닿' 이라고 해야한다고 다현이가 당부했다.",
  image:"/uploads/chemistry/chem/S10/닿리니즈.png",
  links:{
   "260119 시그널":"https://youtu.be/IbGn0PnzNe0?si=rDOBTFGGlAutRr4k"
  }
},
 "설린과 박소현의 케미명":{
  description:"소현의 '<b>소</b>'와 설린의 '<b>설</b>'을 합쳐 만들어진 케미명이다.<br>25.10.25 소현 Fromm에서 언급이 나왔으며 설린이가 맘에 들어 하면 이렇게 하기로 했는데<br> 설린이도 마음에 들어 한다며 소설즈로 하게 되었다.<br>  photo by. Plumver1130",
  image:"/uploads/chemistry/chem/S14/소설즈.png"
 },
 "설린과 주빈의 케미명":{
   description:"<b>설</b>린+ 주<b>빈(빙)</b> 주빈이 직접 fromm을 통해 만든 케미명이며, 이후 설린 또한 이를 받아들여 공식 케미명이 되었다.",
  image:"/uploads/chemistry/chem/S18/설빙즈.png"
  },
  "설린과 박시온의 케미명":{
   description:"설린의 츠렌이 '눈'과 시온의 '온'을 합쳐 만든 케미명 눈이 온다는 뜻을 가지고 있고,<br> 다현이까지 모이면 눈온다즈가 된다고 한다.",
  image:"/uploads/chemistry/chem/S20/눈온즈.png"
  },
  "설린과 김채원의 케미명":{
    description:"설린의 '설'과 채원의 '원'을 합쳐 만들어진 케미명이다. <br>(오피셜 케미명은 아닌 설린 팬계정의 투표를 통해서 정해진 케미명이다.)<br> photo by. @Plumver1130",
    image:"/uploads/chemistry/chem/S21/설원즈.png"
 }
},
 "서아":{
  "서아와 김유연의 케미명":{
   description: "트리플에스의 첫째(유연)와 막내(서아)로 맏막즈가 되었다. <br>(맏막즈는 막내 포지션의 멤버는 연지 -> 주빈-> 서아 총 세 번 바뀌었다.) <br> Photo by. @s23_Seah",
      image: "/uploads/chemistry/chem/S5/맏막즈.png"
  },
 "서아와 서다현의 케미명":{
   description:"250423 시그널을 통해서 정해진 케미명이다. 서아의 '서'와 다현의 별명인 소다의 '소'를 조합하며 만든 케미명이다.",
   image:"/uploads/chemistry/chem/S10/서소즈.png"
  },
 "서아와 니엔의 케미명":{
     description:"**250514** 깨어 쇼케이스 비하인드 시그널에서 언급되었으며 <br> 둘다 똥머리를 하고 있어서 똥자매라고 부르다가 서로 똥을 좋아하냐고.. 물으며 똥똥즈가 결성 되었다.<br> photo by. madebykenny ",
     image:"/uploads/chemistry/chem/S13/똥똥즈.png",
     links:{
      "250514":"https://youtu.be/jmEPtvBSn44?si=8CVBHuNYx82lf-5L&t=370"
    }
   },
 "서아와 박소현의 케미명":{
   description:"서아의 fromm을 통해 만들어진 케미명으로, 햇님은 서아, 달님은 박소현을 뜻한다.",
  image:"/uploads/chemistry/chem/S14/햇님달님즈.png"
 },
 "서아와 린의 케미명":{
  description:"도쿄 디즈니씨의 놀이기구인 '소아링'과 비슷한 발음을 이용해 만들어진 케미명, <br>서아의 fromm에서 팬들이 추천해 주었던 케미명이며,<br> 린이 이를 받아들여 공식 케미명이 되었다. ",
  image:"/uploads/chemistry/chem/S17/서아린즈.png"
 },
 "서아와 주빈의 케미명":{
  description:"서아의 상징인 해와 주빈의 빈이 콩을 뜻하여 둘을 합쳐 햇콩즈로 케미명을 만들었다.<br> 나이순으로 막내와 그 바로 앞에 위치한 S의 조합으로,<br> 막내즈 조합임에 불구하고 케미를 찾기 어려웠는데 월드투어를 함께한 이후로 둘의 케미가 많이 보이기 시작.",
  image:"/uploads/chemistry/chem/S18/햇콩즈.png"
 },
 "서아와 지연의 케미명":{
  description:"<b>가장 마지막으로 공개된 두 멤버의 조합</b>으로 두 멤버의 예명 Gravity에서 정병기대표가 제안한 이름을 합쳐 만든 케미명. 240416 SIGNAL LIVE에서 만든 케미명, 줄여서 끌레도즈라는 이름도 만들었다.",
  image:"/uploads/chemistry/chem/S23/클레어도준즈.png"
 }
},
  "지연":{
     "지연과 윤서연의 케미명":{
       description: "트리플에스에서 둘은 S1과 S24이면서 둘의 본명은 윤서연, 지서연으로 같아 둘의 케미명은 서연즈가 되었다.",
       image: "/uploads/chemistry/chem/S1/서연즈.png"
        },
     "지연과 이지우의 케미명":{
      description:"250511 지연 fromm 에서 얘기하다가 비밀이라고 지크릿을 얘기하고 한 웨이브가 치링치링치리링 지크릿 쥬쥬를 언급.\n 맘에 들게 되어 지우에게 얘기 후 지크릿쥬쥬즈 케미명이 됨.",
      image:"/uploads/chemistry/chem/S3/지크릿쥬쥬즈.png"
      },
    "지연과 김나경의 케미명":{
      description:" 나경의 별명인 '나키'와 지연의 '지'를 합쳐서 만든 케미명으로. 250124 나경 프롬을 통해서 언급이 됐었고 <br> 지연이한테 물어보겠다며 마무리됐었지만 260208 팬사인회에서 지연이가 언급함으로써 공식 케미가 되었다. <br> photo by. wavv57 ",
    image:"/uploads/chemistry/chem/S7/낰지즈.png"
    },
     "지연과 공유빈의 케미명":{
       description:"좋은 일이든 나쁜 일이든 매번 똑같이 함께 당하고 인원을 나눌 때에도 계속해서 붙어있음을 언급하며 <br>'원 플러스 원'을 줄인 '원플원즈가 되었다",
        image:"/uploads/chemistry/chem/S8/원플원즈.png"
     },
     "지연과 코토네의 케미명":{
       description: "핑구의 목소리를 따라 하는 소리. 지연이 이 소리를 잘 따라 하는데, <br> 코토네가 핑구 따라 하기를 굉장히 좋아해서 케미명으로 정함.<br> '눕눕'의 앞뒤 글자가 똑같은 것처럼 두 사람도 취향이 매우 비슷하여 코토네가 눕1, 지연이 눕2를 맡고있다.<br> **(케미 참고 영상)**",
        image:"/uploads/chemistry/chem/S11/눕눕즈.png",
       links:{
       "(케미 참고 영상)":"https://youtu.be/6zRg8x27D5Y?si=7hqdV7lcuwAFe5I7&t=181"
        }
     },
   "지연과 곽연지의 케미명": {
    description: "둘의 고유 번호가 S12와 S24로 12월24일 (크리스마스 이브)가 떠올라 이브즈로 언급되어 케미명이 되었다. ",
    image: "/uploads/chemistry/chem/S12/이브즈.png"
},
 "지연과 린의 케미명":{
     description:"251114 린의 코스모 라이브에서 얼마 전 지연 언니와 케미명을 정해왔다고 말했었다.<br> 잠미즈는 ＇잠이 많은 미녀들이라는 뜻을 가지고 있다.＇ 공식은 아니고 당시에는 임시 케미명이라고 하였다. <br> 그리고 **260105 시그널**을 통해 잠미즈를 결정하는 장면이 나왔고<br> 그 이후 임시라고 했지만 둘이 만나면 잠미즈를 사용하면서 공식으로 굳어진 듯하다",
   image:"/uploads/chemistry/chem/S17/잠미즈.png",
   links:{
            "260105 시그널":"https://youtu.be/Ijn-yOD1rCI?si=sAkzcHYPQe3jKoNo"
    }
  },
"지연과 서아의 케미명": {
    description: "<b>가장 마지막으로 공개된 두 멤버의 조합</b>으로 두 멤버의 예명 Gravity에서 정병기대표가 제안한 이름을 합쳐 만든 케미명. 240416 SIGNAL LIVE에서 만든 케미명, 줄여서 끌레도즈라는 이름도 만들었다.",
    image: "/uploads/chemistry/chem/S23/클레어도준즈.png"
   }
  } // 필요한 추가 멤버와 케미명을 여기에 작성...
};

  function memberHref(index) {
    return "/triples/artist/" + (index + 1);
  }

  function escapeHtml(str) {
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function formatLabel(label) {
    return escapeHtml(label).replace(/\n/g, "<br>");
  }

  function buildTable(matrix) {
    const thead = document.getElementById("chemThead");
    const tbody = document.getElementById("chemTbody");
    if (!thead || !tbody) return;

    let headHtml = '<tr><th class="chem-corner"><img src="' + BASE + '/logo.png" alt="tripleS"></th>';
    MEMBERS.forEach((name, i) => {
      const color = MEMBER_COLORS[i] || "#00f5d4";
      headHtml +=
        '<th class="chem-col-head" style="--member-color:' + color + '">' +
        '<a href="' + memberHref(i) + '" title="' + escapeHtml(name) + '" class="chem-member-link">' +
        '<span class="chem-member-name">' + escapeHtml(name) + "</span>" +
        "</a></th>";
    });
    headHtml += "</tr>";
    thead.innerHTML = headHtml;

    let bodyHtml = "";
    matrix.forEach((row, ri) => {
      const color = MEMBER_COLORS[ri] || "#00f5d4";
      bodyHtml +=
        '<tr><th class="chem-row-head" style="--member-color:' + color + '">' +
        '<a href="' + memberHref(ri) + '" title="' + escapeHtml(MEMBERS[ri]) + '" class="chem-member-link">' +
        '<span class="chem-member-name">' + escapeHtml(MEMBERS[ri]) + "</span>" +
        "</a></th>";

      row.forEach((cell) => {
        if (cell.type === "self") {
          bodyHtml +=
            '<td class="chem-self"><img src="' +
            BASE +
            "/symbols/S" +
            cell.s +
            '.png" alt="S' +
            cell.s +
            '"></td>';
        } else if (cell.type === "chem" && cell.key) {
          const cross = cell.cross ? " chem-cross" : "";
          bodyHtml +=
            '<td class="chem-cell' +
            cross +
            '" data-person="' +
            escapeHtml(cell.person) +
            '" data-key="' +
            escapeHtml(cell.key) +
            '" tabindex="0" role="button">' +
            formatLabel(cell.label) +
            "</td>";
        } else {
          bodyHtml += '<td class="chem-empty"></td>';
        }
      });
      bodyHtml += "</tr>";
    });
    tbody.innerHTML = bodyHtml;

    tbody.querySelectorAll(".chem-cell").forEach((el) => {
      const open = () => openChemistryName(el.dataset.person, el.dataset.key);
      el.addEventListener("click", open);
      el.addEventListener("keydown", (e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          open();
        }
      });
    });
  }

  function openChemistryName(person, chemistryName) {
    const personData = chemistryDescriptions[person];
    if (!personData || !personData[chemistryName]) {
      console.warn("케미 데이터 없음:", person, chemistryName);
      return;
    }
    const data = personData[chemistryName];

    // 원본 데이터의 강제 <br> 줄바꿈을 제거해 자연스럽게 줄바꿈
    let formattedDescription = String(data.description)
      .replace(/<br\s*\/?>/gi, " ")
      .replace(/\s+/g, " ")
      .trim()
      .replace(/\*\*(.*?)\*\*/g, (match, p1) => {
        if (data.links && data.links[p1]) {
          return '<a href="' + data.links[p1] + '" target="_blank" rel="noopener" class="chem-highlight">' + p1 + "</a>";
        }
        return '<span class="chem-highlight">' + p1 + "</span>";
      })
      // 따옴표('…' / ‘…’)로 감싼 표현 강조
      .replace(/(['‘])([^'’]+)(['’])/g, (match, open, inner, close) => {
        return open + '<span class="chem-quote">' + inner + "</span>" + close;
      });

    document.getElementById("chemistryTitle").textContent = chemistryName;
    const img = document.getElementById("chemistryImage");
    img.src = data.image || "";
    img.alt = chemistryName;
    img.style.display = data.image ? "block" : "none";
    document.getElementById("chemistryDescription").innerHTML = formattedDescription;

    const linkDiv = document.getElementById("chemistryLinks");
    linkDiv.innerHTML = "";
    if (data.links) {
      for (const [text, url] of Object.entries(data.links)) {
        if (!data.description.includes("**" + text + "**")) {
          const a = document.createElement("a");
          a.href = url;
          a.target = "_blank";
          a.rel = "noopener";
          a.className = "chem-extra-link";
          a.textContent = text;
          linkDiv.appendChild(a);
        }
      }
    }

    const modal = document.getElementById("chemistryModal");
    modal.classList.remove("hidden");
    modal.classList.add("flex");
    document.body.style.overflow = "hidden";
  }

  function closeModal() {
    const modal = document.getElementById("chemistryModal");
    modal.classList.add("hidden");
    modal.classList.remove("flex");
    document.body.style.overflow = "";
  }

  let pairMatrix = null;
  let currentMode = "pair";

  function setModeButtons(mode) {
    document.querySelectorAll(".chem-mode-toggle .chem-mode-btn[data-mode]").forEach((btn) => {
      const active = btn.dataset.mode === mode;
      btn.classList.toggle("is-active", active);
      btn.setAttribute("aria-pressed", active ? "true" : "false");
    });
  }

  function showPairView() {
    const pairView = document.getElementById("chemPairView");
    const trioView = document.getElementById("chemTrioView");
    if (pairView) pairView.classList.remove("hidden");
    if (trioView) trioView.classList.add("hidden");
  }

  function showTrioPlaceholder() {
    const pairView = document.getElementById("chemPairView");
    const trioView = document.getElementById("chemTrioView");
    if (pairView) pairView.classList.add("hidden");
    if (trioView) trioView.classList.remove("hidden");
  }

  function setMode(mode) {
    currentMode = mode === "trio" ? "trio" : "pair";
    setModeButtons(currentMode);
    if (currentMode === "trio") {
      // 3인 케미 구조화 데이터 없음(이미지 몇 장만) → 플레이스홀더
      showTrioPlaceholder();
      return;
    }
    showPairView();
    if (pairMatrix) buildTable(pairMatrix);
  }

  function bindModeToggle() {
    document.addEventListener("click", (e) => {
      const btn = e.target.closest(".chem-mode-btn[data-mode]");
      if (!btn) return;
      e.preventDefault();
      setMode(btn.getAttribute("data-mode"));
    });
  }

  document.addEventListener("DOMContentLoaded", async () => {
    const closeBtn = document.querySelector("#chemistryModal .chem-close");
    if (closeBtn) closeBtn.addEventListener("click", closeModal);

    const modal = document.getElementById("chemistryModal");
    if (modal) {
      modal.addEventListener("click", (e) => {
        if (e.target === modal) closeModal();
      });
    }
    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape") closeModal();
    });

    bindModeToggle();

    try {
      const res = await fetch("/js/chemistry-matrix.json");
      const data = await res.json();
      pairMatrix = data.matrix;
      setMode("pair");
    } catch (err) {
      console.error("케미 매트릭스 로드 실패", err);
      document.getElementById("chemTbody").innerHTML =
        '<tr><td colspan="25" class="text-center text-[#b9cac4] py-8">케미 데이터를 불러오지 못했습니다.</td></tr>';
    }
  });

  window.openChemistryName = openChemistryName;
  window.__chemSetMode = setMode;
})();
