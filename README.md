# My-Little-Mongle-Popf
A personal project for developing a cozy tamagotchi-style game

## 키워봐요 몽글몽글 포프 (키몽포) — 네트워크프로그래밍 텀프로젝트 (정예팀)

Java + Swing + TCP 소켓으로 만드는 실시간 채팅 기반 포프 육성 게임.

### 폴더 구조

```
body/ eye/ mouth/ hair/ hat/ cloth/ tail/ acc/   포프 파츠 그림 (64x64 도트, 64x64 또는 16배 1024x1024 파일 둘 다 OK)
baby_body/ baby_eye/   (선택) 아기용 파츠. 성체와 같은 파일 이름으로 넣으면 아기 때 이걸 씀. 없으면 성체를 줄여서 임시로 보여줌
egg/egg.png            (선택) 알 그림. 없으면 코드로 그린 기본 알
bg/home.png            (선택) 집 배경 (200x192 권장). 없으면 코드로 그린 방
bg/plaza.png           (선택) 광장 배경 (400x192 권장, 옆으로 스크롤). 없으면 코드로 그린 임시 광장
icon/feed.png wash.png pet.png   (선택) 돌보기 버튼 아이콘 (16x16)
data/                  서버가 만드는 저장 폴더 (사용자별 포프). git 에는 안 올림
src/
  common/   서버·클라이언트가 같이 쓰는 것
    Protocol.java       프로토콜 상수 (/LOGIN, /CHAT ...)
    Gene.java           포프 유전자 (문자열 <-> 객체, 랜덤 생성)
    PartCatalog.java    파츠 폴더를 자동으로 읽어서 파츠 목록 만들기
    Pop.java            포프 상태 (단계, 경험치, 배고픔·청결·행복, 유전자)
    GrowthRule.java     육성 규칙 (경험치, 부화·성장 조건) — 숫자만 바꾸면 조절
    Plaza.java          광장 크기·이동 속도·NPC 목록과 대사
  server/   서버 (정헌)
    ChatServer.java     접속 받기, 접속자 목록, 전체 전송
    ClientHandler.java  클라이언트 1명당 스레드 1개, 메시지 처리
    PopService.java     포프 불러오기·랜덤 알 지급·돌보기 처리·파일 저장 (예민)
    PlazaService.java   집/광장 위치 관리, 광장 입장·퇴장·이동 전달 (예민). isInPlaza(), broadcastToPlaza() 는 채팅용
  client/   클라이언트
    PopfClient.java     ★ 시작점 (로그인 창)
    LoginFrame.java     로그인 창
    MainFrame.java      메인 창 (왼쪽 친구 목록 / 가운데 게임 화면 / 오른쪽 채팅)
    ServerConnection.java  서버 연결 + 수신 스레드
    HomePanel.java      집 화면 (알/아기/성체 + 부화·성장 연출)
    CarePanel.java      돌보기 버튼 + 상태창
    PlazaPanel.java     광장 화면 (방향키 이동, 다른 사람·NPC, 말풍선 showBubble)
    FriendListPanel.java   친구 목록 자리 (4주차)
    ChatPanel.java      채팅 패널 (정헌)
    PopRenderer.java    유전자대로 파츠를 색칠해서 겹쳐 그리기
    PopPreview.java     포프 그리기 테스트 창 (서버 없이 실행)
    ChatClient.java, ChatClientGUI.java   1주차 채팅 테스트용 (정헌)
```

### 실행 방법 (프로젝트 폴더에서, 터미널)

```bash
# 1. 컴파일 (한 번만, 코드 바꿀 때마다 다시)
javac -encoding UTF-8 -d out src/common/*.java src/server/*.java src/client/*.java

# 2. 서버 실행 (터미널 1)
java -cp out server.ChatServer

# 3. 클라이언트 실행 (터미널 2, 3 ... 여러 개 켜서 테스트)
java -cp out client.PopfClient

# 포프 그리기만 테스트 (서버 필요 없음)
java -cp out client.PopPreview
```

- 파츠 그림을 찾을 수 있게 **프로젝트 폴더(body 폴더가 있는 곳)에서** 실행한다.
- 다른 PC에서 접속할 때는 로그인 창의 "서버 주소"에 서버 PC의 IP를 넣는다 (같은 와이파이, 포트 9999).
- 서버와 클라이언트 모두 UTF-8로 주고받는다 (윈도우에서도 한글 안 깨지게).

