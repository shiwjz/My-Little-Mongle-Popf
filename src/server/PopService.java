package server;

import common.Gene;
import common.GrowthRule;
import common.Pop;
import common.Protocol;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/**
 * 포프 육성을 담당하는 서버 쪽 코드 (예민 담당).
 *
 * 사용자마다 "지금 키우는 포프" 1마리 + "보관함"(교배로 받은 알, 쉬고 있는 포프)을 가진다.
 * - 로그인: 저장된 포프를 불러오고, 없으면 랜덤 유전자의 알을 새로 만든다 -> /POP, /BOX 전송
 * - /CARE: 육성 규칙(GrowthRule)을 적용하고 저장한 뒤 -> (자라면 /GROW) -> /POP 전송
 * - /SWITCH 번호: 보관함의 포프와 지금 키우는 포프를 바꾼다 -> /POP, /BOX 전송
 * - addEgg(): 교배로 생긴 알을 보관함에 넣는다 (BreedService 에서 호출) -> /BOX 전송
 * - 저장: data/(닉네임).txt (Properties 형식). 서버를 껐다 켜도 그대로 남는다.
 *
 * 여러 클라이언트 스레드에서 동시에 불리므로 메서드마다 synchronized.
 */
public final class PopService {

    private PopService() {
    }

    /** 보관함 최대 칸 수 (너무 쌓이지 않게) */
    public static final int BOX_LIMIT = 12;

    private static final File DATA_DIR = new File("data");
    /** 지금 키우는 포프 */
    private static final Map<String, Pop> POPS = new HashMap<>();
    /** 보관함 */
    private static final Map<String, List<Pop>> BOXES = new HashMap<>();
    private static final Map<String, ClientHandler> CLIENTS = new HashMap<>();
    private static final Random RANDOM = new Random();

    /** 로그인 직후 ClientHandler 에서 호출 */
    public static synchronized void onLogin(ClientHandler client, String nickname) {
        if (!load(nickname)) {
            POPS.put(nickname, new Pop(Gene.random(RANDOM)));
            BOXES.put(nickname, new ArrayList<>());
            save(nickname);
            System.out.println("[포프] " + nickname + " 에게 새 알 지급: " + POPS.get(nickname).gene);
        }
        CLIENTS.put(nickname, client);
        client.sendMessage(Protocol.POP + " " + POPS.get(nickname).toProtocol());
        client.sendMessage(boxLine(nickname));
    }

    /** "/CARE 행동" 을 받았을 때 ClientHandler 에서 호출 */
    public static void onCare(ClientHandler client, String nickname, String action) {
        boolean changed = careLocked(client, nickname, action);
        // 락을 풀고 나서 광장에 알린다 (PlazaService 와 서로 기다리는 일이 없게)
        if (changed) {
            PlazaService.onPopChanged(nickname);
        }
    }

    private static synchronized boolean careLocked(ClientHandler client, String nickname, String action) {
        Pop pop = POPS.get(nickname);
        if (pop == null) {
            return false;
        }
        if (!GrowthRule.isAction(action)) {
            client.sendMessage("알 수 없는 돌보기입니다: " + action);
            return false;
        }

        Pop.Stage grown = GrowthRule.apply(pop, action);
        save(nickname);

        if (grown != null) {
            client.sendMessage(Protocol.GROW + " " + grown.name());
            System.out.println("[포프] " + nickname + " 의 포프가 " + grown.label + "(으)로 성장");
        }
        client.sendMessage(Protocol.POP + " " + pop.toProtocol());
        return grown != null;
    }

    /** "/SWITCH 번호": 보관함의 index 번째 포프를 꺼내서 키우고, 지금 포프는 보관함 그 자리에 넣는다 */
    public static void onSwitch(ClientHandler client, String nickname, String indexText) {
        boolean ok = switchLocked(client, nickname, indexText);
        if (ok) {
            PlazaService.onPopChanged(nickname);
        }
    }

    private static synchronized boolean switchLocked(ClientHandler client, String nickname, String indexText) {
        List<Pop> box = BOXES.get(nickname);
        Pop active = POPS.get(nickname);
        if (box == null || active == null) {
            return false;
        }
        int index;
        try {
            index = Integer.parseInt(indexText.trim());
        } catch (NumberFormatException e) {
            client.sendMessage("[시스템] 보관함 번호가 올바르지 않습니다.");
            return false;
        }
        if (index < 0 || index >= box.size()) {
            client.sendMessage("[시스템] 보관함에 그 번호의 포프가 없습니다.");
            return false;
        }
        POPS.put(nickname, box.get(index));
        box.set(index, active);
        save(nickname);
        client.sendMessage(Protocol.POP + " " + POPS.get(nickname).toProtocol());
        client.sendMessage(boxLine(nickname));
        return true;
    }

