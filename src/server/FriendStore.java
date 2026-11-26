package server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/** 친구 목록, 대기 중인 친구 신청, 가입(로그인한 적 있는) 닉네임을 파일에 저장한다. 값은 탭으로 구분. */
public class FriendStore {

    private static final String SEP = "\t";

    private final Path friendsFile;
    private final Path pendingFile;
    private final Path usersFile;
    private final Map<String, Set<String>> friends = new HashMap<>();
    private final Set<String> pending = new LinkedHashSet<>(); // "보낸이\t받는이"
    private final Set<String> users = new TreeSet<>();

    public FriendStore() {
        this(Paths.get("friends.txt"), Paths.get("friend_pending.txt"), Paths.get("friend_users.txt"));
    }

    public FriendStore(Path friendsFile, Path pendingFile, Path usersFile) {
        this.friendsFile = friendsFile;
        this.pendingFile = pendingFile;
        this.usersFile = usersFile;
        for (String line : read(friendsFile)) {
            String[] t = line.split(SEP);
            if (t.length == 2) {
                link(t[0], t[1]);
            }
        }
        for (String line : read(pendingFile)) {
            if (line.split(SEP).length == 2) {
                pending.add(line);
            }
        }
        for (String line : read(usersFile)) {
            if (!line.isBlank()) {
                users.add(line.trim());
            }
        }
    }

    /** 로그인한 닉네임을 기록한다. 오프라인인 사람에게도 친구 신청을 보낼 수 있게 하려는 용도. */
    public synchronized void registerUser(String nick) {
        if (users.add(nick)) {
            write(usersFile, new ArrayList<>(users));
        }
    }

    public synchronized boolean isKnown(String nick) {
        return users.contains(nick);
    }

    public synchronized boolean areFriends(String a, String b) {
        return friends.getOrDefault(a, Collections.emptySet()).contains(b);
    }

    public synchronized List<String> friendsOf(String nick) {
        return new ArrayList<>(friends.getOrDefault(nick, Collections.emptySet()));
    }

    public synchronized boolean hasPending(String from, String to) {
        return pending.contains(from + SEP + to);
    }

    public synchronized void addPending(String from, String to) {
        if (pending.add(from + SEP + to)) {
            savePending();
        }
    }

    /** 있었으면 지우고 true */
    public synchronized boolean removePending(String from, String to) {
        boolean removed = pending.remove(from + SEP + to);
        if (removed) {
            savePending();
        }
        return removed;
    }

    /** to 에게 아직 답하지 않은 신청자들 */
    public synchronized List<String> pendingFor(String to) {
        List<String> result = new ArrayList<>();
        for (String p : pending) {
            String[] t = p.split(SEP);
            if (t[1].equals(to)) {
                result.add(t[0]);
            }
        }
        return result;
    }

    public synchronized void addFriends(String a, String b) {
        link(a, b);
        saveFriends();
    }

    private void link(String a, String b) {
        friends.computeIfAbsent(a, k -> new TreeSet<>()).add(b);
        friends.computeIfAbsent(b, k -> new TreeSet<>()).add(a);
    }

    private void saveFriends() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : friends.entrySet()) {
            for (String f : e.getValue()) {
                if (e.getKey().compareTo(f) < 0) { // 쌍을 한 번만 저장
                    lines.add(e.getKey() + SEP + f);
                }
            }
        }
        write(friendsFile, lines);
    }

    private void savePending() {
        write(pendingFile, new ArrayList<>(pending));
    }

    private static List<String> read(Path p) {
        try {
            return Files.exists(p) ? Files.readAllLines(p, StandardCharsets.UTF_8) : Collections.emptyList();
        } catch (IOException e) {
            System.out.println("[친구] 파일 읽기 실패: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private static void write(Path p, List<String> lines) {
        try {
            Files.write(p, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[친구] 파일 저장 실패: " + e.getMessage());
        }
    }
}