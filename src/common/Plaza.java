package common;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 광장 설정과 NPC 목록 (서버·클라이언트 공통).
 *
 * 좌표는 "도트" 단위 (캐릭터 64x64 도트와 같은 단위). 화면에서는 배율(보통 3배)을 곱해서 그린다.
 * 광장 맵은 가로 MAP_W 도트이고, 화면에는 그중 일부만 보이며 내 포프를 따라 카메라가 움직인다.
 */
public final class Plaza {

    private Plaza() {
    }

    /** 광장 맵 가로 길이 (도트). 배경 그림 bg/plaza.png 는 400x192 권장 */
    public static final int MAP_W = 400;
    public static final int MAP_H = 192;
    /** 발이 닿는 높이 (도트, 위에서부터) */
    public static final int FEET_Y = 168;

    /** 포프가 갈 수 있는 x 범위 (포프 발 중심 기준) */
    public static final int MIN_X = 24;
    public static final int MAX_X = MAP_W - 24;
    /** 처음 들어왔을 때 위치 (서버가 이 근처로 정함) */
    public static final int START_X = MAP_W / 2;

    /** 한 프레임(약 1/30초)에 움직이는 도트 수 */
    public static final int SPEED = 2;

    public static int clampX(int x) {
        return Math.max(MIN_X, Math.min(MAX_X, x));
    }

    /** 광장 NPC. 이름에는 띄어쓰기 없음 (프로토콜에 그대로 쓰려고) */
    public static final class Npc {
        public final String name;
        public final int x;
        public final String gene;
        public final String[] lines;

        Npc(String name, int x, String gene, String... lines) {
            this.name = name;
            this.x = x;
            this.gene = gene;
            this.lines = lines;
        }
    }

    /**
     * NPC 목록. 파츠 이름이 폴더에 없으면 그 파츠만 안 그려진다.
     * 대사는 말풍선으로 돌아가며 나온다. 4주차에 NPC 교배를 붙일 때 이 목록을 쓴다.
     */
    public static final List<Npc> NPCS = Collections.unmodifiableList(Arrays.asList(
            new Npc("광장지기퍼프", 70,
                    "body=human_body;eye=bean_eye;mouth=basicsmile_mouth;hair=-;hat=pumkin_hat;cloth=-;tail=-;acc=-;color=FFF0B3,B3F0C8,FFB3C1,B3DCFF",
                    "포프 마을에 온 걸 환영해!", "방향키로 돌아다녀 봐~", "친구를 사귀면 1:1로 이야기할 수 있어"),
            new Npc("큐피트퍼프", 335,
                    "body=bunny;eye=sleep_eye;mouth=kitty_mouth;hair=-;hat=-;cloth=libon_cloth;tail=-;acc=-;color=FFB3E6,D2B3FF,FFC8D2,B3FFF0",
                    "다 자란 포프라면 나랑 교배할 수 있어!", "예쁜 포프를 만나면 말을 걸어봐~")
    ));
}