### 프로토콜 (1주차)

| 기능 | 형식 | 방향 |
| --- | --- | --- |
| 로그인 요청 | `/LOGIN 닉네임` | 클라이언트 → 서버 |
| 로그인 성공 | `/LOGIN_OK` | 서버 → 요청자 |
| 로그인 실패 | `/LOGIN_FAIL 이유` | 서버 → 요청자 |
| 입장 알림 | `/ENTER 닉네임` | 서버 → 모두 |
| 전체 채팅 | `/CHAT 내용` | 클라이언트 → 서버 |
| 전체 채팅 전달 | `/CHAT 닉네임: 내용` | 서버 → 모두 |
| 퇴장 알림 | `/LEAVE 닉네임` | 서버 → 모두 |
| 접속 종료 | `/BYE` | 클라이언트 → 서버 |

### 파츠 바꾸는 법

- 파츠 폴더에 PNG 를 넣으면 바로 랜덤 후보가 되고, 지우면 빠진다 (코드 수정 필요 없음)
- 이름이 `_` 로 시작하는 파일은 무시한다 → 그리는 중인 파일은 `_` 를 붙여서 잠깐 숨기기
- 이미 저장된 포프의 파츠 파일을 지워도 저장 데이터는 남고, 그 파츠만 안 그려진다
- 배경은 꼭 투명하게 (흰 배경이면 흰 네모가 같이 그려짐)
- `PopPreview` 의 [파일 다시 읽기]로 창을 끄지 않고 바로 확인

### 프로토콜 (2주차: 육성)

| 기능 | 형식 | 방향 |
| --- | --- | --- |
| 내 포프 정보 | `/POP 단계 경험치 배고픔 청결 행복 유전자` | 서버 → 본인 (로그인 직후, 돌볼 때마다) |
| 돌보기 | `/CARE FEED` · `/CARE WASH` · `/CARE PET` | 클라이언트 → 서버 |
| 부화·성장 | `/GROW BABY` · `/GROW ADULT` | 서버 → 본인 (`/POP` 보다 먼저) |

육성 규칙 (`GrowthRule.java`): 돌보기 1번에 경험치 +20, 알은 60(3번)이면 부화, 아기는 100(5번)이면 성체.
밥 주기 = 배고픔 −25, 씻기기 = 청결 +25, 쓰다듬기 = 행복 +25.

### 프로토콜 (광장)

| 기능 | 형식 | 방향 |
| --- | --- | --- |
| 광장 가기 | `/PLAZA` (채팅에 `/광장` 또는 [광장으로 가기] 버튼) | 클라이언트 → 서버 |
| 광장 입장 성공 | `/PLAZA_OK 내x` | 서버 → 본인 (이어서 이미 있던 사람들의 `/PLAZA_IN`) |
| 누가 광장에 옴 | `/PLAZA_IN 닉네임 x 방향 단계 경험치 배고픔 청결 행복 유전자` | 서버 → 광장 |
| 누가 광장을 나감 | `/PLAZA_OUT 닉네임` | 서버 → 광장 |
| 집 가기 | `/HOME` (채팅에 `/집` 또는 [집으로 가기] 버튼) | 클라이언트 → 서버 |
| 집 도착 | `/HOME_OK` | 서버 → 본인 |
| 이동 | `/MOVE x 방향` → `/MOVE 닉네임 x 방향` | 클라이언트 → 서버 → 광장의 다른 사람 |

좌표는 도트 단위 (광장 가로 400도트). 로그인하면 집에서 시작한다.
광장에서 방향키가 안 먹으면 광장 화면을 한 번 클릭 (채팅 입력칸에 포커스가 있으면 글자 입력으로 감).

### 유전자 문자열

```
body=slime_body;eye=anime_eye;mouth=kitty_mouth;hair=-;hat=-;cloth=-;tail=-;acc=glasses_acc;color=FFB3C1,B3DCFF,FFF0B3,D2B3FF
```

- `-` = 그 파츠 없음, 장신구는 `+`로 여러 개 (`acc=glasses_acc+touch_acc`)
- `color` = 테마 색 4개. 파츠마다 이 중 두 색을 기본색/포인트색으로 씀
- 처음 생성 확률: 몸통·눈 100%, 입 40%, 그 외(머리·모자·옷·꼬리·장신구) 10%
