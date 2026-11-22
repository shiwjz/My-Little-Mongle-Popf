package server;

import common.DexEntry;
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
 * 사용자마다 "지금 키우는 포프" 1마리 + "도감"(청혼으로 떠나보낸 포프들)을 가진다.
 * - 로그인: 저장된 포프를 불러오고, 없으면 랜덤 유전자의 알을 새로 만든다 -> /POP, /DEX 전송
 * - /CARE: 육성 규칙(GrowthRule)을 적용하고 저장한 뒤 -> (자라면 /GROW) -> /POP 전송
 * - startNewGeneration(): 청혼 성공. 지금 포프는 도감으로, 새 알을 키운다 (BreedService 에서 호출) -> /POP, /DEX
 *   도감의 포프는 보기만 하고 다시 키울 수 없다.
 * - 저장: data/(닉네임).txt (Properties 형식). 서버를 껐다 켜도 그대로 남는다.
 *
 * 여러 클라이언트 스레드에서 동시에 불리므로 메서드마다 synchronized.
 */
public final class PopService {

    private PopService() {
    }

    private static final File DATA_DIR = new File("data");
    /** 지금 키우는 포프 */
    private static final Map<String, Pop> POPS = new HashMap<>();
    /** 도감 (오래된 순) */
    private static final Map<String, List<DexEntry>> DEXES = new HashMap<>();
    private static final Map<String, ClientHandler> CLIENTS = new HashMap<>();
    private static final Random RANDOM = new Random();

    /** 로그인 직후 ClientHandler 에서 호출 */
    public static synchronized void onLogin(ClientHandler client, String nickname) {
        if (!load(nickname)) {
            POPS.put(nickname, new Pop(Gene.random(RANDOM)));
            DEXES.put(nickname, new ArrayList<>());
            save(nickname);
            System.out.println("[포프] " + nickname + " 에게 새 알 지급: " + POPS.get(nickname).gene);
        }
        CLIENTS.put(nickname, client);
        client.sendMessage(Protocol.POP + " " + POPS.get(nickname).toProtocol());
        client.sendMessage(dexLine(nickname));
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

    /**
     * 청혼 성공: 지금 포프를 도감에 넣고, 새 알(child)을 키우기 시작한다.
     * 접속 중이면 /POP, /DEX 를 보낸다. 광장 사람들에게 알리는 건 부른 쪽(BreedService)이 락 밖에서 한다.
     */
    public static synchronized void startNewGeneration(String nickname, Gene child, String partner) {
        Pop old = POPS.get(nickname);
        List<DexEntry> dex = DEXES.get(nickname);
        if (old == null || dex == null) {
            return;
        }
        dex.add(new DexEntry(old.gene, partner));
        POPS.put(nickname, new Pop(child));
        save(nickname);
        ClientHandler client = CLIENTS.get(nickname);
        if (client != null) {
            client.sendMessage(Protocol.POP + " " + POPS.get(nickname).toProtocol());
            client.sendMessage(dexLine(nickname));
        }
    }

    /** 지금 접속 중인 사용자의 포프 (없으면 null) */
    public static synchronized Pop get(String nickname) {
        return POPS.get(nickname);
    }

    /** 로그아웃할 때 메모리에서만 지운다 (파일은 남음) */
    public static synchronized void onLogout(String nickname) {
        POPS.remove(nickname);
        DEXES.remove(nickname);
        CLIENTS.remove(nickname);
    }

    /** /DEX 유전자|짝 유전자|짝 ... */
    private static String dexLine(String nickname) {
        StringBuilder sb = new StringBuilder(Protocol.DEX);
        for (DexEntry e : DEXES.get(nickname)) {
            sb.append(' ').append(e.toProtocol());
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

    /** 파일에서 포프와 도감을 읽어 POPS/DEXES 에 넣는다. 파일이 없거나 깨졌으면 false */
    private static boolean load(String nickname) {
        File f = fileOf(nickname);
        if (!f.isFile()) {
            return false;
        }
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(Files.newInputStream(f.toPath()), StandardCharsets.UTF_8)) {
            p.load(r);
            POPS.put(nickname, readPop(p, ""));
            List<DexEntry> dex = new ArrayList<>();
            int count = Integer.parseInt(p.getProperty("dex.count", "0")); // 예전 파일에는 없음 -> 빈 도감
            for (int i = 0; i < count; i++) {
                dex.add(new DexEntry(Gene.parse(p.getProperty("dex." + i + ".gene", "")),
                        p.getProperty("dex." + i + ".partner", "?")));
            }
            DEXES.put(nickname, dex);
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
        List<DexEntry> dex = DEXES.get(nickname);
        p.setProperty("dex.count", String.valueOf(dex.size()));
        for (int i = 0; i < dex.size(); i++) {
            p.setProperty("dex." + i + ".gene", dex.get(i).gene.toString());
            p.setProperty("dex." + i + ".partner", dex.get(i).partner);
        }
        p.setProperty("nickname", nickname);
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(fileOf(nickname).toPath()), StandardCharsets.UTF_8)) {
            p.store(w, "Kimongpo pop of " + nickname);
        } catch (IOException | RuntimeException e) {
            System.out.println("[포프] 저장 실패: " + e.getMessage());
        }
    }
}