    /**
     * 교배로 생긴 알을 보관함에 넣는다. 접속 중이면 /BOX 로 알려준다.
     * @return 보관함이 꽉 차서 못 넣었으면 false
     */
    public static synchronized boolean addEgg(String nickname, Gene gene) {
        List<Pop> box = BOXES.get(nickname);
        if (box == null) {
            return false;
        }
        if (box.size() >= BOX_LIMIT) {
            return false;
        }
        box.add(new Pop(gene));
        save(nickname);
        ClientHandler client = CLIENTS.get(nickname);
        if (client != null) {
            client.sendMessage(boxLine(nickname));
        }
        return true;
    }

    public static synchronized boolean isBoxFull(String nickname) {
        List<Pop> box = BOXES.get(nickname);
        return box == null || box.size() >= BOX_LIMIT;
    }

    /** 지금 접속 중인 사용자의 포프 (없으면 null) */
    public static synchronized Pop get(String nickname) {
        return POPS.get(nickname);
    }

    /** 로그아웃할 때 메모리에서만 지운다 (파일은 남음) */
    public static synchronized void onLogout(String nickname) {
        POPS.remove(nickname);
        BOXES.remove(nickname);
        CLIENTS.remove(nickname);
    }

    /** /BOX 단계|유전자 단계|유전자 ... */
    private static String boxLine(String nickname) {
        StringBuilder sb = new StringBuilder(Protocol.BOX);
        for (Pop p : BOXES.get(nickname)) {
            sb.append(' ').append(p.stage.name()).append('|').append(p.gene);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 파일 저장 / 불러오기
    // ------------------------------------------------------------------

    /**
     * 닉네임을 파일 이름으로 쓸 때 한글·특수문자를 %XX 형태로 바꾼다 (예: 예민 -> %EC%98%88%EB%AF%BC.txt).
     * 컴퓨터마다 파일 이름 인코딩이 달라도 저장이 깨지지 않게 하기 위해서. 실제 닉네임은 파일 안에 적어둔다.
     */
    private static File fileOf(String nickname) {
        String safe;
        try {
            safe = URLEncoder.encode(nickname, StandardCharsets.UTF_8.name()).replace("*", "%2A");
        } catch (java.io.UnsupportedEncodingException e) {
            safe = Integer.toHexString(nickname.hashCode());
        }
        return new File(DATA_DIR, safe + ".txt");
    }

    /** 파일에서 포프와 보관함을 읽어 POPS/BOXES 에 넣는다. 파일이 없거나 깨졌으면 false */
    private static boolean load(String nickname) {
        File f = fileOf(nickname);
        if (!f.isFile()) {
            return false;
        }
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(Files.newInputStream(f.toPath()), StandardCharsets.UTF_8)) {
            p.load(r);
            POPS.put(nickname, readPop(p, ""));
            List<Pop> box = new ArrayList<>();
            int count = Integer.parseInt(p.getProperty("box.count", "0")); // 예전 파일에는 없음 -> 빈 보관함
            for (int i = 0; i < count; i++) {
                box.add(readPop(p, "box." + i + "."));
            }
            BOXES.put(nickname, box);
            return true;
        } catch (IOException | RuntimeException e) {
            System.out.println("[포프] 저장 파일을 읽지 못함 (" + f + "): " + e.getMessage());
            return false;
        }
    }

    private static Pop readPop(Properties p, String prefix) {
        Pop pop = new Pop(Gene.parse(p.getProperty(prefix + "gene", "")));
        pop.stage = Pop.Stage.valueOf(p.getProperty(prefix + "stage", "EGG"));
        pop.exp = Integer.parseInt(p.getProperty(prefix + "exp", "0"));
        pop.hunger = Integer.parseInt(p.getProperty(prefix + "hunger", "50"));
        pop.clean = Integer.parseInt(p.getProperty(prefix + "clean", "50"));
        pop.happy = Integer.parseInt(p.getProperty(prefix + "happy", "50"));
        return pop;
    }

    private static void writePop(Properties p, String prefix, Pop pop) {
        p.setProperty(prefix + "stage", pop.stage.name());
        p.setProperty(prefix + "exp", String.valueOf(pop.exp));
        p.setProperty(prefix + "hunger", String.valueOf(pop.hunger));
        p.setProperty(prefix + "clean", String.valueOf(pop.clean));
        p.setProperty(prefix + "happy", String.valueOf(pop.happy));
        p.setProperty(prefix + "gene", pop.gene.toString());
    }

    private static void save(String nickname) {
        if (!DATA_DIR.isDirectory() && !DATA_DIR.mkdirs()) {
            System.out.println("[포프] data 폴더를 만들 수 없음");
            return;
        }
        Properties p = new Properties();
        writePop(p, "", POPS.get(nickname));
        List<Pop> box = BOXES.get(nickname);
        p.setProperty("box.count", String.valueOf(box.size()));
        for (int i = 0; i < box.size(); i++) {
            writePop(p, "box." + i + ".", box.get(i));
        }
        p.setProperty("nickname", nickname);
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(fileOf(nickname).toPath()), StandardCharsets.UTF_8)) {
            p.store(w, "Kimongpo pop of " + nickname);
        } catch (IOException | RuntimeException e) {
            System.out.println("[포프] 저장 실패: " + e.getMessage());
        }
    }
}
