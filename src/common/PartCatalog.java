package common;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 파츠 폴더(body/, eye/ ...)에 있는 PNG 파일을 자동으로 읽어서 파츠 목록을 만든다.
 *
 * - 폴더에 그림을 넣으면 바로 후보가 되고, 지우면 빠진다. 코드를 고칠 필요가 없다.
 * - 이름이 "_" 로 시작하는 파일은 무시한다 (예: _draft_body.png). 그리는 중인 파일을 잠깐 숨길 때 사용.
 * - 아기용 파츠는 "baby_" 를 붙인 폴더에 같은 이름으로 넣는다 (예: baby_body/slime_body.png).
 * - 서버와 클라이언트 모두 프로젝트 폴더(body 폴더가 있는 곳)에서 실행한다고 가정한다.
 */
public final class PartCatalog {

    private PartCatalog() {
    }

    private static final File BASE_DIR = findBaseDir();
    private static final Map<String, List<String>> CACHE = new HashMap<>();

    /** body/ 폴더가 있는 위치. 프로젝트 루트, src, bin 안에서 실행해도 찾을 수 있게 위로 몇 칸 올라가 본다. */
    private static File findBaseDir() {
        String[] candidates = {".", "..", "../.."};
        for (String c : candidates) {
            File dir = new File(c);
            if (new File(dir, "body").isDirectory()) {
                return dir;
            }
        }
        return new File(".");
    }

    public static File baseDir() {
        return BASE_DIR;
    }

    /** 폴더 안의 파츠 이름 목록 (확장자 뺀 파일 이름, 가나다순). 폴더가 없으면 빈 목록. */
    public static synchronized List<String> options(String folder) {
        List<String> cached = CACHE.get(folder);
        if (cached != null) {
            return cached;
        }
        List<String> names = new ArrayList<>();
        File[] files = new File(BASE_DIR, folder).listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                String n = f.getName();
                if (f.isFile() && n.toLowerCase().endsWith(".png") && !n.startsWith("_") && !n.startsWith(".")) {
                    names.add(n.substring(0, n.length() - 4));
                }
            }
        }
        List<String> result = Collections.unmodifiableList(names);
        CACHE.put(folder, result);
        return result;
    }

    /** 파일을 추가·삭제한 뒤 다시 읽고 싶을 때 (실행 중에 그림을 바꿀 때) */
    public static synchronized void reload() {
        CACHE.clear();
    }

    /** folder/name.png 파일 */
    public static File file(String folder, String name) {
        return new File(new File(BASE_DIR, folder), name + ".png");
    }

    public static boolean exists(String folder, String name) {
        return name != null && file(folder, name).isFile();
    }
}
