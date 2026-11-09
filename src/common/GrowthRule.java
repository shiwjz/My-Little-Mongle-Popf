package common;

/**
 * 육성 규칙을 한곳에 모아둔 클래스. 숫자만 바꾸면 성장 속도를 조절할 수 있다.
 *
 * 제안서 기준: 밥 주기·씻기기·쓰다듬기 1회당 성장 경험치 +20, 100이면 성체 (총 5회).
 * 알 부화 조건은 제안서에 없어서 "돌보기 3번(경험치 60)이면 부화"로 정해둠. 바꾸려면 EGG_GOAL 수정.
 * 판정은 서버에서만 한다 (클라이언트는 결과를 받아서 보여주기만).
 */
public final class GrowthRule {

    private GrowthRule() {
    }

    public static final int EXP_PER_CARE = 20;
    /** 알 -> 아기 */
    public static final int EGG_GOAL = 60;
    /** 아기 -> 성체 */
    public static final int BABY_GOAL = 100;

    public static final int START_STAT = 50;
    public static final int CARE_AMOUNT = 25;

    /** 돌보기 종류 (프로토콜 값) */
    public static final String FEED = "FEED";   // 밥 주기: 배고픔 감소
    public static final String WASH = "WASH";   // 씻기기: 청결 증가
    public static final String PET = "PET";     // 쓰다듬기: 행복 증가

    public static int goal(Pop.Stage stage) {
        switch (stage) {
            case EGG:
                return EGG_GOAL;
            case BABY:
                return BABY_GOAL;
            default:
                return 0;
        }
    }

    public static boolean isAction(String action) {
        return FEED.equals(action) || WASH.equals(action) || PET.equals(action);
    }

    /**
     * 돌보기를 적용한다.
     * @return 단계가 바뀌었으면 새 단계, 아니면 null
     */
    public static Pop.Stage apply(Pop pop, String action) {
        switch (action) {
            case FEED:
                pop.hunger = clamp(pop.hunger - CARE_AMOUNT);
                break;
            case WASH:
                pop.clean = clamp(pop.clean + CARE_AMOUNT);
                break;
            case PET:
                pop.happy = clamp(pop.happy + CARE_AMOUNT);
                break;
            default:
                return null;
        }

        if (pop.stage == Pop.Stage.ADULT) {
            return null; // 성체는 상태만 바뀌고 경험치는 더 안 쌓임
        }

        pop.exp += EXP_PER_CARE;
        if (pop.exp >= goal(pop.stage)) {
            pop.stage = (pop.stage == Pop.Stage.EGG) ? Pop.Stage.BABY : Pop.Stage.ADULT;
            pop.exp = 0;
            return pop.stage;
        }
        return null;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
