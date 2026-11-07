# My-Little-Mongle-Popf
A personal project for developing a cozy tamagotchi-style game

## 키워봐요 몽글몽글 포프 (키몽포) — 네트워크프로그래밍 텀프로젝트 (정예팀)

Java + Swing + TCP 소켓으로 만드는 실시간 채팅 기반 포프 육성 게임.

### 폴더 구조

```
body/ eye/ mouth/ hair/ hat/ cloth/ tail/ acc/ ear/   포프 파츠 그림 (1024x1024, 같은 위치에 겹쳐 그림)
src/
  common/   서버·클라이언트가 같이 쓰는 것
    Protocol.java       프로토콜 상수 (/LOGIN, /CHAT ...)
    Gene.java           포프 유전자 (문자열 <-> 객체, 랜덤 생성)
  server/   서버 (정헌)
    ChatServer.java     접속 받기, 접속자 목록, 전체 전송
    ClientHandler.java  클라이언트 1명당 스레드 1개, 메시지 처리
  client/   클라이언트
    PopfClient.java     ★ 시작점 (로그인 창)
    LoginFrame.java     로그인 창
    MainFrame.java      메인 창 (왼쪽 친구 목록 / 가운데 게임 화면 / 오른쪽 채팅)
    ServerConnection.java  서버 연결 + 수신 스레드
    HomePanel.java      집 화면 (알)
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

### 유전자 문자열

```
body=slime_body;eye=anime_eye;mouth=kitty_mouth;hair=-;hat=-;cloth=-;tail=-;acc=glasses_acc;color=FFB3C1,B3DCFF,FFF0B3,D2B3FF
```

- `-` = 그 파츠 없음, 장신구는 `+`로 여러 개 (`acc=glasses_acc+touch_acc`)
- `color` = 테마 색 4개. 파츠마다 이 중 두 색을 기본색/포인트색으로 씀
- 처음 생성 확률: 몸통·눈 100%, 입 40%, 그 외(머리·모자·옷·꼬리·장신구) 10%
