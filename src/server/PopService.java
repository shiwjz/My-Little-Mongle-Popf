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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/**
 * 포프 육성을 담당하는 서버 쪽 코드 (예민 담당).
 *
 * - 로그인: 저장된 포프를 불러오고, 없으면 랜덤 유전자의 알을 새로 만든다 -> /POP 전송
 * - /CARE: 육성 규칙(GrowthRule)을 적용하고 저장한 뒤 -> (자라면 /GROW) -> /POP 전송
 * - 저장: data/(닉네임).txt (Properties 형식). 서버를 껐다 켜도 그대로 남는다.
 *
 * 여러 클라이언트 스레드에서 동시에 불리므로 메서드마다 synchronized.
 */
public final class PopService {

    private PopService() {
    }

    private static final File DATA_DIR = new File("data");
    private static final Map<String, Pop> POPS = new HashMap<>();
    private static final Random RANDOM = new Random();

    /** 로그인 직후 ClientHandler 에서 호출 */
    public static synchronized void onLogin(ClientHandler client, String nickname) {
        Pop pop = load(nickname);
        if (pop == null) {
            pop = new Pop(Gene.random(RANDOM));
            save(nickname, pop);
            System.out.println("[포프] " + nickname + " 에게 새 알 지급: " + pop.gene);
        }
        POPS.put(nickname, pop);
        client.sendMessage(Protocol.POP + " " + pop.toProtocol());
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
        save(nickname, pop);

        if (grown != null) {
            client.sendMessage(Protocol.GROW + " " + grown.name());
            System.out.println("[포프] " + nickname + " 의 포프가 " + grown.label + "(으)로 성장");
        }
        client.sendMessage(Protocol.POP + " " + pop.toProtocol());
        return grown != null;
    }

    /** 지금 접속 중인 사용자의 포프 (없으면 null) */
    public static synchronized Pop get(String nickname) {
        return POPS.get(nickname);
    }

    /** 로그아웃할 때 메모리에서만 지운다 (파일은 남음) */
    public static synchronized void onLogout(String nickname) {
        POPS.remove(nickname);
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

    private static Pop load(String nickname) {
        File f = fileOf(nickname);
        if (!f.isFile()) {
            return null;
        }
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(Files.newInputStream(f.toPath()), StandardCharsets.UTF_8)) {
            p.load(r);
            Pop pop = new Pop(Gene.parse(p.getProperty("gene", "")));
            pop.stage = Pop.Stage.valueOf(p.getProperty("stage", "EGG"));
            pop.exp = Integer.parseInt(p.getProperty("exp", "0"));
            pop.hunger = Integer.parseInt(p.getProperty("hunger", "50"));
            pop.clean = Integer.parseInt(p.getProperty("clean", "50"));
            pop.happy = Integer.parseInt(p.getProperty("happy", "50"));
            return pop;
        } catch (IOException | RuntimeException e) {
            System.out.println("[포프] 저장 파일을 읽지 못함 (" + f + "): " + e.getMessage());
            return null;
        }
    }

    private static void save(String nickname, Pop pop) {
        if (!DATA_DIR.isDirectory() && !DATA_DIR.mkdirs()) {
            System.out.println("[포프] data 폴더를 만들 수 없음");
            return;
        }
        Properties p = new Properties();
        p.setProperty("stage", pop.stage.name());
        p.setProperty("exp", String.valueOf(pop.exp));
        p.setProperty("hunger", String.valueOf(pop.hunger));
        p.setProperty("clean", String.valueOf(pop.clean));
        p.setProperty("happy", String.valueOf(pop.happy));
        p.setProperty("gene", pop.gene.toString());
        p.setProperty("nickname", nickname);
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(fileOf(nickname).toPath()), StandardCharsets.UTF_8)) {
            p.store(w, "Kimongpo pop of " + nickname);
        } catch (IOException | RuntimeException e) {
            System.out.println("[포프] 저장 실패: " + e.getMessage());
        }
    }
}
